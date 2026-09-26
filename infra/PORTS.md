# 포트 맵

> 최종 갱신: 2026-09-26

## 핵심 원칙

**Zorin 서버 배포는 호스트 포트를 하나도 열지 않는다.** 서버에 여러
프로젝트가 있어 포트가 겹치면 안 되기 때문이다. Compose 에
`ports:` 가 아니라 `expose:` 만 있다.

**외부로 들어오는 유일한 길은 Cloudflare Tunnel 이다.** 터널이
컨테이너 네트워크에 직접 붙어서 서비스 이름으로 통신한다.

터널 등록값과 디버깅 방법은 [Cloudflare Tunnel 등록 메뉴얼]
(./CLOUDFLARE-TUNNEL.md) 을 따른다.

## 로컬 개발 (`compose.yaml`)

로컬 개발은 **호스트 포트를 의도적으로 연다**. 디버거·브라우저·
Playwright 가 `127.0.0.1` 로 접근해야 하기 때문이다. 이 컴퓨터에서만
실행하므로 다른 프로젝트와 신경 쓸 필요가 없다.

| 서비스 | 컨테이너 포트 | 호스트 포트 | 비고 |
|---|---|---|---|
| `db` (PostgreSQL) | `5432` | `127.0.0.1:55432` | 개발용 DB. `HOTEL_DB_PORT` 로 변경 |
| `db-test` (PostgreSQL) | `5432` | `127.0.0.1:55433` | 테스트 전용. tmpfs. 고정 |
| `api` (Spring Boot) | `4080` | `127.0.0.1:4080` | `HOTEL_API_PORT` 로 변경 |
| `concierge` (AI 도우미) | `9000` | `127.0.0.1:9000` | 로컬에서만 |
| `s3mock` | `9090` | `127.0.0.1:59090` | `object-storage` 프로파일에서만 |

**고객 웹(`4000`)과 관리자(`3000`)는 Vite / Next 개발 서버를 직접
실행한다.** 컨테이너가 아니다.

## Zorin 서버 배포 (`infra/compose.zorin.yml`)

**호스트 포트 없음.** `expose:` 만 선언한다.

| 서비스 | 컨테이너 포트 | 호스트 포트 | 공개 도메인 |
|---|---|---|---|
| `postgres` | `5432` | (없음) | - |
| `api` (Spring Boot) | `4080` | (없음) | `hcm.approid.team/api/*` |
| `web` (nginx) | `3110` | (없음) | `hcm.approid.team/*` |
| `admin` (Next.js) | `3111` | (없음) | `admin-hcm.approid.team/*` |
| `tunnel` (cloudflared) | - | - | - |

`docker compose ps` 를 실행해도 `PORTS` 칸이 비어 있는 것이 정상이다.

## 컨테이너 포트 (터널 URL) vs 호스트 포트

**헷갈리기 가장 쉬운 부분이다.**

- **Cloudflare Public Hostname 의 URL** 은 **컨테이너 포트**다:
  `api:4080`·`web:3110`·`admin:3111`
- **`docker compose exec` 로 컨테이너 안에서 검사**할 때도
  **컨테이너 포트**다: `http://127.0.0.1:4080` (컨테이너 안의
  `127.0.0.1`)
- **로컬 개발**에서 브라우저로 접근할 때는 **호스트 포트**다:
  `http://127.0.0.1:4080` (로컬 `compose.yaml` 은 열어둠)

배포에서는 호스트 포트가 없으므로 `http://127.0.0.1:4080` 은
**컨테이너 안에서만** 의미가 있다.

## 사용하지 않는 포트

| 포트 | 이유 |
|---|---|
| `80` | 흔히 다른 서비스가 쓴다. 쓰지 않는다. |
| `8080` | 마찬가지로 자주 충돌한다. 쓰지 않는다. |
| `3000` | 관리자의 Next.js 기본 포트. `3111` 로 바꿨다. |
| `3100`~`3104` | **기존 프로젝트** (`sdtpl-adm`·`real-es`·`music-pie`·`alpha-momega`) |
| `3200` | **기존 프로젝트** (`b2b-stm`) |

`3100`~`3104` 와 `3200` 은 같은 서버에서 이미 실행 중인 다른
프로젝트가 쓰고 있다. **겹치면 안 된다.**

