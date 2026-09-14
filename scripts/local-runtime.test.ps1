$ErrorActionPreference = 'Stop'

$modulePath = Join-Path $PSScriptRoot 'local-runtime.psm1'
Import-Module $modulePath -Force

$plan = Get-LocalRuntimePlan -Api -Web -Admin
if ($plan.DockerServices -join ',' -ne 'db,api') { throw 'API plan must start db and api.' }
if ($plan.Web.Port -ne 4000) { throw 'Web plan must use port 4000.' }
if ($plan.Admin.Port -ne 4001) { throw 'Admin plan must use port 4001.' }

$state = ConvertTo-LocalRuntimeState ([pscustomobject]@{ Web = $null; Admin = $null; Docker = [pscustomobject]@{ api = 'api-id' } })
if ($state.Docker['api'] -ne 'api-id') { throw 'Docker state must remain addressable after JSON conversion.' }

Write-Output 'local runtime plan tests passed'
