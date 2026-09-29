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

  # 배포 입력은 검증·커밋된 HEAD로 한정한다. git archive는 ignored `.env`,
  # 생성 이미지, Playwright 결과와 로컬 비밀값을 구조적으로 포함하지 않는다.
  $repositoryStatus = & git status --porcelain --untracked-files=normal
  if ($LASTEXITCODE -ne 0) { throw "Git 상태를 확인하지 못했습니다" }
  if ($repositoryStatus) { throw "커밋되지 않은 변경이 있습니다. clean HEAD만 배포할 수 있습니다." }
  $deployArchive = Join-Path ([IO.Path]::GetTempPath()) "hotel-chain-manager-$([guid]::NewGuid().ToString('N')).tar.gz"
  $remoteArchive = "/tmp/hotel-chain-manager-$([guid]::NewGuid().ToString('N')).tar.gz"
  try {
    & git archive --format=tar.gz --output=$deployArchive HEAD
    if ($LASTEXITCODE -ne 0) { throw "커밋 archive 생성이 실패했습니다" }

    & scp -o ConnectTimeout=15 $deployArchive "$Server`:$remoteArchive"
    if ($LASTEXITCODE -ne 0) { throw "배포 archive 전송이 실패했습니다" }

    Invoke-Remote "mkdir -p $RemoteDir; tar -xzf $remoteArchive -C $RemoteDir; rm -f $RemoteDir/.env; rm -rf $RemoteDir/artifacts $RemoteDir/playwright-report $RemoteDir/test-results $RemoteDir/apps/web/playwright-report $RemoteDir/apps/web/test-results $RemoteDir/SDTPL_ADM/playwright-report $RemoteDir/SDTPL_ADM/test-results; chmod +x $RemoteDir/services/api/mvnw $RemoteDir/infra/scripts/*.sh; rm -f $remoteArchive"
  } finally {
    if (Test-Path -LiteralPath $deployArchive) {
      Remove-Item -LiteralPath $deployArchive -Force
    }
  }

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
  Invoke-Remote "cd $RemoteDir; bash ./infra/scripts/verify-deployment.sh"
}

if ($Backup) {
  $stamp = (Get-Date -Format "yyyy-MM-dd-HHmm")
  Write-Output "DB 를 backup/db-$stamp.tar.gz 으로 백업한다"
  Invoke-Remote "cd $RemoteDir; mkdir -p backup; docker run --rm -v hotel-chain-manager_hcm-db-data:/data -v `$(pwd)/backup:/backup alpine tar czf /backup/db-$stamp.tar.gz -C /data ."
  Write-Output "백업 완료"
}
