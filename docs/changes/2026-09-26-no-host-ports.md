# 포트 충돌 제거 - 호스트 포트를 아예 열지 않는다

> 날짜: 2026-09-26
> 작업 트리에만 있고 커밋하지 않은 상태.

## 배경

사용자가 "8080도 안 돼", "서비스하는 프로젝트가 많아서 포트 충돌나면
안 돼"라고 지시했다. 이전 작업에서 고객 웹 nginx 의 수신 포트를
`80` → `8080` 으로 바꿨지만, **그것은 근본 해결이 아니었다.** `8080`
역시 서버의 다른 프로젝트가 쓸 수 있고, 더 본질적으로는 **호스트
포트를 여는 한 어떤 번호든 충돌 가능성이 남는다.**

배포 구성을 살펴보니 호스트 포트 3개(`3201`·`3110`·`3111`)를
`127.0.0.1` 에 바인딩하고 있었다. 이 값들은 서버 안의 다른 프로젝트
(`b2b-stm` `3200`, `sdtpl-adm`·`real-es`·`music-pie`·`alpha-momega`
`3100`~`3104`)와 같은 대역이라 의도적으로 피해서 골랐지만, **같은
서버에 프로젝트가 계속 늘어나면 언젠가는 겹친다.**

## 해결: 호스트 포트를 하나도 열지 않는다

**`ports:` 를 전부 `expose:` 로 바꿨다.** Compose 의 `expose:` 는
호스트에 포트를 바인딩하지 않고 컨테이너 네트워크 안에서만 열어준다.

| 서비스 | 이전 | 이후 |
|---|---|---|
| `api` | `ports: 127.0.0.1:3201:4080` | `expose: 4080` |
| `web` | `ports: 127.0.0.1:3110:8080` | `expose: 3110` |
| `admin` | `ports: 127.0.0.1:3111:3000` | `expose: 3111` |

**터널이 컨테이너 네트워크 `hcm-frontend` 에 붙어 있으므로 호스트
포트가 필요 없다.** 터널 → `api:4080` 으로 직접 통신한다. 이것이
포트를 아예 열지 않아도 되는 이유다.

`docker compose ps` 를 실행하면 `PORTS` 칸이 비어 있다. **이것이
정상이다.**

## 컨테이너 포트도 프로젝트 전용 대역으로 통일

80·8080·3000 을 전부 없애고 `3110`·`3111` 로 모았다.

| 서비스 | 이전 컨테이너 포트 | 이후 |
|---|---|---|
| 고객 웹 nginx | `80` → `8080` | `3110` |
| 관리자 Next.js | `3000` | `3111` |

- `apps/web/infra/nginx.conf`: `listen 8080` → `listen 3110`
- `apps/web/infra/Dockerfile`: `EXPOSE 8080` → `EXPOSE 3110`
- `SDTPL_ADM/infra/Dockerfile`: `ENV PORT=3000` → `ENV PORT=3111`,
  `EXPOSE 3000` → `EXPOSE 3111`
- `infra/compose.zorin.yml` 의 헬스체크 3곳도 같은 포트로.

**로컬 개발(`compose.yaml`)은 건드리지 않았다.** 개발 머신에서만
실행하므로 `4080`·`55432`·`9000` 호스트 포트를 그대로 연다.

## 검증 스크립트를 컨테이너 기반으로 재작성

`verify-deployment.sh` 는 `http://127.0.0.1:3201` 등 **호스트
포트를 찌르고 있었으므로 더 이상 동작하지 않았다.** 전부
`docker compose exec` 로 컨테이너 안에서 검사하게 고쳤다.

```bash
docker compose … --profile tunnel exec -T api \
  wget -q -O - http://127.0.0.1:4080/actuator/health/readiness
```

컨테이너 안의 `127.0.0.1` 은 **컨테이너 자신**이므로 이것은
`expose` 된 포트를 검사한다. 터널 경유 검사 3개는 그대로 `https` 로
둬서 Cloudflare 설정 문제와 컨테이너 문제를 분리했다.

## Cloudflare 터널 등록 메뉴얼

`infra/CLOUDFLARE-TUNNEL.md` 를 새로 만들었다. 이전 문서는 표
하나에 그쳤는데, 입력칸 하나하나·순서·실수 목록·디버깅까지
11개 절로 정리했다.

**등록값 (이것만 보면 된다):**

| Subdomain | Domain | Path | Type | URL |
|---|---|---|---|---|
| `hcm` | `approid.team` | `/api/*` | HTTP | `api:4080` |
| `hcm` | `approid.team` | (비움) | HTTP | `web:3110` |
| `admin-hcm` | `approid.team` | (비움) | HTTP | `admin:3111` |

