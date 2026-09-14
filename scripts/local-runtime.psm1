function Get-LocalRuntimeRoot {
    Split-Path -Parent $PSScriptRoot
}

function Get-LocalRuntimeStatePath {
    Join-Path (Get-LocalRuntimeRoot) '.local-runtime\\state.json'
}

function Get-LocalRuntimePlan {
    param([switch]$Api, [switch]$Web, [switch]$Admin)

    [pscustomobject]@{
        DockerServices = if ($Api) { @('db', 'api') } else { @() }
        Web = if ($Web) { [pscustomobject]@{ Port = 4000 } } else { $null }
        Admin = if ($Admin) { [pscustomobject]@{ Port = 4001 } } else { $null }
    }
}

function ConvertTo-LocalRuntimeState {
    param($State)

    $docker = @{}
    if ($null -ne $State.Docker) {
        foreach ($property in $State.Docker.PSObject.Properties) { $docker[$property.Name] = $property.Value }
    }
    return [pscustomobject]@{ Web = $State.Web; Admin = $State.Admin; Docker = $docker }
}

function Read-LocalRuntimeState {
    $path = Get-LocalRuntimeStatePath
    if (-not (Test-Path -LiteralPath $path)) {
        return [pscustomobject]@{ Web = $null; Admin = $null; Docker = @{} }
    }
    return ConvertTo-LocalRuntimeState (Get-Content -LiteralPath $path -Raw | ConvertFrom-Json)
}

function Write-LocalRuntimeState {
    param([Parameter(Mandatory)]$State)

    $path = Get-LocalRuntimeStatePath
    $directory = Split-Path -Parent $path
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $State | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $path -Encoding utf8
}

function Get-LocalRuntimePortOwner {
    param([Parameter(Mandatory)][int]$Port)

    Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty OwningProcess
}

function Test-LocalRuntimeProcess {
    param($Record)

    if ($null -eq $Record -or $null -eq $Record.Pid -or $null -eq $Record.StartedAtUtc) { return $false }
    $process = Get-Process -Id $Record.Pid -ErrorAction SilentlyContinue
    if ($null -eq $process) { return $false }
    $recordedStart = if ($Record.StartedAtUtc -is [DateTime]) {
        $Record.StartedAtUtc.ToUniversalTime()
    } else {
        [DateTime]::ParseExact($Record.StartedAtUtc, 'o', [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::RoundtripKind).ToUniversalTime()
    }
    return [Math]::Abs(($process.StartTime.ToUniversalTime() - $recordedStart).TotalSeconds) -lt 1
}

function Get-ComposeContainerId {
    param([Parameter(Mandatory)][string]$Service)

    $id = (& docker compose ps -q $Service).Trim()
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose 상태를 확인하지 못했습니다: $Service" }
    return $id
}

Export-ModuleMember -Function Get-LocalRuntimeRoot, Get-LocalRuntimeStatePath, Get-LocalRuntimePlan, ConvertTo-LocalRuntimeState, Read-LocalRuntimeState, Write-LocalRuntimeState, Get-LocalRuntimePortOwner, Test-LocalRuntimeProcess, Get-ComposeContainerId