## 포트 대역 규칙

| 대역 | 용도 |
|---|---|
| `3000` | 로컬 개발 Next.js 관리자 (개발 서버) |
| `3110`·`3111` | 이 프로젝트의 배포용 컨테이너 포트 (web·admin) |
| `4000` | 로컬 개발 Vite 고객 웹 (개발 서버) |
| `4080` | Spring Boot 컨테이너 내부 포트 |
| `55432`·`55433` | 로컬 개발·테스트 DB |
| `59090` | s3mock (로컬만) |
| `9000` | AI 컨시어지 컨테이너 내부 포트 |

## 80·8080 을 쓰지 않는 이유

고객 웹의 nginx 와 관리자의 Next.js 는 각각 `3110`·`3111` 만
수신한다.

- `80`·`8080` 은 서버의 다른 서비스가 이미 쓰고 있을 가능성이 높다.
- 컨테이너 포트는 호스트와 직접 충돌하지 않지만, **포트를 전부
  프로젝트 전용 대역으로 통일**하면 헷갈림이 사라진다.
- 어차피 **외부에서는 전혀 보이지 않는다.** 유일한 입구는
  Cloudflare Tunnel 이다.

## Cloudflare Public Hostname

상세한 등록 절차·입력칸·실수 목록은
[Cloudflare Tunnel 등록 메뉴얼](./CLOUDFLARE-TUNNEL.md) 을 따른다.

| Subdomain | Domain | Path | Type | URL |
|---|---|---|---|---|
| `hcm` | `approid.team` | `/api/*` | HTTP | `api:4080` |
| `hcm` | `approid.team` | (비움) | HTTP | `web:3110` |
| `admin-hcm` | `approid.team` | (비움) | HTTP | `admin:3111` |

**URL 은 Compose 서비스 이름 + 컨테이너 포트다.** `hcm-api` 같은
이름은 풀리지 않아 502 가 된다.

**`/api/*` 행이 "비움" 행보다 위에 있어야 한다.**

## 검증

```bash
./infra/scripts/verify-deployment.sh
```

이 스크립트는 호스트 포트를 쓰지 않는다. 컨테이너 안에서
`docker compose exec` 로 검사하고, 공개 검사는 터널 경유로 한다.

| 검사 | 기대값 |
|---|---|
| `api` 컨테이너 readiness | `UP` |
| `web` 컨테이너 root | HTML |
| `admin` 컨테이너 root | HTML |
| `https://hcm.approid.team/api/hotels` | HTTP 200 JSON (터널 경유) |
| `https://hcm.approid.team/` | HTML (터널 경유) |
| `https://admin-hcm.approid.team/` | HTML (터널 경유) |

앞의 3개가 실패하면 컨테이너가 아직 준비되지 않은 것이다.
**뒤의 3개(터널)만 실패하면 Cloudflare 설정 문제**다. Public
Hostname 의 URL 이 `api:4080`·`web:3110`·`admin:3111` 인지 확인한다.

## 디버깅 (호스트 포트가 없으므로)

```bash
# 컨테이너 안으로 들어가서 직접 검사
docker compose --profile tunnel exec api sh
docker compose --profile tunnel exec web sh
docker compose --profile tunnel exec admin sh

# 터널 컨테이너에서 다른 서비스가 보이는지 확인
docker compose --profile tunnel exec tunnel wget -qO- http://api:4080/actuator/health
docker compose --profile tunnel exec tunnel wget -qO- http://web:3110/
docker compose --profile tunnel exec tunnel wget -qO- http://admin:3111/
```

**로컬 개발처럼 `curl http://127.0.0.1:3201` 을 쓸 수 없다.** 호스트
포트가 없다. 항상 `docker compose exec` 로 들어간다.

## 환경변수

`infra/secrets/compose.env`:

```env
# 더 이상 사용하지 않는다. 호스트 포트를 열지 않는다.
# HCM_API_PORT=3201
# HCM_WEB_PORT=3110
# HCM_ADMIN_PORT=3111
```

로컬 개발은 `compose.yaml` 옆의 환경변수를 쓴다:

```env
HOTEL_DB_PORT=55432
HOTEL_API_PORT=4080
```
