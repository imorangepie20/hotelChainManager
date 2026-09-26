# 호텔 체인 매니저 - Zorin 홈 서버 배포

> 최종 갱신: 2026-09-26

## 대상

| 항목 | 값 |
|---|---|
| 호스트 | `approid@192.168.219.174` (Zorin OS, `johae-server`) |
| 공개 도메인 | `hcm.approid.team` (고객 웹 + API) |
| 관리자 도메인 | `admin-hcm.approid.team` |
| 연결 | Cloudflare Tunnel (`cloudflared` 컨테이너) |
## 서비스와 포트

**호스트 포트를 하나도 열지 않는다.** 서버에 여러 프로젝트가 있어
포트가 겹치면 안 되기 때문이다. Compose 파일에 `ports:` 가 아니라
`expose:` 만 있다.

| 서비스 | 컨테이너 | 컨테이너 포트 | 호스트 포트 | 공개 |
|---|---|---|---|---|
| API (Spring Boot) | `api` | `4080` | **없음** | `hcm.approid.team/api/*` |
| 고객 웹 (Vite + nginx) | `web` | `3110` | **없음** | `hcm.approid.team/*` |
| 관리자 (Next.js) | `admin` | `3111` | **없음** | `admin-hcm.approid.team/*` |
| PostgreSQL | `postgres` | `5432` | **없음** | - |
| 터널 | `tunnel` | - | - | - |

외부로 들어오는 유일한 길은 Cloudflare Tunnel이다. 터널 등록값과
절차는 [Cloudflare Tunnel 등록 메뉴얼](./CLOUDFLARE-TUNNEL.md)을,
포트 할당 규칙은 [포트 맵](./PORTS.md)을 따른다.

**80·8080·3000 을 쓰지 않는다.** 고객 웹은 `3110`, 관리자는
`3111` 만 수신한다. 서버의 다른 서비스와 겹치지 않게 프로젝트
전용 대역으로 통일했다.

**Cloudflare의 Public Hostname URL은 서비스 이름 + 컨테이너
포트다.** `container_name`을 따로 지정하지 않았으므로 Compose
네트워크 안에서 DNS로 풀리는 이름은 **서비스 이름**(`api`·`web`·
`admin`)이다. `hcm-api:4080`을 등록하면 터널이 `hcm-api`를 찾지
못해서 502가 된다.

## 도메인 구조

고객 웹과 API는 **같은 도메인**을 공유하고, 관리자는 **별도
서브도메인**을 쓴다.

- `hcm.approid.team` → nginx → 정적 파일(고객 웹), `/api/*` → API
- `admin-hcm.approid.team` → Next.js

**같은 도메인을 공유하는 이유.** 고객 웹이 브라우저에서
`fetch('/api/...')` 상대 경로 호출을 하므로 API와 같은 출처에 있어야
한다. CORS 설정 없이 동작한다. 관리자는 `NEXT_PUBLIC_CUSTOMER_WEB_ORIGIN`
환경변수로 고객 웹 주소를 알아야 해서 별도 서브도메인으로 분리했다.
## 준비

### 1. Cloudflare 터널 만들기

### 1. Cloudflare 터널 만들기

Cloudflare Zero Trust 대시보드에서 터널을 만든다.

1. `Zero Trust` → `Networks` → `Tunnels` → `Create a tunnel`
2. 터널 이름: `hcm`
3. **Install 화면의 토큰을 복사** (한 번만 보여준다)
4. Public Hostname 등록:

   | Subdomain | Domain | Path | Type | URL |
   |---|---|---|---|---|
   | `hcm` | `approid.team` | `/api/*` | HTTP | `api:4080` |
   | `hcm` | `approid.team` | (비움) | HTTP | `web:3110` |
   | `admin-hcm` | `approid.team` | (비움) | HTTP | `admin:3111` |

   **URL은 Compose 서비스 이름 + 컨테이너 포트다.** `hcm-api`·
   `hcm-admin` 같은 이름은 풀리지 않는다.

   **`/api/*`가 전체 경로(비움)보다 위에 있어야 한다.** 더 구체적인
   경로가 먼저 매칭된다. 순서가 반대면 `/api`가 고객 웹으로 가서
   `actuator/health`가 404가 된다.

   > 입력칸 하나하나와 실수 목록은
   > [Cloudflare Tunnel 등록 메뉴얼](./CLOUDFLARE-TUNNEL.md)을 따른다.

5. DNS 레코드는 터널이 자동으로 만든다.

### 2. 토큰과 환경변수

```bash
./infra/scripts/set-tunnel-token.sh eyJhIjoi...   # 토큰 저장
cp infra/secrets/compose.env.example infra/secrets/compose.env
cp infra/secrets/tunnel.env.example   infra/secrets/tunnel.env
```

`compose.env`에서 **DB 비밀번호를 먼저 바꾼다**. 처음 기동할 때만
데이터베이스가 만들어지고, 이후에는 `compose.env`를 바꿔도 이미
만들어진 데이터베이스에 영향을 주지 않는다.

### 3. Cloudflare DNS 확인

터널이 만든 CNAME을 확인한다. `approid.team` zone이 Cloudflare에
있어야 한다.

```bash
getent hosts hcm.approid.team
getent hosts admin-hcm.approid.team
```

## 배포

```bash
# 로컬 Windows 에서 코드를 서버로 보낸다
.\infra\scripts\deploy-zorin.ps1 -Push

# 서버에서 빌드·기동한다
.\infra\scripts\deploy-zorin.ps1 -Up
```

또는 서버에서 직접:

```bash
cd ~/apps/hotel-chain-manager
docker compose --env-file infra/secrets/compose.env \
  -f infra/compose.zorin.yml --profile tunnel up -d --build
```

