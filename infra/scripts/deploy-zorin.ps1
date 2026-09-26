# 배포 스크립트
#
# 사용:
#   .\infra\scripts\deploy-zorin.ps1 -Push           # 코드를 서버로 보낸다
#   .\infra\scripts\deploy-zorin.ps1 -Up             # 빌드·기동
#   .\infra\scripts\deploy-zorin.ps1 -Push -Up       # 보내고 기동
#   .\infra\scripts\deploy-zorin.ps1 -Down           # 정지
#   .\infra\scripts\deploy-zorin.ps1 -Logs           # 로그
#   .\infra\scripts\deploy-zorin.ps1 -Verify         # 헬스체크
#   .\infra\scripts\deploy-zorin.ps1 -Backup         # DB 백업
#
# 로컬 Windows 에서 실행한다. 서버 주소와 배포 디렉터리는 매개변수로 바꿀 수 있다.
#
#   .\infra\scripts\deploy-zorin.ps1 -Up -Server approid@192.168.219.174

[CmdletBinding()]
param(
  [switch]$Push,
  [switch]$Up,
  [switch]$Down,
  [switch]$Logs,
  [switch]$Verify,
  [switch]$Backup,
  [string]$Server = "approid@192.168.219.174",
  [string]$RemoteDir = "~/apps/hotel-chain-manager"
)

$ErrorActionPreference = "Stop"

function Invoke-Remote {
  param([Parameter(Mandatory)][string]$Script)
  # 작은따옴표 안의 && 를 bash 에 그대로 넘기기 위해 base64 로 인코딩한다.
  $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Script))
  ssh -o ConnectTimeout=15 $Server "echo $encoded | base64 -d | bash -s"
  if ($LASTEXITCODE -ne 0) {
    throw "원격 명령이 실패했습니다 (종료 코드 $LASTEXITCODE)"
  }
}

if (-not ($Push -or $Up -or $Down -or $Logs -or $Verify -or $Backup)) {
  Write-Output "동작을 하나 이상 지정한다: -Push -Up -Down -Logs -Verify -Backup"
  exit 2
}

if ($Push) {
  Write-Output "코드를 $Server`:$RemoteDir 로 보낸다"

  # 로컬 산출물과 비밀값은 보내지 않는다. Windows 에 rsync 가 없으므로
  # tar 로 묶어서 ssh 로 보낸다.
  $excludes = @(
    "--exclude=.git", "--exclude=node_modules", "--exclude=target",
    "--exclude=dist", "--exclude=.next", "--exclude=build",
    "--exclude=.venv", "--exclude=__pycache__", "--exclude=.tmp",
    "--exclude=.local-runtime", "--exclude=test-results",
    "--exclude=apps/web/node_modules", "--exclude=SDTPL_ADM/node_modules",
    "--exclude=apps/web/dist", "--exclude=SDTPL_ADM/.next",
    "--exclude=infra/secrets/tunnel.env", "--exclude=infra/secrets/compose.env",
    "--exclude=backup"
  )
  $tarArgs = @("-czf", "-") + $excludes + @(".")
  & tar @tarArgs | ssh -o ConnectTimeout=15 $Server "mkdir -p $RemoteDir; tar -xzf - -C $RemoteDir"
  if ($LASTEXITCODE -ne 0) { throw "tar 전송이 실패했습니다" }

  Write-Output "전송 완료"
}

$compose = @(
  "docker compose",
  "--env-file infra/secrets/compose.env",
  "-f infra/compose.zorin.yml"
) -join " "

if ($Up) {
  Write-Output "빌드·기동한다 (첫 기동은 2~5분)"
  Invoke-Remote "cd $RemoteDir; $compose --profile tunnel up -d --build"
  Write-Output "기동 완료. -Verify 로 확인한다."
}

if ($Down) {
  Write-Output "서비스를 정지한다"
  Invoke-Remote "cd $RemoteDir; $compose --profile tunnel down"
}

if ($Logs) {
  Write-Output "로그를 따라간다. 종료는 Ctrl+C"
  ssh -o ConnectTimeout=15 $Server "cd $RemoteDir; $compose logs -f --tail=200"
}

if ($Verify) {
  Invoke-Remote "cd $RemoteDir; ./infra/scripts/verify-deployment.sh"
}

if ($Backup) {
  $stamp = (Get-Date -Format "yyyy-MM-dd-HHmm")
  Write-Output "DB 를 backup/db-$stamp.tar.gz 으로 백업한다"
  Invoke-Remote "cd $RemoteDir; mkdir -p backup; docker run --rm -v hotel-chain-manager_hcm-db-data:/data -v `$(pwd)/backup:/backup alpine tar czf /backup/db-$stamp.tar.gz -C /data ."
  Write-Output "백업 완료"
}