# 로컬 서버 스크립트

PowerShell에서 실행한다.

```powershell
.\scripts\start-local.ps1
.\scripts\stop-local.ps1
```

기본 대상은 API(4080), 고객 웹(4000), 관리자(4001)다. `-Api`, `-Web`, `-Admin`으로 필요한 대상만 선택한다.

예시:

```powershell
.\scripts\start-local.ps1 -Web
.\scripts\stop-local.ps1 -Api -Admin
```

스크립트가 시작한 고객·관리자 PID와 Docker 컨테이너만 `.local-runtime/state.json`에 기록한다. 이미 실행 중인 포트·컨테이너는 건드리지 않는다. 다른 Compose 서비스가 데이터베이스를 사용하면 데이터베이스도 유지한다.
