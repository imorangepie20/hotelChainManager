# Cloudflare Tunnel 등록 메뉴얼

> 최종 갱신: 2026-09-26
> 이 메뉴얼은 `infra/compose.zorin.yml` 의 실제 서비스 이름·포트와
> 1:1 로 맞춰져 있다. 값을 바꾸면 이 문서도 같이 바꾼다.

## 0. 이 배포의 구조

```
인터넷 ──https──> Cloudflare ──> Cloudflare Tunnel ──> Docker 네트워크
                                                        hcm-frontend
                                                       ┌────────────┐
                                                       │ tunnel     │
                                                       │ api  :4080 │
                                                       │ web  :3110 │
                                                       │ admin:3111 │
                                                       └────────────┘
```

**호스트 포트를 하나도 열지 않는다.** 서버에 여러 프로젝트가 있어
포트가 겹치면 안 되기 때문이다. Compose 파일에 `ports:` 가 아니라
`expose:` 만 있다. 외부에서 들어오는 유일한 길은 이 터널이다.

**터널은 컨테이너 네트워크 `hcm-frontend` 에 붙어 있다.** 따라서
Public Hostname 의 URL 에는 **컨테이너 포트**를 쓴다.
`127.0.0.1:3110` 같은 호스트 주소가 아니다.

## 1. 터널 만들기

1. https://one.dash.cloudflare.com/ 접속
2. 왼쪽 메뉴 `Networks` → `Tunnels`
3. `Create a tunnel`
4. 터널 유형: **Cloudflared**
5. 터널 이름: `hcm`
6. `Save tunnel`
7. 다음 화면(**Install and run connector**)에서 **token 을 복사**한다.

   > ⚠️ **토큰은 이 화면에서 한 번만 보여준다.**
   > 다음 화면으로 넘어가면 다시 볼 수 없다. 잃어버리면
   > `Tunnels` → `hcm` → `Configure` → `Install connector` 에서
   > 재발급해야 한다.

   토큰은 `eyJhIjoi...` 로 시작하는 긴 문자열이다.

## 2. Public Hostname 등록

`Tunnels` → `hcm` → `Configure` → `Public Hostname` →
`Add a public hostname`

### 등록할 값 3개

| # | Subdomain | Domain | Path | Type | URL |
|---|---|---|---|---|---|
| 1 | `hcm` | `approid.team` | `/api/*` | HTTP | `api:4080` |
| 2 | `hcm` | `approid.team` | **(비움)** | HTTP | `web:3110` |
| 3 | `admin-hcm` | `approid.team` | **(비움)** | HTTP | `admin:3111` |

> **주의 — 위 표를 그대로 입력한다.** 이전 문서의 `web:8080`·
> `hcm-api:4080`·`hcm-admin:3111` 은 **잘못된 값**이다. Compose 가
> `expose` 로 바뀌면서 서비스 이름과 포트가 바뀌었다.

> **`/api/*` 행이 "비움" 행보다 위에 있어야 한다.** 순서는 3번 참조.

> **관리자 호스트는 `admin-hcm.approid.team`을 쓴다.** Cloudflare의
> 기본 Universal SSL은 full setup에서 첫 단계 서브도메인까지만
> 보장하므로 `admin.hcm.approid.team` 같은 다단계 호스트는 Total TLS나
> 별도 인증서 없이는 TLS 오류가 난다.

### 입력칸 하나하나

**Public hostname**

| 칸 | 값 |
|---|---|
| Subdomain | `hcm` (또는 `admin-hcm`) |
| Domain | `approid.team` (드롭다운에서 선택) |
| Path | `/api/*` 또는 **비움** |

**Service**

| 칸 | 값 |
|---|---|
| Type | `HTTP` |
| URL | `api:4080` (또는 `admin:3111`) |

**나머지 칸은 전부 기본값**이다. `Additional application settings` 는
펼치지 않아도 된다. 특히 **TLS · Origin Server Address · HTTP
Settings 는 건드리지 않는다.**

### 왜 서비스 이름인가

Compose 에 `container_name` 을 지정하지 않았다. 그래서:

- 컨테이너 이름: `hotel-chain-manager-api-1` (자동 생성)
- **네트워크상 DNS 이름: `api`** (서비스 이름)

터널 컨테이너는 `hcm-frontend` 네트워크에 있으므로 `api` 로
요청하면 API 컨테이너에 닿는다. `hcm-api` 라는 이름은 어디에도
없어서 **502 Bad Gateway** 가 된다.

### 왜 컨테이너 포트인가

`expose:` 로 선언된 포트는 컨테이너 안에서만 의미가 있다.

