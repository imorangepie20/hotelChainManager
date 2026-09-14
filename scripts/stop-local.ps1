[CmdletBinding(SupportsShouldProcess)]
param([switch]$Api, [switch]$Web, [switch]$Admin)

$ErrorActionPreference = 'Stop'
if (-not $Api -and -not $Web -and -not $Admin) { $Api = $true; $Web = $true; $Admin = $true }
Import-Module (Join-Path $PSScriptRoot 'local-runtime.psm1') -Force

$state = Read-LocalRuntimeState
if ($Web -and (Test-LocalRuntimeProcess $state.Web)) {
    if ($PSCmdlet.ShouldProcess("Customer web PID $($state.Web.Pid)", 'Stop')) {
        Stop-Process -Id $state.Web.Pid -Force
        Write-Output 'Stopped customer web managed by this script.'
        $state.Web = $null
    }
} elseif ($Web) { Write-Output 'No customer web process managed by this script.' }

if ($Admin -and (Test-LocalRuntimeProcess $state.Admin)) {
    if ($PSCmdlet.ShouldProcess("Administrator PID $($state.Admin.Pid)", 'Stop')) {
        Stop-Process -Id $state.Admin.Pid -Force
        Write-Output 'Stopped administrator managed by this script.'
        $state.Admin = $null
    }
} elseif ($Admin) { Write-Output 'No administrator process managed by this script.' }

if ($Api) {
    $apiId = $state.Docker.api
    if ($apiId -and (Get-ComposeContainerId 'api') -eq $apiId -and $PSCmdlet.ShouldProcess('Docker Compose api', 'Stop')) {
        & docker compose stop api
        if ($LASTEXITCODE -ne 0) { throw 'Docker Compose API stop failed.' }
        $state.Docker.Remove('api')
        Write-Output 'Stopped API managed by this script.'
    }

    $dbId = $state.Docker.db
    $otherDependent = (Get-ComposeContainerId 'api') -or (Get-ComposeContainerId 'concierge')
    if ($dbId -and (Get-ComposeContainerId 'db') -eq $dbId -and -not $otherDependent -and $PSCmdlet.ShouldProcess('Docker Compose db', 'Stop')) {
        & docker compose stop db
        if ($LASTEXITCODE -ne 0) { throw 'Docker Compose database stop failed.' }
        $state.Docker.Remove('db')
        Write-Output 'Stopped database managed by this script.'
    } elseif ($dbId -and $otherDependent) { Write-Output 'Database remains running because another Compose service depends on it.' }
}

if (-not $WhatIfPreference) { Write-LocalRuntimeState $state }
