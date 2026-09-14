# 로컬 서버 실행 안내

PowerShell에서 프로젝트 루트로 이동한다.

```powershell
cd C:\Users\jowoo\hotelChainManager
```

모든 로컬 서버를 시작한다.

```powershell
.\scripts\start-local.ps1
```

실행 주소는 다음과 같다.

- 고객 웹: `http://127.0.0.1:4000`
- 관리자: `http://127.0.0.1:4001`
- API: `http://127.0.0.1:4080`

필요한 서버만 실행할 수 있다.

```powershell
.\scripts\start-local.ps1 -Web
.\scripts\start-local.ps1 -Admin
.\scripts\start-local.ps1 -Api
```

정지는 같은 방법으로 실행한다.

```powershell
.\scripts\stop-local.ps1
.\scripts\stop-local.ps1 -Web
.\scripts\stop-local.ps1 -Admin
.\scripts\stop-local.ps1 -Api
```

스크립트는 자신이 시작한 고객·관리자 프로세스와 Docker 컨테이너만 정지한다. 이미 실행 중이던 서버는 그대로 둔다. PowerShell 실행 정책 오류가 나면 현재 창에서만 아래 명령을 한 번 실행한 뒤 다시 시도한다.

```powershell
Set-ExecutionPolicy -Scope Process Bypass
```