| 서비스 | 컨테이너 포트 | (호스트 포트 없음) |
|---|---|---|
| `api` | `4080` | - |
| `web` | `3110` | - |
| `admin` | `3111` | - |

터널이 `api:4080` 으로 보내면 API 컨테이너가 받는다. 이 값은
`infra/compose.zorin.yml` 의 `expose:` 와 정확히 같아야 한다.

터널 안쪽은 암호화되지 않은 컨테이너 네트워크다. **TLS 는
Cloudflare 가 클라이언트와의 구간에서 종료**한다. 그래서
`hcm.approid.team` 은 `https` 이지만 터널 안쪽은 `http` 다.
`HTTPS` 로 설정하면 인증서가 없어서 연결이 실패한다.

## 3. 도메인 구조 (이해하면 순서가 명확해진다)

고객 웹과 API 는 **같은 도메인**을 공유한다. 고객 웹이
`fetch('/api/...')` 상대 경로를 쓰므로 같은 출처여야 CORS 없이
동작한다. nginx 가 도메인 안에서 경로를 나눈다:

```
hcm.approid.team
├── /api/*   →  API 컨테이너 (Spring Boot)
└── /        →  정적 파일 (고객 웹, Vite 빌드 결과)
```

**nginx 가 `/` 와 `/api/` 를 나눈다.** 그래서 전체 경로(비움)은
`api:4080` 이 아니라 **`web:3110`** 이다.

```
hcm.approid.team/api/*    →  api:4080
hcm.approid.team/         →  web:3110    ← nginx
admin-hcm.approid.team/   →  admin:3111
```

## 4. 등록 순서 (중요)

Cloudflare 는 **더 구체적인 Path 가 먼저 매칭**된다. 하지만
구체도가 같으면 **등록 순서**를 따른다.

**`/api/*` 행이 "비움" 행보다 위에 있어야 한다.**

| 순서 | Path | URL |
|---|---|---|
| 1 | `/api/*` | `api:4080` |
| 2 | (비움) | `web:3110` |

> 등록 후 공개 API 검증에는 실제 애플리케이션 경로인
> `https://hcm.approid.team/api/hotels`를 사용한다. Spring Actuator는
> `/actuator/health`이고 `/api/actuator/health`가 아니므로 후자는
> 터널이 정상이어도 404가 맞다.

> **`/` 를 `api:4080` 으로 등록하면 안 된다.** API 컨테이너는
> 정적 파일이 없어서 고객 웹이 빈 페이지가 된다. `/` 는
> **반드시 `web:3110`** 이다.

## 5. DNS 레코드

**터널이 자동으로 만든다. 수동으로 넣지 않는다.**

Public Hostname 을 저장하면 Cloudflare 가 `approid.team` zone 에
CNAME 을 추가한다:

| 이름 | 유형 | 값 |
|---|---|---|
| `hcm` | CNAME | `<tunnel-id>.cfargotunnel.com` |
| `admin-hcm` | CNAME | `<tunnel-id>.cfargotunnel.com` |

전제 조건: **`approid.team` zone 이 Cloudflare 에 있어야 한다.**
(이미 있다.) 다른 DNS 공급자가 관리 중이면 먼저 zone 을 이전한다.

## 6. 토큰 저장

서버에서 **반드시 먼저** 실행한다:

```bash
./infra/scripts/set-tunnel-token.sh eyJhIjoi...
```

이 스크립트는 `infra/secrets/tunnel.env` 에
`TUNNEL_TOKEN=eyJhIjoi...` 을 쓴다.

- `.gitignore` 가 무시한다. **저장소에 들어가지 않는다.**
- `chmod 600` 으로 보호한다.
- 토큰이 40자 미만이면 거부한다.

**`docker compose up` 보다 먼저** 실행해야 한다. `tunnel` 서비스가
`env_file: ./secrets/tunnel.env` 를 읽기 때문이다. 파일이 없으면
터널이 시작부터 실패한다.

## 7. Access 정책 (권장)

`admin-hcm.approid.team` 은 관리자 로그인 페이지다. 공개 인터넷에
노출된다. 백엔드 권한 검증은 Spring Boot 에 있지만 **한 겹 더
두는 것을 권장**한다.

1. `Access` → `Applications` → `Add an application`
2. `Self-hosted`
3. **Application name**: `HCM Admin`
4. **Session Duration**: `24 hours`
5. **Application domain**: `admin-hcm.approid.team`
6. `Next`
7. **Policy name**: `Allow admins`
8. **Action**: `Allow`
9. **Include**: `Emails ending in` → 본인 이메일 도메인
10. `Save`

이제 `admin-hcm.approid.team` 을 열면 Cloudflare 로그인이 먼저
나타나고, 통과한 뒤 관리자 로그인 페이지로 간다.

