[CmdletBinding(SupportsShouldProcess)]
param([switch]$Api, [switch]$Web, [switch]$Admin)

$ErrorActionPreference = 'Stop'
if (-not $Api -and -not $Web -and -not $Admin) { $Api = $true; $Web = $true; $Admin = $true }
Import-Module (Join-Path $PSScriptRoot 'local-runtime.psm1') -Force

$root = Get-LocalRuntimeRoot
$state = Read-LocalRuntimeState
if ($null -eq $state.Docker) { $state.Docker = @{} }

if ($Api) {
    $before = @{}
    foreach ($service in @('db', 'api')) { $before[$service] = Get-ComposeContainerId $service }
    if ($before['api']) {
        Write-Output 'API 4080 is already running; left unchanged.'
    } elseif ($PSCmdlet.ShouldProcess('Docker Compose db, api', 'Start')) {
        & docker compose up -d db api
        if ($LASTEXITCODE -ne 0) { throw 'Docker Compose API start failed.' }
        foreach ($service in @('db', 'api')) {
            $id = Get-ComposeContainerId $service
            if (-not $before[$service] -and $id) { $state.Docker[$service] = $id }
        }
        Write-Output 'API is available at http://127.0.0.1:4080.'
    }
}

if ($Web) {
    if (Test-LocalRuntimeProcess $state.Web) {
        Write-Output 'Customer web 4000 is already managed by this script.'
    } else {
        $owner = Get-LocalRuntimePortOwner 4000
        if ($owner) {
            Write-Output "Customer web 4000 is already in use by PID $owner; left unchanged."
        } elseif ($PSCmdlet.ShouldProcess('Customer web 4000', 'Start')) {
            $vite = Join-Path $root 'apps\\web\\node_modules\\vite\\bin\\vite.js'
            if (-not (Test-Path -LiteralPath $vite)) { throw 'Customer dependencies are missing. Run pnpm install in apps/web first.' }
            $node = (Get-Command node -ErrorAction Stop).Source
            $process = Start-Process -FilePath $node -ArgumentList $vite, '--host', '127.0.0.1', '--port', '4000' -WorkingDirectory (Join-Path $root 'apps\\web') -WindowStyle Hidden -PassThru
            $state.Web = [pscustomobject]@{ Pid = $process.Id; StartedAtUtc = $process.StartTime.ToUniversalTime().ToString('o') }
            Write-LocalRuntimeState $state
            for ($attempt = 0; $attempt -lt 20; $attempt++) {
                if (Get-LocalRuntimePortOwner 4000) { Write-Output 'Customer web is available at http://127.0.0.1:4000.'; break }
                Start-Sleep -Milliseconds 500
            }
            if (-not (Get-LocalRuntimePortOwner 4000)) { throw 'Customer web did not open port 4000.' }
        }
    }
}

if ($Admin) {
    if (Test-LocalRuntimeProcess $state.Admin) {
        Write-Output 'Administrator 4001 is already managed by this script.'
    } else {
        $owner = Get-LocalRuntimePortOwner 4001
        if ($owner) {
            Write-Output "Administrator 4001 is already in use by PID $owner; left unchanged."
        } elseif ($PSCmdlet.ShouldProcess('Administrator 4001', 'Start')) {
            $next = Join-Path $root 'SDTPL_ADM\\node_modules\\next\\dist\\bin\\next'
            if (-not (Test-Path -LiteralPath $next)) { throw 'Administrator dependencies are missing. Run pnpm install in SDTPL_ADM first.' }
            $node = (Get-Command node -ErrorAction Stop).Source
            $process = Start-Process -FilePath $node -ArgumentList $next, 'dev', '--port', '4001' -WorkingDirectory (Join-Path $root 'SDTPL_ADM') -WindowStyle Hidden -PassThru
            $state.Admin = [pscustomobject]@{ Pid = $process.Id; StartedAtUtc = $process.StartTime.ToUniversalTime().ToString('o') }
            Write-LocalRuntimeState $state
            for ($attempt = 0; $attempt -lt 20; $attempt++) {
                if (Get-LocalRuntimePortOwner 4001) { Write-Output 'Administrator is available at http://127.0.0.1:4001.'; break }
                Start-Sleep -Milliseconds 500
            }
            if (-not (Get-LocalRuntimePortOwner 4001)) { throw 'Administrator did not open port 4001.' }
        }
    }
}

if (-not $WhatIfPreference) { Write-LocalRuntimeState $state }
