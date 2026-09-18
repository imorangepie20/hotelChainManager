# pnpm 권한 문제 해결

## 원인

`pnpm`이 PATH에 없었다. Node.js 24.19.0 설치 디렉터리(`C:\Program Files\nodejs`)에는 `corepack`이 있지만 `pnpm` shim이 없다.

```powershell
pnpm -> absent
corepack -> C:\Program Files\nodejs\corepack.cmd
```

`corepack enable pnpm`은 shim을 corepack 실행 파일 옆에 설치하는데, `Program Files` 쓰기에 관리자 권한이 필요하다.

```
Internal Error: EPERM: operation not permitted, open 'C:\Program Files\nodejs\pnpm.CMD'
```

## 해결

`corepack enable --install-directory` 로 쓰기 가능한 사용자 디렉터리에 shim을 설치했다. corepack은 설치 디렉터리만 바꿀 뿐 shim 내용은 동일하다.

```powershell
& "C:\Program Files\nodejs\corepack.cmd" enable --install-directory "C:\Users\jowoo\AppData\Local\pnpm" pnpm
```

그리고 그 디렉터리를 사용자 PATH에 추가했다.

```powershell
[Environment]::SetEnvironmentVariable('PATH', "$([Environment]::GetEnvironmentVariable('PATH','User'));C:\Users\jowoo\AppData\Local\pnpm", 'User')
```

PowerShell의 기본 실행 정책이 `Restricted`라 corepack이 만든 `.ps1` shim이 거부됐다. 사용자 범위만 `RemoteSigned`로 변경했다. 로컬 파일은 실행 허용, 원격 다운로드 파일만 서명을 요구한다.

```powershell
Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned -Force
```

검증:

```powershell
Get-Command pnpm | Select-Object Source
& pnpm --version
# 11.25.0
```

`apps/web`과 `SDTPL_ADM` 모두 `pnpm dev` 로 dev 서버를 띄울 수 있다.

```powershell
cd apps\web
pnpm exec vite --host 127.0.0.1 --port 4000

cd SDTPL_ADM
pnpm dev
```

## 백업 래퍼

AppData shim 디렉터리가 PATH에서 사거나 실행 정책이 다시 `Restricted`로 돌아갈 경우를 대비해 `scripts/pnpm.cmd` 백업 래퍼를 뒀다. corepack이 만든 `pnpm.CMD`와 같은 `dist/pnpm.js` 진입점을 호출한다.

```powershell
& "C:\Users\jowoo\hotelChainManager\scripts\pnpm.cmd" --version
```

`scripts/` 디렉터리 자체는 `compose.yaml`과 같은 루트 스크립트 보관소로 이미 존재한다.