**고객 웹(`hcm.approid.team`)에는 Access 정책을 붙이지 않는다.**
공개 사이트다.

## 8. 배포와 검증

```bash
# 서버에서
cd ~/apps/hotel-chain-manager

# 토큰 저장 (최초 1회)
./infra/scripts/set-tunnel-token.sh eyJhIjoi...

# 기동 (첫 기동은 2~5분)
docker compose --env-file infra/secrets/compose.env \
  -f infra/compose.zorin.yml --profile tunnel up -d --build --wait

# 검증
./infra/scripts/verify-deployment.sh
```

또는 Windows 에서:

```powershell
.\infra\scripts\deploy-zorin.ps1 -Push -Up
.\infra\scripts\deploy-zorin.ps1 -Verify
```

### 검증 결과 읽는 법

`verify-deployment.sh`:

| 검사 | 기대값 |
|---|---|
| `api` readiness | `UP` |
| `web` root | HTML |
| `admin` root | HTML |
| `https://hcm.approid.team/api/hotels` | HTTP 200 JSON |
| `https://hcm.approid.team/` | HTML |
| `https://admin-hcm.approid.team/` | HTML |

| 결과 | 원인 | 해결 |
|---|---|---|
| 전부 실패 | 컨테이너가 안 떠 있다 | `docker compose ps` |
| 앞 3개만 실패 | 헬스체크 시간 부족 | 5분 뒤 재실행 |
| 터널만 실패, 502 | **서비스 이름/포트 불일치** | 2번 표와 대조 |
| 터널만 실패, 530 | 터널이 켜지지 않음 | 토큰 확인 (6번) |
| 터널만 실패, DNS | 레코드가 안 만들어짐 | 5번 확인 |

> `verify-deployment.sh` 의 앞 3개 검사는 각 컨테이너 안에서
> `127.0.0.1`의 컨테이너 포트를 검사한다. 뒤의 3개는 Cloudflare
> Tunnel을 경유한 공개 HTTPS 응답을 검사한다.

### 502 로 디버깅

```bash
# 서버에서 컨테이너끼리 직접 통신하는지 확인 (호스트 포트 없이)
docker compose --profile tunnel exec tunnel wget -qO- http://api:4080/actuator/health
docker compose --profile tunnel exec tunnel wget -qO- http://web:3110/
docker compose --profile tunnel exec tunnel wget -qO- http://admin:3111/
```

**이 명령이 `UP` / HTML 을 돌려주면 컨테이너는 정상이고 문제는
Cloudflare 설정이다.** 돌려주지 못하면 서비스가 죽어 있거나
네트워크가 잘못됐다.

## 9. 터널 상태 확인

```bash
# 터널 컨테이너 로그
docker compose --profile tunnel logs tunnel

# Cloudflare 가 터널을 healthy 로 보는지
docker compose --profile tunnel logs tunnel | grep -i "Registered tunnel connection"
```

정상이면 `Registered tunnel connection` 줄이 4개 나온다
(Cloudflare 가 연결을 4개 맺는다).

## 10. 자주 하는 실수

| 실수 | 증상 | 해결 |
|---|---|---|
| URL 에 `hcm-api` 사용 | 502 | `api` 로 변경 |
| URL 에 `hcm-admin` 사용 | 502 | `admin` 으로 변경 |
| URL 에 `127.0.0.1:4080` 사용 | 502 (터널은 호스트가 아님) | `api:4080` |
| URL 에 호스트 포트 `3201` 사용 | 502 | 컨테이너 포트 `4080` |
| `/` 를 `api:4080` 으로 등록 | 고객 웹이 빈 페이지 | `web:3110` 으로 변경 |
| URL 에 `web:8080` / `web:80` 사용 | 502 | `web:3110` 으로 변경 |
| Type 을 `HTTPS` 로 | 525 / 연결 실패 | `HTTP` |
| `/api/*` 가 아래에 | `/api/*` 404 | 순서 바꾸기 |
| `tunnel.env` 없이 `up` | 터널 시작 실패 | 6번 먼저 |
| 수동 DNS 입력 | 터널과 충돌 | 5번, 자동에 맡기기 |

## 11. 구성 요약

```
infra/compose.zorin.yml
├── api      expose 4080   networks [backend, frontend]
├── web      expose 3110   networks [frontend]
├── admin    expose 3111   networks [frontend]
└── tunnel                   networks [frontend]
              env_file ./secrets/tunnel.env
```

```
Cloudflare Tunnel "hcm"
├── hcm.approid.team/api/*  →  api:4080    (Spring Boot)
├── hcm.approid.team/       →  web:3110    (nginx, 정적 파일)
└── admin-hcm.approid.team  →  admin:3111  (Next.js)
```