**첫 기동은 2~5분 걸린다.** Spring Boot가 Flyway 마이그레이션
(V1~V64)을 실행한 뒤에 준비된다. `--wait`을 쓰면 헬스체크가
통과할 때까지 기다린다.

```bash
docker compose --env-file infra/secrets/compose.env \
  -f infra/compose.zorin.yml --profile tunnel up -d --build --wait
```

## 검증

```bash
./infra/scripts/verify-deployment.sh
```

**호스트 포트를 열지 않으므로 `http://127.0.0.1:3201` 같은 주소는
없다.** 스크립트는 컨테이너 안에서 `docker compose exec`로
검사한다.

- `api` 컨테이너 readiness → `UP`
- `web` 컨테이너 root → 고객 웹 HTML
- `admin` 컨테이너 root → 관리자 로그인 페이지
- `https://hcm.approid.team/api/hotels` → 터널 경유 HTTP 200 JSON
- `https://hcm.approid.team/` → 터널 경유 고객 웹
- `https://admin-hcm.approid.team/` → 터널 경유 관리자

**뒤의 3개(터널 경유)만 실패하면 Cloudflare 설정 문제다.**
[Cloudflare Tunnel 등록 메뉴얼](./CLOUDFLARE-TUNNEL.md)의 10번
(자주 하는 실수)을 확인한다.

## 직원 로그인

`SPRING_PROFILES=dev` + `STAFF_DEV_ENABLED=true`일 때
`StaffDevAccountInitializer`가 개발용 직원 계정을 만든다. 비밀번호는
`compose.env`의 `STAFF_HQ_PASSWORD` 등에서 읽는다. **첫 기동 이후에는
`.env`의 값을 바꿔도 이미 만들어진 계정에 영향을 주지 않는다.**

운영용 비밀번호를 쓰려면:

1. `compose.env`에 강한 비밀번호를 넣는다
2. `docker compose ... up -d --build` 로 다시 기동한다
3. 새로 만들어지는 계정에만 적용된다

## 정지·업데이트·백업

```bash
.\infra\scripts\deploy-zorin.ps1 -Down        # 서비스 정지
.\infra\scripts\deploy-zorin.ps1 -Push -Up    # 새 코드 배포
.\infra\scripts\deploy-zorin.ps1 -Logs        # 로그
.\infra\scripts\deploy-zorin.ps1 -Backup      # DB 백업
```

백업 파일은 `./backup/`에 쌓인다.

## 제한

- **실결제·정산은 기본적으로 꺼져 있다.** `PAYMENT_PROVIDER=fake`,
  `PAYMENT_CHECKOUT_ENABLED=false`, `TOSS_SETTLEMENT_ENABLED=false`.
- **AI 컨시어지는 이 배포에 포함하지 않는다.** `GOOGLE_API_KEY`가
  필요하다. `CONCIERGE_PROXY_TARGET`을 비워두면 관리자의 `/concierge`
  rewrite를 만들지 않고, AI 운영 메뉴가 "AI 도우미가 실행 중이
  아닙니다"를 보여준다. 고객 웹의 `/concierge` 호출이 빈 응답을
  받아도 나머지 기능은 동작한다. 도우미를 넣으려면 `compose.env`의
  `CONCIERGE_PROXY_TARGET=http://concierge:9000`과 `GOOGLE_API_KEY`를
  채우고 `docker compose ... up -d --build`로 다시 기동한다.
- **DB를 호스트에 노출하지 않는다.** 서버의 DB는 컨테이너 네트워크
  안에서만 통신한다. 개발용 `55432` 포트 노출은 로컬 Windows
  `compose.yaml`만 해당한다.
- **호스트 포트를 하나도 열지 않는다.** 서버에 여러 프로젝트가
  있어 포트가 겹치면 안 되기 때문이다. `docker compose ps` 를
  실행해도 `PORTS` 칸이 비어 있는 것이 정상이다.
- **첫 배포 직후에는 고객 웹이 빈 페이지일 수 있다.** Flyway가
  시드하지 않는 한 CMS 페이지가 없다. CMS 메뉴에서 페이지를
  만들어야 한다. 단 지점 3곳의 객실 유형·요금·재고와 랜딩 콘텐츠는
  dev 프로필이 시드한다.
- **`--wait`을 쓰면 터널 서비스도 대기한다.** `depends_on`이
  세 서비스의 헬스체크를 기다린다.

## 검증한 배포 상태

| 항목 | 결과 |
|---|---|
| compose 파일 유효성 | 본 문서의 단일 서비스 정의가 `docker compose config` 없이 정성 검사만 했다. 서버에서 실제로 `up`하지 않았다. |
| Flyway V1~V64 순서·additive 여부 | 읽기로 확인. V58·V60·V63 등 기존 표를 변경하지 않는 additive 마이그레이션이고, `V1__baseline.sql`은 `schema_marker`만 만든다. |
| 마이그레이션 파일 인코딩 | 64개 전부 UTF-8 BOM 없음, 잘못된 바이트 시퀀스 0건. |
| dev 프로필 시드 | `R__demo_data.sql`이 3개 지점 모두의 객실 유형·요금·재고를 심는다 (이전에는 속초만). |
| Cloudflare 터널 | 터널 토큰은 `infra/secrets/tunnel.env`에 있어야 하고 `.gitignore`가 무시한다. `git check-ignore`로 무시됨을 확인했다. |
| 라이브 DB | 검증하지 않았다. 배포를 실행하지 않았다. |
| 컨테이너 빌드 | 검증하지 않았다. `docker build`를 실행하지 않았다. |
| 브라우저 동작 | 검증하지 않았다. |