메뉴얼에서 가장 중요한 부분 두 가지:

1. **`/` (전체 경로)는 `api:4080` 이 아니라 `web:3110` 이다.**
   nginx 가 도메인 안에서 `/` 는 정적 파일, `/api/` 는 API 로
   나눈다. 이전 문서는 둘 다 `api:4080` 으로 적어서 **고객 웹이 빈
   페이지가 될 결함**이 있었다.
2. **URL 은 서비스 이름 + 컨테이너 포트다.** `hcm-api`·`hcm-admin`
   은 풀리지 않아 502 이고, `127.0.0.1:4080` 은 터널 컨테이너
   자신을 가리킨다.

그 외에 담은 내용: 토큰을 한 번만 보여준다는 경고, DNS 자동
생성, Access 정책으로 관리자 도메인 한 겹 더 보호, 502 디버깅
(`docker compose exec tunnel wget …`), `Registered tunnel
connection` 로그 4개, 자주 하는 실수 표 10개.

## 문서 일관성

`PORTS.md` 와 `DEPLOY-ZORIN.md` 도 전부 새 포트로 고쳤다.
`PORTS.md` 에는 **컨테이너 포트(터너 URL) vs 호스트 포트(로컬
개발)** 를 비교한 단락과 **디버깅은 항상 `docker compose exec` 로
들어간다** 는 안내를 넣었다. `set-tunnel-token.sh` 의 안내
메시지와 `compose.env.example` 도 같이 맞췄다.

## 검증 결과

- **compose 유효성**: `docker compose … config --quiet` 종료
  코드 0. 출력에서 `ports:` / `published` 가 **0개**, `expose:` 가
  3개(`4080`·`3110`·`3111`)인 것을 확인했다.
- **nginx 수신과 프록시**: 실제 `nginx:1.29-alpine` 에 프로젝트의
  `nginx.conf` 를 넣고 `127.0.0.1:13110:3110` 으로 기동했다.
  - `http://127.0.0.1:13110/` → **STATUS 200** (정적 파일 정상)
  - `http://127.0.0.1:13110/api/actuator/health` → **STATUS 502**
    (프록시 대상을 의도적으로 존재하지 않는 `9999` 로 지정.
    **502 가 나왔다는 것은 `/api/` 가 정상적으로 프록시되고
    있다는 뜻이다.** 404 였으면 nginx 가 프록시하지 않고 정적
    파일로 처리한 것이다.)
  - `${API_PROXY_TARGET}` 환경변수 치환 정상.
- **nginx 단순 수신**: `listen 3110` 만 단독으로 넣었을 때
  STATUS 200, 본문 `port-3110-ok`.

## 미검증 항목

- **서버에서 `docker compose up` 을 실행하지 않았다.** 컨테이너
  빌드도 돌지 않았다. nginx 단독 컨테이너로 검증했을 뿐, 전체
  파이프라인에서 `expose` 와 헬스체크·터널이 함께 동작하는지는
  서버에서 기동해야 알 수 있다.
- **Cloudflare 대시보드에 Public Hostname 을 등록하지 않았다.**
  사용자가 `CLOUDFLARE-TUNNEL.md` 의 표를 보고 직접 등록한다.
- **터널 경유 검사(`https://…`)를 실행하지 않았다.** 터널과
  도메인이 서버에 없다.
- **로컬 개발 `compose.yaml` 은 그대로** 라서 로컬에서는 여전히
  `127.0.0.1:4080` 로 접근한다. 이것은 의도적이다.
- **관리자 Next.js 가 `3111` 에서 기동하는지 확인하지 않았다.**
  `ENV PORT=3111` 과 `EXPOSE 3111` 로 바꿨지만 Next standalone
  서버를 직접 띄우지는 않았다. 서버 기동 시 확인한다.

## 다음 작업

1. `.\infra\scripts\deploy-zorin.ps1 -Push -Up` 으로 서버에서
   기동한다. `--profile tunnel` 이 포함돼야 터널이 뜬다.
2. Cloudflare Zero Trust 에서 `CLOUDFLARE-TUNNEL.md` 2번 표대로
   3개의 Public Hostname 을 등록한다. **`/api/*` 를 제일 위에.**
3. `./infra/scripts/verify-deployment.sh` 로 검사한다.
   **앞의 3개(컨테이너)가 실패하면 아직 준비 중인 것이고, 뒤의
   3개(터널)만 실패하면 Cloudflare 설정 문제**다.
4. 서버에서 디버깅할 때는 `curl http://127.0.0.1:3201` 이 아니라
   `docker compose --profile tunnel exec api sh` 로 들어간다.
