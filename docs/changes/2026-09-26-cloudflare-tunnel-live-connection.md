# Cloudflare Tunnel 라이브 연결 및 배포 포트 전환

> 날짜: 2026-09-26

## 목표와 완료 기준

- Zorin 서버의 기존 배포 상태와 터널 오류 원인을 확인한다.
- 호스트 포트 없이 `api:4080`·`web:3110`·`admin:3111`로 통신한다.
- Cloudflare Tunnel 연결 4개가 등록되고 최근 인증 오류가 없어야 한다.
- 고객 API·고객 웹·관리자 웹을 공개 HTTPS로 확인한다.
- 비밀값은 출력하거나 저장소에 추가하지 않는다.

## 작업 결과

- 서버 `johae-server`의 `postgres`·`api`·`web`·`admin`은 기존에도
  healthy였지만, 터널은 폐기된 토큰으로 `Unauthorized: Tunnel not
  found`를 반복하고 있었다. 고객·관리자 DNS도 없었다.
- 사용자가 Cloudflare에서 `hcm` 터널과 Public Hostname을 등록하고 새
  Connector Token을 서버의 `infra/secrets/tunnel.env`에 저장했다.
  터널 컨테이너를 재생성한 뒤 서울 엣지(`icn01`·`icn06`)에 연결 4개가
  등록됐다.
- 서버는 구형 `127.0.0.1:3201→4080`, `3110→80`, `3111→3000` 구성이어서
  Cloudflare의 새 origin과 맞지 않았다. 전체 미커밋 애플리케이션 소스를
  배포하지 않고 다음 배포 파일만 서버에 반영했다.
  - `infra/compose.zorin.yml`
  - `apps/web/infra/Dockerfile`, `apps/web/infra/nginx.conf`
  - `SDTPL_ADM/infra/Dockerfile`, `SDTPL_ADM/next.config.ts`
  - `infra/scripts/verify-deployment.sh`
- 원격 Compose 검증과 web·admin production build가 통과했다. Compose가
  의존 서비스의 구성 변경을 감지해 API도 기존 소스·이미지 기준으로 다시
  기동했으며 PostgreSQL 볼륨은 유지했다.
- 현재 `docker compose ps`에서 API·web·admin은 호스트 published port 없이
  각각 `4080/tcp`·`3110/tcp`·`3111/tcp`로 healthy다.
- 관리자 호스트는 사용자가 실제 등록한 `admin-hcm.approid.team`으로
  통일했다. `admin.hcm.approid.team` 같은 다단계 호스트는 full setup의
  기본 Universal SSL 범위를 벗어나 TLS 오류가 났다.
- 공개 API 검증은 잘못된 `/api/actuator/health` 대신 실제 애플리케이션
  경로 `/api/hotels`를 사용하도록 스크립트와 문서를 고쳤다. Actuator의
  실제 경로는 컨테이너 내부 `/actuator/health`다.

## 검증 결과

- 원격 `docker compose ... config --quiet`: 통과
- 원격 compose SHA-256: 로컬 `infra/compose.zorin.yml`과 일치
- API 컨테이너 `/actuator/health`: `UP`
- web 컨테이너 `http://127.0.0.1:3110/`: 성공
- admin 컨테이너 `http://127.0.0.1:3111/`: 성공
- `https://hcm.approid.team/api/hotels`: HTTP 200 JSON
- `https://hcm.approid.team/`: HTTP 200 HTML
- `https://admin-hcm.approid.team/`: HTTP 307, `/login`으로 정상 이동
- 배포 전 구성 백업: 서버
  `backup/deploy-config-20260926-173831`

## 미검증 항목

- 실제 직원 계정으로 관리자 로그인하지 않았다.
- 예약 검색·예약·결제·취소의 공개 브라우저 E2E는 실행하지 않았다.
- 이번 작업 트리의 대규모 미커밋 기능 변경 전체를 서버에 배포하지 않았다.
  서버 애플리케이션 소스는 기존 배포본이고, 배포·터널 파일만 갱신했다.
- 관리자 Cloudflare Access 정책은 구성 여부를 확인하지 않았다.

## 후속 전체 변경 배포

- 전체 미커밋 기능을 `cdbea6e`로 커밋하고 원격 `main`에 푸시한 뒤 Zorin 서버에 배포했다.
- 배포 전 DB 볼륨을 `backup/db-2026-09-26-1855.tar.gz`로 백업했다.
- Windows PowerShell의 `tar | ssh`가 바이너리 스트림을 손상시키는 문제가 있어, `deploy-zorin.ps1`을 임시 archive 생성 → `scp` → 원격 해제 방식으로 변경했다.
- Windows archive가 실행 비트를 보존하지 않는 문제는 전송 후 `mvnw`와 `infra/scripts/*.sh`에 `chmod +x`를 적용하고 API Dockerfile에서도 `mvnw` 실행 권한을 명시해 해결했다.
- API·고객 웹·관리자 production image가 모두 빌드됐고 컨테이너가 healthy 상태다. Flyway는 스키마 버전 64가 최신 상태임을 확인했다.
- `verify-deployment.sh`의 컨테이너 내부 API·고객 웹·관리자 검사와 Cloudflare 경유 API·고객 웹·관리자 검사 6개가 모두 통과했다.
- cloudflared는 서울 리전 `icn01`·`icn06`에 QUIC 연결 4개를 등록했다.
