# 현재 개발 상태











최종 갱신: 2026-09-26

## 관리자 호텔·객실 지점 선택 명확화 (2026-09-26)

- 호텔·객실과 재고·가격 화면에 현재 지점의 선택 행, `aria-pressed`, `선택됨` 배지와 결과 제목 지점명을 추가했다.
- 재고·가격 화면은 하드코딩 지점 대신 서버 지점 목록을 사용한다. 전환 중 이전 결과를 숨기고 현재 지점 응답만 표시한다.
- 저장 중 지점 전환을 잠그고, 이전 지점의 늦은 객실 기본값·요금 상세 응답은 generation 검사로 폐기한다.
- 관련 관리자 Chromium 61건과 TypeScript 검사가 통과했다. ESLint는 오류 0건, Effect 내 상태 갱신 경고 6건이다. 상세 기록은 [관리자 호텔·객실 지점 선택 명확화](../changes/2026-09-26-admin-hotel-selection-clarity.md)를 따른다.
- 전체 변경을 `cdbea6e`로 커밋·푸시하고 Zorin 서버에 배포했다. API·고객 웹·관리자 컨테이너와 Cloudflare Tunnel 경유 공개 주소 3개 검사가 모두 통과했고, Flyway 스키마 버전 64와 터널 QUIC 연결 4개를 확인했다.
- Windows 배포의 바이너리 파이프 손상과 실행 비트 손실을 해결하기 위해 배포 전송을 임시 archive·`scp` 방식으로 변경하고 원격에서 `mvnw`·셸 스크립트 실행 권한을 복원한다.

## 라이브 3개 호텔 객실유형·요금·재고 시드 적용 (2026-09-26)

- 관리자 `객실유형` 화면은 호텔 목록의 첫 항목을, `재고·가격` 화면은 속초 호텔을
  기본 선택하고 있었다. 라이브 DB에는 속초만 객실유형·요금·재고 시드가 있어 서로
  다른 화면 결과처럼 보인 것이 원인이었다.
- 최신 `R__demo_data.sql`을 라이브 서버에 선별 반영하고 API를 재빌드했다. Flyway
  repeatable migration `demo data`가 1건 적용됐으며 기존 속초 행은 덮어쓰지 않았다.
- DB에서 속초·설악·제주 모두 객실유형 3개와 요금제 3개를 확인했다. 실제 HQ 직원
  세션의 객실유형 API도 세 호텔 모두 `totalCount=3`을 반환했다.
- 전체 배포·Cloudflare Tunnel 검증 스크립트가 다시 통과했다. 적용 전 DB와 시드는
  `backup/pre-three-hotel-seed-20260926-180701`에 보관했다. 상세 결과는
  [변경 기록](../changes/2026-09-26-live-three-hotel-room-type-seed.md)에 있다.

## 라이브 관리자 직원 로그인 계정 시드 (2026-09-26)

- 공개 관리자 로그인은 UI·API 프록시 문제가 아니라 서버에서
  `STAFF_DEV_ENABLED=false`, 직원 비밀번호 4개 미설정, 직원 계정 0건인
  것이 원인이었다.
- 사용자가 서버 비밀값 파일에 비밀번호를 직접 설정한 뒤 API만 재생성했고,
  본사 관리자·편집자·발행자와 지점 직원 3명 등 활성 계정 6개가 생성됐다.
- 설정된 HQ 비밀번호로 컨테이너 내부 로그인과
  `https://admin-hcm.approid.team/api/staff/sessions` 공개 프록시 로그인이
  모두 성공했다. 비밀번호와 세션 토큰은 출력하거나 저장소에 기록하지
  않았다. 상세 결과는 [변경 기록](../changes/2026-09-26-live-staff-login-seed.md)에 있다.

## Cloudflare Tunnel 라이브 연결 및 배포 포트 전환 (2026-09-26)

- Zorin 서버의 애플리케이션 컨테이너는 healthy였지만 기존 터널 토큰이
  폐기돼 `Unauthorized: Tunnel not found`를 반복하고 있었다. 새 `hcm`
  터널 토큰을 적용한 뒤 서울 엣지 연결 4개가 정상 등록됐다.
- 배포 파일만 최신 무호스트포트 구성으로 반영해 API·고객 웹·관리자를
  `api:4080`·`web:3110`·`admin:3111`로 다시 빌드·기동했다. 세 서비스 모두
  healthy이며 호스트 published port는 없다.
- 공개 고객 API `https://hcm.approid.team/api/hotels`는 200 JSON, 고객 웹
  `https://hcm.approid.team/`은 200 HTML이다. 관리자는 Universal SSL이
  지원하는 단일 단계 호스트 `https://admin-hcm.approid.team/`을 사용하며
  `/login`으로 307 이동한다.
- 전체 미커밋 기능 소스는 배포하지 않았고 배포 관련 파일 6개만 반영했다.
  실제 관리자 로그인과 예약·결제 브라우저 E2E는 미검증이다. 상세 결과와
  롤백용 백업 경로는 [변경 기록](../changes/2026-09-26-cloudflare-tunnel-live-connection.md)에 있다.

## ECC 설치 - 프로젝트 로컬 `.agents/`에 ECC 2.2.2 minimal (2026-09-26)

- 사용자가 `https://github.com/affaan-m/ECC`의 설치를 지시했다. 이 작업 폴더에는 ECC가 설치돼 있지 않았고(`.opencode/`·`.claude/` 없음), Codex 플러그인 캐시 `~/.codex/plugins/cache/ecc/ecc/2.2.2`는 있었으나 활성 상태가 아니었다.
- `--target antigravity`는 **유일하게 쓰기 대상이 프로젝트 로컬 `.agents/`인 타깃**이었다. `opencode` 타깃은 `OPENCODE_CONFIG_DIR`·`XDG_CONFIG_HOME/opencode`·`~/.config/opencode/` 중 하나에 설치하고 컴파일된 플러그인 페이로드(`.opencode/dist/index.js`·`plugins`·`tools`)를 요구했고, `claude`·`codex`·`qwen`·`hermes` 타깃은 작업 폴더 바깥인 `~/.claude/`·`~/.codex/`·`~/.qwen/`·`~/.hermes/`에 쓴다. 현재 세션의 작업 폴더 권한은 `.agents/` 바깥 쓰기를 허용하지 않으므로 `antigravity`를 선택했다.
- **`--profile minimal`과 `--no-hooks`를 사용했다.** minimal이 선택한 모듈은 `rules-core`·`agents-core`·`commands-core`·`platform-configs`·`skill-unified-memory`·`workflow-quality` 6개, 파일 387개다. 훅은 Impeccable 훅(`.codex/hooks.json`)이 이미 동작 중이어서 **`hookConsent: "declined"`**로 ECC 훅 런타임을 빼고 설치했다.
- 설치 결과는 `.agents/agents/*.md` 68개·`.agents/rules/<언어>-<주제>.md` 122개(평탄화)·`.agents/workflows/*.md` 94개·`.agents/skills/` 160개(기존 Impeccable 포함, ECC 스킬 48종)다. `.agents/ecc-install-state.json`에 연산 387개와 파일별 SHA-256이 기록됐고, **전부 비교해 0건 불일치**를 확인했다.
- **기존 하네스를 훼손하지 않았다.** 루트 `AGENTS.md` 해시가 커밋 `865f1944…`와 동일하고, `.codex/hooks.json`도 Impeccable 훅만 그대로며, `.agents/skills/impeccable/` 내용(스킬 4.3.1, 실행기 0.1.5)도 변경되지 않았다. `git status`의 Git 추적 파일 변경에 ECC 설치 항목은 없다. `.agents/`·`.tmp/`는 `.gitignore`로 제외된다.
- `.agents/ECC.md`를 추가해 설치 범위, ECC 자산과 루트 `AGENTS.md`·프로젝트 문서의 **적용 순위**(호텔 규칙이 우선, ECC 규칙은 충돌하지 않을 때만 보조), 알려진 충돌(최소 커버리지 80%·TDD 의무화·파일 크기 권장), 사용 자산 목록, 미검증 항목, 유지보수 방법을 지정했다.
- **미검증**: ECC 스킬·워크플로·에이전트의 실제 실행, 새 세션에서의 노출 여부, `.agents/rules/`가 항상 로드되는 규칙으로 인식되는지 여부, `unified-memory`·`continuous-learning-v2`가 만드는 로컬 상소 파일, 다른 PC·하네스에서의 재현. 상세 범위는 [계획](../plans/2026-09-26-ecc-install.md)과 [변경 기록](../changes/2026-09-26-ecc-install.md)을 따른다.

## 포트 충돌 제거 - 호스트 포트를 하나도 열지 않는다 (2026-09-26)

- 사용자가 "8080도 안 돼", "서비스하는 프로젝트가 많아서 포트 충돌나면 안 돼"라고 지시했다. 바로 앞 작업에서 nginx 수신 포트를 `80`→`8080`로 바꿨지만 **근본 해결이 아니었다.** `8080`도 다른 프로젝트가 쓸 수 있고, 더 본질적으로는 **호스트 포트를 여는 한 어떤 번호든 충돌 가능성이 남는다.**
- **해결은 `ports:` 를 전부 `expose:` 로 바꾼 것이다.** `expose:` 는 호스트에 바인딩하지 않고 컨테이너 네트워크 안에서만 연다. 터널이 `hcm-frontend` 네트워크에 붙어 있어서 호스트 포트 없이 `api:4080` 으로 직접 통신하므로 이것이 가능하다. `api`·`web`·`admin` 세 서비스의 호스트 포트(`3201`·`3110`·`3111`)를 전부 없앴다. **`docker compose ps` 를 실행하면 `PORTS` 칸이 비어 있는 것이 정상이다.**
- **컨테이너 포트도 80·8080·3000 을 없애고 `3110`·`3111` 로 통일했다.** `nginx.conf`(`listen 3110`)·`apps/web/infra/Dockerfile`(`EXPOSE 3110`)·`SDTPL_ADM/infra/Dockerfile`(`ENV PORT=3111`, `EXPOSE 3111`)·compose 헬스체크 3곳. **로컬 개발 `compose.yaml` 은 건드리지 않았다** — 개발 머신에서만 실행하므로 `4080`·`55432`·`9000` 호스트 포트를 그대로 연다.
- **`verify-deployment.sh` 가 더 이상 동작하지 않았다.** 호스트 포트를 찌르고 있었기 때문이다. 전부 `docker compose exec` 로 컨테이너 안에서 검사하게 재작성했다. 컨테이너 안의 `127.0.0.1` 은 컨테이너 자신이므로 `expose` 된 포트를 검사한다. 터널 경유 `https` 검사 3개는 그대로 둬서 Cloudflare 설정 문제와 컨테이너 문제를 분리했다.
- **`infra/CLOUDFLARE-TUNNEL.md` 를 새로 만들었다.** 이전 문서는 표 하나에 그쳤는데, 입력칸 하나하나·등록 순서·실수 목록 10개·502 디버깅·Access 정책까지 11개 절로 정리했다. **가장 중요한 정정: `/` (전체 경로)는 `api:4080` 이 아니라 `web:3110` 이다.** nginx 가 도메인 안에서 `/` 는 정적 파일, `/api/` 는 API 로 나눈다. 이전 문서는 둘 다 `api:4080` 으로 적어서 **고객 웹이 빈 페이지가 될 결함**이 있었다.
- **검증**: `docker compose … config --quiet` 종료 코드 0, 출력에서 `ports:`/`published` **0개**·`expose:` **3개**(`4080`·`3110`·`3111`) 확인. 실제 `nginx:1.29-alpine` 에 프로젝트 `nginx.conf` 를 넣고 기동하니 `http://…/` → **STATUS 200**, `http://…/api/actuator/health` → **STATUS 502** (프록시 대상을 의도적으로 존재하지 않는 `9999` 로 지정했으므로 **502 가 나온 것이 `/api/` 가 정상적으로 프록시되고 있다는 뜻**이다). `PORTS.md`·`DEPLOY-ZORIN.md`·`set-tunnel-token.sh`·`compose.env.example` 도 전부 새 포트로 맞췄다.
- **서버에서 `docker compose up` 을 실행하지 않았다.** 컨테이너 빌드도, Cloudflare 대시보드 등록도, 터널 경유 검사도 안 했다. nginx 단독 컨테이너 검증뿐이다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-26-no-host-ports.md)을 따른다.

## 80번 포트 제거·포트 맵 문서화 (2026-09-26)

- 사용자가 "80번 포트는 사용하지 마"라고 지시했다. 고객 웹 컨테이너의 nginx가 컨테이너 안에서 `80`을 수신하고 있었고, Cloudflare Public Hostname URL도 `web:80`이었다. **컨테이너 내부 포트를 `8080`으로 바꿨다.** 호스트 포트 `3110`은 그대로라서 `verify-deployment.sh`와 서버의 다른 서비스에 영향이 없다. `nginx.conf`(`listen 8080`)·`Dockerfile`(`EXPOSE 8080`)·`compose.zorin.yml`(`3110:8080`, 헬스체크 `8080`) 세 곳을 같이 바꿨다.
- **터널 등록값에 숨어 있던 별도의 결함을 같이 고쳤다.** Compose에 `container_name`을 지정하지 않았으므로 Compose 네트워크 안에서 DNS로 풀리는 이름은 **서비스 이름**인데, 문서와 스크립트는 `hcm-api`·`hcm-admin`·`hcm-web`을 쓰고 있었다. 이 이름들은 풀리지 않아 **터널이 502**를 냈을 것이다. `set-tunnel-token.sh`는 더 심했다 — `hcm-admin:3111`은 **호스트 포트**까지 적혀 있었다. `api:4080`·`web:8080`·`admin:3000`으로 바꿨다.
- **`infra/PORTS.md`를 추가했다.** 포트 할당이 6개 파일에 흩어져 있었고 로컬 개발과 배포의 포트 체계가 서로 달랐다. 로컬(`55432`·`55433`·`4080`·`9000`·`59090`·`4000`·`3000`)과 배포(`3201`·`3110`·`3111`, 전부 `127.0.0.1`만), 대역 규칙(`3100`~`3104`·`3200`은 같은 서버의 다른 프로젝트가 쓴다), 80번을 쓰지 않는 이유, **컨테이너 포트(터널 URL) vs 호스트 포트(검증 스크립트)** 를 한 곳에 모았다.
- **nginx 설정을 실제 이미지로 검증했다.** `nginx:1.29-alpine`에 프로젝트 `nginx.conf`를 주입해 `nginx -t`를 실행했고 `syntax is ok`·`test is successful`, `${API_PROXY_TARGET}` 환경변수 치환도 통과했다. nginx 컨테이너를 `127.0.0.1:18080:8080`로 띄워 `http://127.0.0.1:18080/`에 접근하니 **STATUS=200**, 본문 `port-8080-ok`. 8080 수신을 확인했다.
- **서버에서 `docker compose up`을 실행하지 않았다.** 컨테이너 빌드도 돌지 않았고 Cloudflare 대시보드에도 등록하지 않았다. `nginx.conf` 단독 검증뿐이다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-26-port-8080.md)을 따른다.

## 서버 배포·Cloudflare 터널 - 배포 차단 결함 수정 (2026-09-26)

- `infra/` 배포 구성(compose·Dockerfile·스크립트·문서)은 이미 거의 완성돼 있었고, 서버 배포와 Cloudflare 터널 연결을 진행 중이었다. 배포를 실행하기 전에 구성을 확인했을 때 **첫 기동을 깨거나 화면 하나를 통째로 쓸 수 없게 만드는 결함 4가지**가 있었다. 코드가 아니라 "어느 환경에서 무엇을 가리키는가"에 관한 것들이었다.
- **dev 프로필 시드가 속초 지점만 심었다.** `R__demo_data.sql`은 3개 지점 행은 만들었지만 객실 유형·요금·재고를 속초 3개 유형만 심었으므로, 고객이 설악산·제주를 검색하면 **오퍼가 한 건도 나오지 않았다**. 반면 `R__web_content_data.sql`은 3개 지점 콘텐츠를 모두 심어서 지점 선택기는 세 지점을 다 보여줬다. 설악산·제주의 유형·요금제·90일분 일자 요금·재고를 추가했고 **모든 insert가 `ON CONFLICT DO NOTHING`이라 기존 DB의 속초 행은 바뀌지 않는다.** 이 파일은 dev 프로필에서만 읽히고 테스트는 기본 프로필을 쓰므로 테스트에 영향이 없다.
- **관리자 `/concierge` rewrite가 컨테이너 자신을 가리켰다.** `CONCIERGE_PROXY_TARGET`가 정의되지 않으면 `http://127.0.0.1:9000`이 기본값이었고, 컨테이너 안에서 이것은 **관리자 컨테이너 자신**이라 도우미가 없는데 연결을 시도했다. 빈 값을 주면 **rewrite 자체를 만들지 않게** 했고, compose는 `${CONCIERGE_PROXY_TARGET-}`(`:-`가 아닌 `-`)로 빈 값을 전달한다. `staff-api.ts`가 404를 만나면 "AI 도우미가 실행 중이 아닙니다. 이 배포에는 도우미가 포함되지 않았습니다."를 보여주고, 502는 도우미가 있을 때의 일반 장애 안내로 그대로 뒀다. e2e에 404 케이스를 추가했다.
- **`NEXT_PUBLIC_CUSTOMER_WEB_ORIGIN`이 런타임 environment에만 있었다.** `NEXT_PUBLIC_*` 변수는 **Next.js 빌드 시점에 클라이언트 번들에 구워지므로** 런타임 환경변수로는 아무것도 바뀌지 않는다. 그대로 두면 배포된 관리자의 미디어 미리보기·콘텐츠 미리보기·저장 초안 링크가 전부 로컬 개발 주소 `http://127.0.0.1:4000`을 가리킬 뻔했다. admin Dockerfile에 build arg를 추가하고 compose build args에 배포 도메인을 넣었다.
- **터널 비밀값은 저장소에 없다.** `.gitignore`가 `infra/secrets/tunnel.env`·`compose.env`·`*.env`를 무시하고 예제만 살려두며, `git check-ignore`이 무시됨을 확인했다.
- **서버에서 `docker compose up`을 실행하지 않았다.** compose 유효성을 `docker compose config`로 검사하지도 않았고 컨테이너 빌드도 돌지 않았다. 읽기 확인(마이그레이션 순서·additive 여부·파일 인코딩)과 로컬 타입·테스트 검증만 했다. 인프라 파일 11개는 전부 UTF-8 BOM 없음·잘못된 시퀀스 0건이었고, 터미널 코드 페이지로 인해 콘솔에서만 글자가 깨져 보였다.
- API `mvnw` `WebsiteMediaIntegrationTest` **26건**·인접 `AvailabilityIntegrationTest`·`HotelCatalogQueryIntegrationTest`·`InventoryQueryIntegrationTest` **22건**·`GuestRequestIntegrationTest` **22건**이 종료 코드 0이다. 관리자 `tsc --noEmit`·`eslint`(경고 0건)·고객 웹 `tsc --noEmit`·`tsx` 검증 스크립트 **25건**도 종료 코드 0이다. 라이브 DB·compose 재빌드·터널·브라우저 확인은 실행하지 않았다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-26-deploy-tunnel-fixes.md)을 따른다.
- **다음 작업은 서버에서 실제 기동이다.** `.\infra\scripts\deploy-zorin.ps1 -Push -Up`으로 첫 기동하고 `verify-deployment.sh`로 헬스체크와 터널 경유 도메인을 검사한다. 앞선 5번 `공통 정책 관리` 남은 항목(환불 규칙, 지점별 정책, 정책 변경의 감사 이력 통합)은 배포가 끝난 뒤에 이어한다.

## 본사 직원 계정 관리 - 삭제·본인 계정 수정 (2026-09-25)

- `admin-menu-roadmap.md` 3번 `직원 계정 관리`의 **마지막 남은 항목**을 구현했다. 1번·2번 영역이 끝나 로드맵 순서(1 → 2 → 3 → 5 → 4 → 7 → 6 → 8)에 따라 3번을 마무리했다. 본사는 직원을 만들고 역할·소속 지점을 바꾸고 비밀번호를 재발급하고 비활성할 수 있었지만 **잘못 만든 계정이나 퇴사가 확정된 계정을 지울 수단이 없었다.** 비활성은 로그인을 막을 뿐 직원 목록에 계속 나타났다. 직원도 **본인 이름을 직접 고칠 수단이 없었다.**
- `DELETE /api/staff/staff/{staffId}`가 직원 계정을 영구 삭제한다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401, 멱원 키 누락은 400, 없는 직원은 404다. **삭제는 "행을 지우는" 것이 아니라 "식별자를 영구적으로 비우는" 것**이다. `staff_member` 행을 남겨두고 이메일을 `deleted-<uuid>@deleted.local` 자리 표시자로, 표시 이름을 `삭제된 직원`으로, 비밀번호를 빈 문자열로, 역할을 `REMOVED`로, 지점을 `null`로, `active`를 `false`로 바꾸고 세션을 지운다. 감사 이력·예약 변경 승인·콘텐츠 발행 이력 20개 표의 참조가 끊기지 않는다.
- **진행 중인 예약 변경 요청이나 정산 실행이 있으면 409 `STAFF_DELETION_CONFLICT`로 거부하고 아무것도 비우지 않는다.** 삭제는 되돌릴 수 없으므로 모든 충돌 검사를 삭제 앞에 둔다. **본인 계정도 409 `STAFF_SELF_MODIFICATION_FORBIDDEN`**이다. 비활성·역할 수정과 같은 예외를 재사용한다.
- `PATCH /api/staff/staff/me`가 **본인**의 표시 이름과 비밀번호를 바꾼다. 전 역할이 쓸 수 있다. **역할·소속 지점은 본사 전용 권한이라 받지 않는다.** 본인 계정 수정이 역할을 바꾸면 본인 권한을 올리는 것이 가능해진다. **비밀번호를 바꿀 때는 현재 비밀번호 확인이 필수**이고 틀리면 400 `STAFF_PASSWORD_MISMATCH`다. **비밀번호를 바꾸면 현재 세션 하나만 남기고 나머지를 끊는다.** `token_hash` 기반이라 현재 세션은 유지된다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `deleted=false`·`changed=false`로 같은 결과를 돌려주고, 응답 유실 뒤 새 키로 같은 내용을 보내도 SHA-256 지문이 같아 같은 결과를 돌려준다. **삭제의 멱원 재호출은 계정 조회 앞에서 검사**해서 계정이 이미 비워졌으면 404로 착각하게 되는 것을 막는다. **삭제·본인 수정은 매번 새 멱원 키를 쓴다.**
- V64 additive 마이그레이션이 `role`의 `CHECK`에 `REMOVED`를 추가하고 `staff_member_hotel_scope_check`에 `REMOVED` 가지를 추가한다. **기존 행은 모두 활성 역할이므로 값을 바꾸지 않는다.**
- 관리자 `/dashboard/staff`의 직원 표에 `삭제` 열과 확인 대화상자를 추가했고, 전 역할이 쓰는 `내 계정` (`/dashboard/me`) 화면을 추가했다. `nav.ts`의 **`운영` 그룹**에 넣어서 지점 직원도 본인 계정을 고칠 수 있게 했다. 서버 검증 실패 시 대화상자를 닫지 않고 이유를 보여준다.
- API `mvnw -Dtest=StaffAccountIntegrationTest` **75건**(기존 47 + 신규 28)이 종료 코드 0이고, 인접 5개 suite **87건**도 종료 코드 0이다. 관리자 `tsc --noEmit`·고객 웹 `tsc --noEmit`·`eslint`(경고 3건, 기존 패턴)이 종료 코드 0이다. Playwright `staff-accounts` **14건**(기존 13 + 신규 4)·`staff-self-account` **7건**(신규)·인접 4개 suite **15건**이 종료 코드 0이다. 라이브 DB·compose 재빌드·브라우저 확인은 실행하지 않았다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-25-hq-staff-delete-self-update.md)을 따른다.
- **3번 `직원 계정 관리` 영역이 완료됐다.** 1번·2번·3번 영역이 끝났으므로 로드맵 순서에 따라 다음은 5번 `공통 정책 관리`의 남은 항목(환불 규칙, 지점별 정책, 정책 변경의 감사 이력 통합)이다.

## 본사 재고·가격 - 재고 일괄 업로드(CSV) (2026-09-24)

- `admin-menu-roadmap.md` 2번 `재고·가격`의 **마지막 남은 항목**을 구현했다. 본사가 여러 날짜의 재고를 바꾸려면 `PATCH .../inventory` 대화상자를 날짜 범위마다 다시 띄워야 했고, 대화상자는 **표시된 전체 숙박일을 하나의 같은 총량값으로 덮어버려서** "주말은 6실, 평일은 8실, 공사 주간은 0실" 같은 날짜별 차등을 한 번에 반영할 수 없었다.
- `GET /api/staff/hotels/{hotelId}/inventory/export`가 객실 유형별 일자 재고를 CSV로 내려준다. SELECT만 사용하고 재고를 변경하지 않는다. `POST /api/staff/hotels/{hotelId}/inventory/import`가 CSV 본문을 받아 여러 객실 유형의 여러 날짜 재고를 한 번에 바꾼다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401, 멱원 키 누락은 400, 없는 지점은 404다.
- **내보낸 CSV를 그대로 다시 올릴 수 있다.** 열 순서가 같고 헤더 행을 무시한다. **총량 열만 반영한다.** `보류`·`확정`·`잔여`·`판매 상태`는 읽기 전용이어서 올려도 무시한다. 업로드가 이 값을 바꾸면 확정 예약과 재고가 어긋난다.
- **한 파일의 모든 행을 한 트랜잭션에 처리한다.** 100행 중 99행만 성공하고 1행이 409로 거부되면 본사가 어느 날짜까지 반영됐는지 알 수 없으므로 전부 성공하거나 전부 실패한다. 총량은 0 이상 1,000 이하, 시드되지 않은 일자는 404 `INVENTORY_DAY_NOT_FOUND`, 확정·보류 건수 아래로 내리면 409 `INVENTORY_CAPACITY_CONFLICT`로 **아무것도 바꾸지 않는다**. 형식 오류는 400 `INVENTORY_IMPORT_FORMAT`이고 행 수 제한은 1,000행이다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `created=false`로 같은 결과를 돌려주고, 응답 유실 뒤 새 키로 같은 파일을 보내도 SHA-256 지문이 같아 같은 결과를 돌려준다. **새 마이그레이션이 없다.** `inventory_command` 표를 재사용하고 CSV 본문 전체가 요청 지문의 입력이 된다.
- 관리자 `/dashboard/inventory`에 `CSV 내보내기`·`CSV 업로드` 버튼을 추가했다. 내보내기는 UTF-8 BOM을 붙여 엑셀이 한글을 깨지지 않게 읽는다(감사 이력·운영 통계와 같은 패턴). 업로드 대화상자는 파일을 고르면 **본문을 미리 보여주고 올릴 행 수를 센 뒤** 올릴 수 있게 한다. **업로드는 매번 새 멱원 키를 쓴다.** 서버 검증 실패 시 대화상자를 닫지 않고 이유를 보여준다.
- API `mvnw -Dtest=InventoryImportIntegrationTest` **17건**, 인접 suite **48건**(재고 쓰기 16·재고 조회 9·판매 중지 17·가용성 6)이 종료 코드 0이다. 관리자 `tsc --noEmit`·`eslint`(경고 2건, 기존 패턴)·Playwright `inventory-viewer` **24건**(기존 19 + 신규 5)이 종료 코드 0이다. 라이브 DB·compose 재빌드·브라우저 확인은 실행하지 않았다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-24-hq-inventory-import.md)을 따른다.
- **2번 `재고·가격` 영역이 완료됐다.** 1번·2번 영역이 끝났으므로 다음은 3번 `직원 계정 관리`의 삭제·본인 계정 수정이다.

## 본사 재고·가격 - 판매 중지 구간 설정 (2026-09-24)

- `admin-menu-roadmap.md` 2번 `재고·가격`의 남은 항목 중 "판매 중지 구간 설정"을 구현했다. 본사가 특정 날짜의 판매를 멈추는 유일한 수단은 **해당 일자의 총량을 0으로 내리는 것**이었는데, 원래 총량을 잃어버리고, 매진과 중지를 구분할 수 없고, 확정 예약이 있는 날짜는 409로 막혀서 **중지 자체가 불가능했다**.
- `PATCH /api/staff/hotels/{hotelId}/room-types/{roomTypeId}/sales-status`가 날짜 구간의 판매 상태를 바꾼다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401, 멱원 키 누락은 400, 없는 지점·다른 지점의 유형·없는 유형은 404다. 본문은 `fromDate`·`toDate`·`status`(`OPEN`·`STOPPED`)이고, 역순은 400, 기간은 최대 92일이다.
- **총량은 건드리지 않는다.** `capacity`·`held`·`confirmed`를 그대로 둬서 재개하면 중지 전과 같은 재고가 돌아온다. 이것이 총량 0으로 내리는 것과의 핵심 차이다. **중지한 구간에 확정·보류된 예약이 있어도 중지할 수 있다.** 총량 0은 409 `INVENTORY_CAPACITY_CONFLICT`로 거부되지만, 중지는 신규 판매만 막고 기존 예약을 그대로 둔다. 지점 전체 중지(`PATCH .../active`)와 같은 원칙이다.
- **중지한 일자는 고객 `GET /api/availability`에서 빠진다.** `AvailabilityService` 검색 쿼리에 `NOT EXISTS (... status = 'STOPPED')`를 붙였다. 예약할 수 없다는 것이지 오류가 아니다. `GET .../sales-status`가 중지 구간을 돌려주고, 재고 조회의 각 일자에 `salesStatus`가 추가돼서 직원 화면이 "매진"과 "중지"를 구분한다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `created=false`로 같은 구간을 돌려주고, 응답 유실 뒤 새 키로 같은 내용을 보내도 SHA-256 지문이 같아 같은 결과를 돌려준다. 동시에 들어오면 유일 인덱스에 걸려 먼저 들어간 쪽의 결과를 돌려준다.
- V63 additive 마이그레이션이 `room_type_sales_status`·`room_type_sales_status_command` 표를 만든다. **기존 표를 변경하지 않는다.** 중지 상태가 없으므로 모든 날짜가 `OPEN`이고 라이브 고객 가용성도 바뀌지 않는다.
- 관리자 `재고·가격` (`/dashboard/inventory`)의 그리드에 `판매 중지` 열을 추가하고, **매진(빨강)과 중지(주황)를 다른 색으로 구분**했다. `판매 중지` 대화상자는 현재 보이는 범위로 날짜를 미리 채우고, 현재 중지 구간과 `판매 재개` 버튼을 보여준다. **중지·재개는 매번 새 멱원 키를 쓴다.** 서버 검증 실패 시 대화상자를 닫지 않고 이유를 보여준다.
- API `mvnw -Dtest=SalesStatusIntegrationTest` **17건**·**전체 719건**(실패 0, 건너뜀 3)·인접 6개 suite **111건**이 종료 코드 0이다. 관리자 `tsc --noEmit`·`eslint`(경고 2건, 기존 패턴)·Playwright `inventory-viewer` **19건**(기존 12 + 신규 7)·인접 3개 suite **59건**이 종료 코드 0이다. 라이브 DB·compose 재빌드·브라우저 확인은 실행하지 않았다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-24-hq-sales-status.md)을 따른다.
- **2번 `재고·가격`의 남은 항목**: 없음. 재고 일괄 업로드(CSV)로 2번 영역이 완료됐다.

## 본사 지점 관리 - 두 번째 요금제 등록·수정 (2026-09-24)

- `admin-menu-roadmap.md` 1번 `지점·객실 유형 관리`의 마지막 남은 항목을 구현했다. 객실 유형 하나에 **요금제가 무조건 1개**였다. 유형을 만들 때 `기본 요금제`가 깔리는 것이 전부고, 본사가 요금제를 직접 만들거나 고칠 수단이 없어서 **같은 객실을 조식 포함·미포함으로 따로 팔 수 없었고**, 취소 규정이 다른 요금을 만들 수도 없었다.
- `POST /api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans`가 새 요금제를 만든다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401, 멱원 키 누락은 400, 없는 지점·다른 지점의 유형·없는 유형은 404다. 본문은 `name`(1~100자)·`breakfastIncluded`·`policyVersion`(1~50자)·`defaultRateKrw`(0~10,000,000원)·`fromDate`(선택)·`days`(1~92, 기본값 90)이고 위반은 400이다.
- **요금제 행과 일자별 금액을 같은 트랜잭션에 심는다.** 실패하면 요금제 행도 롤백돼서 "요금제는 있는데 금액이 없어 오퍼가 안 나오는" 상태가 생기지 않는다. **재고는 심지 않는다.** 재고는 객실 유형 단위(`inventory_day`의 PK가 `room_type_id` + `stay_date`)이고 요금제가 같은 재고를 공유하므로, 요금제 생성이 재고를 만들면 유형의 재고를 덮어쓴다.
- **재고가 없는 날짜는 409 `RATE_PLAN_INVENTORY_DAY_NOT_FOUND`로 거부한다.** 재고가 없는 날짜의 금액을 만들면 고객에게 보여주지도 못하는 요금제가 생긴다. **조식·정책 버전은 예약의 계약 조건이라 이름만 바꿀 수 있다.** 계약 조건이 다른 요금제가 필요하면 새 요금제를 만드는 것이 맞다. 이것이 "두 번째 요금제"를 만드는 이유다.
- `PATCH .../rate-plans/{ratePlanId}`가 이름만 바꾼다. **이름 중복은 같은 유형 안에서만 검사**하고 다른 유형·다른 지점의 같은 이름은 허용하며, 위반은 409 `RATE_PLAN_NAME_CONFLICT`다. 요청한 요금제가 유형에 속하지 않으면 404 `RATE_PLAN_NOT_FOUND`다. `GET .../rate-plans`가 유형의 전체 요금제를 돌려주고 `PATCH .../rates`·`GET .../rates`가 선택적 `ratePlanId`를 받아서 명시하지 않으면 기본 요금제를 쓴다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `created=false`로 같은 요금제를 돌려주고, 응답 유실 뒤 새 키로 같은 내용을 보내도 SHA-256 지문이 같아 같은 결과를 돌려준다. **멱원 재호출은 이름 중복 검사 앞에서 검사한다.**
- V62 additive 마이그레이션이 `rate_plan_command` 표를 만들고 `rate_plan.created_at`을 추가한다. `rp.id`는 UUID라 삽입 순서를 알 수 없으므로 기본 요금제를 고를 때 `created_at`을 기준으로 삼는다. **기존 행은 마이그레이션이 `now()`로 채우므로 기존 동작을 바꾸지 않는다.**
- 관리자 `호텔 및 객실` (`/dashboard/hotels`)의 객실 유형 표에 **요금제 열**을 추가하고, 유형 표 아래에 각 유형의 요금제를 나열했다. `요금제 추가`·`이름 변경` 대화상자는 기본 요금제의 조건을 미리 채운다. **생성·수정은 매번 새 멱원 키를 쓴다.** 서버 검증 실패 시 대화상자를 닫지 않고 이유를 보여준다.
- API `mvnw -Dtest=RatePlanCommandIntegrationTest` **35건**이 종료 코드 0이고, 인접 8개 suite **175건**도 종료 코드 0이다. 관리자 `tsc --noEmit`·`eslint`(경고 3건, 기존 패턴)·Playwright `hotel-catalog` **29건**(기존 24 + 신규 5)이 종료 코드 0이다. 라이브 DB·compose 재빌드·브라우저 확인은 실행하지 않았다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-24-hq-rate-plan.md)을 따른다.
- **1번 `지점·객실 유형 관리` 영역이 완료됐다.** 다음은 2번 `재고·가격`의 판매 중지 구간 설정·재고 일괄 업로드, 3번 `직원 계정 관리`의 삭제·본인 계정 수정이다.

## 본사 지점 관리 - 객실 유형 삭제 (2026-09-23)

- `admin-menu-roadmap.md` 1번 `지점·객실 유형 관리`의 남은 항목 중 "객실 유형 삭제"를 구현했다. 본사가 유형을 만들고 수정할 수 있었지만 **지울 수단이 없어서** 잘못 만든 유형이나 더 이상 팔지 않는 유형이 카탈로그·재고·보고서 화면에 계속 나타났다.
- `DELETE /api/staff/hotels/{hotelId}/room-types/{roomTypeId}`가 유형을 지운다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401, 멱원 키 누락은 400, 없는 유형·다른 지점의 유형은 404다.
- **진행 중인 판매·예약·배정이 있으면 409 `ROOM_TYPE_DELETION_CONFLICT`로 거부하고 아무것도 지우지 않는다.** 확정 예약·진행 중인 예약 변경 요청·보류 재고·배정된 실제 객실 4가지를 모두 센다. **에러 메시지가 어느 조건이 막았는지 알려준다.** 인원·조식·재고 `PATCH`가 취한 것과 같은 기준이다.
- **종료된 예약·요금제는 `room_type_id` 참조만 끊고 보존한다.** `reservation.rate_plan_id`·`reservation.room_type_id`가 NOT NULL이라 행을 지울 수 없다. **남은 요금제·예약은 고객 검색에 나타나지 않는다.** `AvailabilityService`·`InventoryQueryService`·`HotelCatalogQueryService`가 모두 `room_type`에서 시작해 join하므로 유형이 없으면 함께 빠진다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `deleted=false`로 같은 결과를 돌려주고, 응답 유실 뒤 새 키로 같은 내용을 보내도 SHA-256 지문이 같아 같은 결과를 돌려준다. **멱원 재호출은 유형 조회 앞에서 검사한다.** 유형이 이미 지워졌으면 404로 착각하게 된다.
- V61 additive 마이그레이션이 `room_type_command.deleted_room_type_name`을 추가하고 `room_type_command`·`rate_plan`·`reservation`의 `room_type_id` NOT NULL을 해제한다. **기존 행은 모두 값을 가지고 있어 빈 값을 만들지 않는다.**
- 관리자 `호텔 및 객실` (`/dashboard/hotels`)의 객실 유형 표에 `삭제` 열과 확인 대화상자를 추가했다. 삭제는 매번 새 멱원 키를 쓴다. 서버가 409를 내면 대화상자를 닫지 않고 이유를 보여준다.
- API `mvnw -Dtest=RoomTypeDeletionIntegrationTest` **17건**이 종료 코드 0이고, 인접 9개 suite **185건**·예약·가용성·카탈로그 suite **88건**도 종료 코드 0이다. 관리자 `tsc --noEmit`·`eslint`(경고 3건, 기존 패턴)·Playwright `hotel-catalog` **24건**(기존 22 + 신규 2)·인접 5개 suite 61건이 종료 코드 0이다. 라이브 DB·compose 재빌드·브라우저 확인은 실행하지 않았다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-23-hq-room-type-delete.md)을 따른다.

## 본사 지점 관리 - 지점 판매 중지·재개 (2026-09-22)

- `admin-menu-roadmap.md` 1번 `지점·객실 유형 관리`의 남은 항목 중 "지점 판매 중지"를 구현했다. **본사가 리모델 공사나 브랜드 정비로 한 지점의 예약 접수를 멈출 수단이 없었다.** `hotel` 표에 판매 상태 칼럼이 없었으므로 점검 기간인 지점도 고객 `GET /api/hotels`·`GET /api/availability`에 계속 나타났다. 재고를 0으로 내리는 임시 방편은 가능하지만 **재고 0은 "매진"이지 "판매하지 않는다"가 아니므로** 본사가 의도를 가지고 중지·재개할 수 있는 상태가 필요했다.
- `PATCH /api/staff/hotels/{hotelId}/active`가 지점의 판매를 중지·재개한다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401, 멱원 키 누락은 400, `active` 필드 누락은 400, 없는 지점은 404다.
- **중지는 고객 `GET /api/hotels`·`GET /api/availability`에서 지점을 빼는 것이다.** `HotelController.list()`가 `where active`로 걸러주고 `AvailabilityService`가 `active`가 아닌 지점에 빈 오퍼를 내려준다. 예약할 수 없다는 것이지 오류가 아니다.
- **이미 확정된 예약은 그대로 둔다.** 중지가 예약을 취소하면 본사가 고객에게 알리지 않은 채 환불 의무가 생기므로, 중지는 신규 판매에만 적용한다. 취소는 전용 API가 있다. **직원은 중지한 지점도 본다.** 다시 판매하려면 상태를 알아야 하므로 고객 화면과 다르다.
- **중지한 지점의 객실 유형에 본사가 여전히 요금·재고를 바꿀 수 있다.** 본사는 점검 기간에도 가격·재고를 정비해야 하므로, 중지가 쓰기 권한에 영향을 주지 않는다. **판매 재개는 재고를 다시 심지 않는다.** 중지는 `inventory_day`·`rate_day`를 건드리지 않으므로 재개하면 중지 전과 같은 재고가 돌아온다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `changed=false`로 같은 결과를 돌려주고, 응답 유실 뒤 새 키로 같은 내용을 보내도 `staff_id`·지점·`active`의 SHA-256 지문이 같아 같은 결과를 돌려준다. **이미 같은 상태면 DB를 건드리지 않고 멱원 기록만 남긴다.** 지점 행을 `select ... for update`로 잡아 동시 전환을 직렬화한다.
- V60 additive 마이그레이션이 `hotel.active BOOLEAN NOT NULL DEFAULT TRUE`를 추가한다. **기존 3개 지점은 모두 활성이므로 기존 동작을 변경하지 않는다.** `hotel_command` 표를 재사용해 `kind='ACTIVATE'`를 썼다. V59·V60 모두 아직 커밋되지 않은 상태다.
- 관리자 `호텔 및 객실` (`/dashboard/hotels`)의 지점 표에 `판매` 열과 `판매 중지`·`판매 재개` 버튼을 추가했다. **중지·재개는 매번 새 멱원 키를 쓴다.** 재시도가 같은 키를 재사용하지 않게 해서 네트워크 장애 뒤 다시 눌러도 중복 전환이 생기지 않는다. 실패 안내는 표 위에 남기고 상태는 바꾸지 않는다.
- API `mvnw -Dtest=HotelActivationIntegrationTest` **14건**이 종료 코드 0이고, 인접 9개 suite **193건**도 종료 코드 0다. 관리자 `tsc --noEmit`·`eslint`(경고 3건, 기존 패턴)·Playwright `hotel-catalog` **22건**(기존 20 + 신규 2)·인접 6개 suite 68건이 종료 코드 0이다. 라이브 DB·compose 재빌드·브라우저 확인은 실행하지 않았다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-22-hq-hotel-activation.md)을 따른다.

## 본사 지점 관리 - 지점 수정 (2026-09-22)

- `admin-menu-roadmap.md` 1번 `지점·객실 유형 관리`의 남은 항목 중 "지점 수정"을 구현했다. 본사가 지점 이름을 잘못 만들거나 브랜드가 바뀌었을 때 고칠 수단이 없었다. 생성 API가 이름 중복을 409로 막고 있어서 **수정 수단이 없으면 한 번 잘못 만든 지점 이름을 바로잡을 방법이 아예 없었다.**
- `PATCH /api/staff/hotels/{hotelId}`가 지점의 이름·지역·시간대를 바꾼다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401, 멱원 키 누락은 400, 없는 지점은 404다. **세 필드 모두 선택**이고 보내지 않은 필드는 현재 값을 유지한다. 빈 본문은 400이라 빈 PATCH가 지점을 초기화하지 않는다.
- **이름을 바꿀 때만 다른 지점과의 중복을 검사한다.** 자기 자신의 현재 이름은 제외하고, 대소문자 구분 없이 중복이면 409 `HOTEL_NAME_CONFLICT`다. **시간대는 `ZoneId.of`로 검증**하고 실패하면 400이다. 시간대는 재고 시드 시작일·취소 마감 시각·운영 상태 전환의 기준이다.
- **이미 지나간 날짜는 다시 계산하지 않는다.** `inventory_day`·`rate_day`·`reservation`이 날짜를 `LocalDate`로 저장하므로 시간대를 바꿔도 행이 이동하지 않는다. 새 시간대는 그 이후의 판정부터 적용된다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `changed=false`로 같은 결과를 돌려주고, 응답 유실 뒤 새 키로 같은 내용을 보내도 SHA-256 지문이 같아 같은 결과를 돌려준다. **실제로 바뀌는 값이 없으면 DB를 건드리지 않고 멱원 기록만 남긴다.**
- V59가 이전 작업과 함께 아직 커밋되지 않은 상태이므로 제자리에서 수정했다. `kind`(`CREATE`·`UPDATE`) 칼럼과 이력용 `previous_*` 칼럼을 추가하고 유일 인덱스에 `kind`를 포함했다. **테스트 DB에서만 체크섬 불일치가 났고** `flyway_schema_history`의 version 59 행과 `hotel_command` 표를 지워서 다시 적용했다. 라이브 DB에는 영향이 없다.
- 관리자 `호텔 및 객실` (`/dashboard/hotels`)의 지점을 선택기 버튼에서 **표**로 바꿨다. 이름·지역·시간대와 수정 버튼을 보여준다. 수정 대화상자는 현재값으로 미리 채우고 서버 검증 실패 시 닫지 않는다.
- API `mvnw -Dtest=HotelUpdateIntegrationTest` **14건**이 종료 코드 0이고, 인접 8개 suite **193건**도 종료 코드 0이다. 관리자 `tsc --noEmit`·`eslint`(경고 3건, 기존 패턴)·Playwright `hotel-catalog` **20건**(기존 17 + 신규 3)·인접 6개 suite 68건이 종료 코드 0이다. 라이브 DB·compose 재빌드·브라우저 확인은 실행하지 않았다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-22-hq-hotel-update.md)을 따른다.

## 본사 지점 관리 - 지점 생성·지점 목록 조회 (2026-09-22)

- `admin-menu-roadmap.md` 1번 `지점·객실 유형 관리`의 남은 항목 중 "지점 생성"을 구현했다. 본사가 지점을 만들 수단이 전혀 없었다. 지점 행은 마이그레이션과 개발용 시드만 만들었다.
- 부수 결함: **관리자 화면의 지점 선택기가 3개 UUID를 하드코딩**하고 있었다. 서버에 지점이 생기거나 사라져도 화면이 따라가지 못했고, 환경마다 UUID가 다르면 선택기가 빈 상태로 남아 카탈로그·재고·보고서 화면이 동작하지 않았다.
- `POST /api/staff/hotels`가 새 지점을 만든다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401, 멱원 키 누락은 400이다. **지점 행만 만들고 객실 유형·요금·재고를 심지 않는다.** 객실 유형 추가가 이미 기본 요금제와 90일분 일자 요금·재고를 같은 트랜잭션에 심으므로 시드는 그 동작에 맡긴다.
- **이름은 대소문자 구분 없이 중복을 거부한다.** 409 `HOTEL_NAME_CONFLICT`다. 지점 이름은 지점을 식별하는 라벨이므로 중복을 허용하지 않는다. 삭제·이름 변경 수단이 아직 없으므로 의도하지 않은 중복 생성이 들어가면 두 지점을 구분할 방법이 없다.
- **시간대를 서버가 검증한다.** `ZoneId.of`로 파싱해 보고 실패하면 400이다. 시간대는 재고 시드 시작일(지점 현지 날짜)과 취소 마감 시각의 기준이므로 잘못된 값을 저장하면 나중에 고칠 수 없다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `created=false`로 같은 지점을 돌려주고, 응답 유실 뒤 새 키로 같은 내용을 보내도 `staff_id`·이름·지역·시간대의 SHA-256 지문이 같아 같은 결과를 돌려준다.
- `GET /api/staff/hotels`가 지점 목록을 돌려준다. SELECT만 사용한다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401이다. V59 additive 마이그레이션이 `hotel_command` 표를 만든다. 기존 표를 변경하지 않는다.
- 관리자 `호텔 및 객실` (`/dashboard/hotels`)의 **지점 선택기를 하드코딩에서 서버 조회로 바꿨다.** 지점이 추가되면 선택기에 바로 나타난다. `지점 추가` 버튼과 대화상자를 추가했고, 빈 지점을 만들면 "객실 유형이 없으므로 고객 검색에 나타나지 않습니다"라고 안내한다. 서버 검증 실패 시 대화상자를 닫지 않는다.
- API `mvnw -Dtest=HotelCreateIntegrationTest` **13건**이 종료 코드 0이고, 인접 영역 객실 유형 27건·재고 16건·요금 20건·고객 요청 22건·감사 16건·운영 통계 18건·직원 계정 47건도 종료 코드 0이다. 관리자 `tsc --noEmit`·`eslint`(경고만, 기존 패턴)·Playwright `hotel-catalog` **17건**(기존 13 + 신규 4)이 종료 코드 0이다. 라이브 DB·compose 재빌드·브라우저 확인은 실행하지 않았다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-22-hq-hotel-create.md)을 따른다.

## 본사·지점 고객 요청 관리 - 접수·조회·상태 변경 (2026-09-22)

- `admin-menu-roadmap.md` 6번 `고객 요청 관리`의 1차를 구현했다. 고객이 호텔에 보낼 수 있는 채널이 아예 없어서 예약 변경·취소가 아닌 모든 문의가 전화로만 들어왔다.
- `POST /api/hotels/{hotelId}/guest-requests`가 고객의 요청을 받는다. **예약 변경·취소는 전용 API가 있으므로 받지 않는다.** 객실 요청·편의 요청·환불 문의·일반 문의·기타 5종만 허용하고 나머지는 400이다. 멱원 키가 필수이고, **재호출은 200에 `created=false`로 같은 요청을 돌려준다.** 가격·재고·예약 상태를 바꾸지 않는다.
- `GET /api/staff/guest-requests`가 요청 목록을 돌려준다. **본사는 지점 필터가 없으면 전 지점을 읽고**, 지점 직원은 무조건 자기 지점만 읽는다. 지점 직원이 다른 지점을 명시적으로 요청하면 403이다. `status` 필터, `limit` 1~100, `offset` 0 이상이고 위반은 400이다. SELECT만 사용한다.
- `GET /api/staff/guest-requests/{requestId}`가 연락처·내용·처리 이력을 돌려주고 `POST /api/staff/guest-requests/{requestId}/transition`이 처리 상태를 `OPEN`→`IN_PROGRESS`→`RESOLVED`→`CLOSED`로 바꾼다. 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200으로 같은 결과를 돌려주고, 이미 같은 상태면 값을 바꾸지 않고 멱원 기록만 남겨서 재시도가 같은 결과를 돌려주게 한다.
- V58 additive 마이그레이션이 `guest_request`·`guest_request_event` 표와 `guest_request_detail` 뷰를 만든다. 기존 표를 변경하지 않는다.
- 관리자 `/dashboard/guest-requests`에 상태 필터·요청 표·검토 대화상자·상태 변경 대화상자를 추가했다. `nav.ts`의 `운영` 그룹에 `고객 요청`을 넣었고 `HQ_ADMIN`·`BRANCH_STAFF`만 본다. 본사·지점 직원이 처리하므로 7번 감사 메뉴와 달리 본사 전용이 아니다. 서버 검증 실패 시 대화상자를 닫지 않고 안내를 보여준다.
- 고객 웹에 `/contact`·`/en/contact` 요청 접수 폼을 추가했다. 고객 웹은 세션 기반이 아니므로 접수는 인증 없이 받고, 직원만 조회·처리할 수 있다.
- API `mvnw -Dtest=GuestRequestIntegrationTest` **22건**이 종료 코드 0이고, 인접 영역 감사 16건·운영 통계 18건·직원 계정 47건도 종료 코드 0이다. 관리자 `tsc --noEmit`·`eslint`(경고만, 기존 패턴)·Playwright `guest-requests` **7건**·인접 suite 49건이 종료 코드 0이다. 고객 웹 `tsc --noEmit`과 `tsx` 검증 스크립트 25건이 종료 코드 0이다. 라이브 DB·compose 재빌드·브라우저 확인은 실행하지 않았다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-22-guest-requests.md)을 따른다.
- **후속 항목**: 담당자 지정 UI(`transition` 본문은 `assignTo`를 받지만 화면이 안 쓴다), 요청 유형별 알림, 고객 요청 이력의 감사 메뉴(7번) 통합, `priority` 노출.

## 본사 감사 이력 - 개인정보 마스킹·CSV 내보내기 (2026-09-22)

- `admin-menu-roadmap.md` 7번 `감사·이력 조회`의 남은 항목 중 "개인정보 마스킹 옵션"과 "CSV 내보내기"를 구현했다. 감사 이력은 처리 추적을 위해 고객 이름·이메일을 보여주는데, 본사가 화면을 공유하거나 캡처하면 개인정보가 그대로 퍼졌다.
- `GET /api/staff/audit?masked=true`가 고객 이름·이메일을 가린다. 응답의 `masked` 필드로 상태를 알려주고 기본값은 `false`다. 식별자(예약 id·객실 번호·금액·처리 직원)는 그대로 둬서 추적성을 유지한다. `GUEST_UPDATE`의 `summary` 안에도 이름·이메일이 중복으로 들어 있어 같이 가린다.
- SELECT만 사용하고 마이그레이션이 필요 없다. `HQ_ADMIN`만 호출 가능하고 지점 직원은 403, 잘못된 세션은 401이다.
- 관리자 `/dashboard/audit`에 `개인정보 마스킹` 토글과 `CSV 내보내기` 버튼을 추가했다. CSV는 브라우저에서 파일로 내려주고 UTF-8 BOM을 붙여 엑셀이 한글을 깨지지 않게 읽는다.
- API `mvnw -Dtest=AuditIntegrationTest` **16건**(기존 12 + 신규 4), 관리자 `tsc --noEmit`·`eslint`(경고만, 기존 패턴)·Playwright `audit` **9건**(기존 7 + 신규 2)이 종료 코드 0이다. 라이브 DB·compose 재빌드·브라우저 확인은 실행하지 않았다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-22-hq-audit-masking-csv.md)을 따른다.

## 본사 운영 통계 - 객실 유형별 매출·CSV 내보내기 (2026-09-22)

- `admin-menu-roadmap.md` 4번 `운영 통계·리포트`의 남은 항목 중 "객실 유형별 상세 매출"과 "CSV 내보내기"를 구현했다. 지점별 합계만 있어서 어느 객실 유형이 매출을 만드는지 알 수 없었고, 보고서를 공유하려면 화면을 캡처해야 했다.
- `GET /api/staff/reports/operations/room-types?hotelId=...`가 한 지점의 객실 유형별 예약·취소·노쇼·매출·매출 비중을 반환한다. SELECT만 사용한다. `hotelId`는 필수이고 빠지면 400, 없는 지점은 404, 역순 기간·93일 초과는 400, 기간 생략이면 최근 7일이다.
- 매출 집계는 지점별 보고서와 같은 기준으로 취소·노쇼가 아닌 예약의 금액만 인정한다. 지점 매출이 0원이면 비중을 계산하지 않고 0이다.
- 관리자 `/dashboard/reports`에 객실 유형별 대상 지점 선택기와 객실 유형별 매출 표를 추가했다. `CSV 내보내기`는 브라우저에서 파일로 내려주고 UTF-8 BOM을 붙여 엑셀이 한글을 깨지지 않게 읽는다.
- API `mvnw -Dtest=OperationsReportIntegrationTest` **18건**(기존 10 + 신규 8), 관리자 `tsc --noEmit`·`eslint`(경고만, 기존 패턴)·Playwright `operations-report` **8건**(기존 6 + 신규 2)이 종료 코드 0이다. 라이브 DB·compose 재빌드·브라우저 확인은 실행하지 않았다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-22-hq-reports-room-type-revenue.md)을 따른다.

## 본사 공통 정책 - 예약 변경 승인 TTL 런타임 변경 (2026-09-22)

- `admin-menu-roadmap.md` 5번 `공통 정책 관리`에서 남은 "승인 TTL 런타임 변경"을 구현했다. 지점이 본사 승인이 필요한 예약 변경을 요청하면 `approval_expires_at`에 승인 만료 시각이 저장되는데, 이 시간을 결정하는 `reservation.change.approval-ttl`이 `application.yml`에 고정돼 재배포하지 않으면 바꿀 수 없었다.
- `PUT /api/staff/policies/change-approval-ttl`이 승인 TTL을 바꾼다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401, 멱원 키 누락은 400이다. 60초 이상 7일(604,800초) 이하이고 위반은 400이다.
- **진행 중인 변경 요청은 영향을 받지 않는다.** `approval_expires_at`은 요청 생성 시점에 한 번 저장되므로 TTL을 바꿔도 이미 만들어진 요청의 만료 시각은 그대로다. 신규 요청부터 새 TTL이 적용된다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `created=false`로 같은 revision을 돌려주고, 응답 유실 뒤 새 키로 같은 내용을 보내도 `staff_id`·정책 키·`value_seconds`의 SHA-256 지문이 같아 같은 결과를 돌려준다.
- V57 additive 마이그레이션이 `policy_revision.value_seconds`를 추가한다. 기존 행은 모두 NULL이므로 기존 동작을 변경하지 않는다.
- 관리자 `/dashboard/policies`의 예약 변경 승인 카드에 `본사 승인 대기 시간` 행과 `TTL 변경` 대화상자를 추가했다. 본사는 시간 단위로 입력하고 저장은 초 단위다. 서버 검증 실패 시 대화상자를 닫지 않고 안내를 보여준다.
- API `mvnw -Dtest=PolicyIntegrationTest` **30건**(기존 22 + 신규 8), 인접 `ReservationChangeApprovalIntegrationTest` 3건, 관리자 `tsc --noEmit`·`eslint`(경고만, 기존 패턴)·Playwright `policies` **13건**(기존 11 + 신규 2)이 종료 코드 0이다. 라이브 DB·compose 재빌드·브라우저 확인은 실행하지 않았다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-22-hq-policy-approval-ttl.md)을 따른다.

## 본사 직원 계정 - 비활성·재활성 (2026-09-22)

- `admin-menu-roadmap.md` 3번 `직원 계정 관리`에서 남은 "비활성"을 구현했다. 퇴사하거나 권한을 회수해야 하는 직원의 계정을 끌 수단이 없었다. 삭제하면 감사 이력이 참조하는 직원 행이 사라지므로 비활성을 먼저 구현했다.
- `PATCH /api/staff/staff/{staffId}/active`가 활성 상태를 바꾼다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401, 없는 직원은 404, 멱원 키 누락은 400이다.
- **비활성은 즉시 효력이 있다.** 비밀번호가 맞아도 로그인을 403으로 거부하고, 세션 조회 SQL에 `AND m.active`를 붙여 남겨둔 세션도 401로 만료시키며, 비활성과 동시에 `delete from staff_session`으로 세션을 지운다.
- **본인 계정은 본인이 비활성할 수 없다.** 409 `STAFF_SELF_MODIFICATION_FORBIDDEN`로 거부한다. 역할 수정과 같은 예외를 재사용한다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `created=false`로 같은 결과를 돌려주고, 응답 유실 뒤 새 키로 같은 내용을 보내도 `staff_id`·`modified_by`·`active`의 SHA-256 지문이 같아 같은 결과를 돌려준다. 이미 같은 상태면 값을 바꾸지 않고 멱원 기록만 남겨서 재시도가 200으로 같은 결과를 돌려주게 했다.
- V56 additive 마이그레이션이 `staff_member.active BOOLEAN NOT NULL DEFAULT TRUE`를 추가한다. 기존 행은 모두 활성이므로 기존 동작을 변경하지 않는다.
- 관리자 `/dashboard/staff`의 직원 표에 `활성` 열과 `비활성`·`재활성` 버튼을 추가했다. 비활성·재활성은 매번 새 멱원 키를 쓴다. 서버 검증 실패 시 표 위에 안내를 보여주고 상태를 바꾸지 않는다.
- API `mvnw -Dtest=StaffAccountIntegrationTest` **47건**(기존 35 + 신규 12), 인접 영역 객실 유형 27건·요금 20건·재고 16건, 관리자 `tsc --noEmit`·`eslint`(경고만, 기존 패턴)·Playwright `staff-accounts` **17건**·`inventory-viewer` 14건·`hotel-catalog` 13건이 종료 코드 0이다. 라이브 DB·compose 재빌드·브라우저 확인은 실행하지 않았다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-22-hq-staff-deactivate.md)을 따른다.

## 본사 재고·가격 - 객실 유형별 일자 요금 개별 변경 (2026-09-20)

- `admin-menu-roadmap.md` 1번·2번 메뉴에 공통으로 남은 "일자별 요금 개별 변경"을 구현했다. 기본 요금을 바꿀 수는 있었지만 **전체 일자를 같은 폭으로 옮기는 방식뿐**이어서 주말·계절 차등을 직접 만들거나 특정 날짜의 가격만 바꿀 수단이 없었다.
- `GET /api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates`가 일자별 금액을 돌려주고(`from`·`to` 필터, 최대 92일), `PATCH .../rates`가 날짜별 금액을 덮어쓴다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401, 없는 지점·다른 지점의 유형·없는 유형은 404다.
- **새 가격을 만들지 않는다.** `rate_day` 행이 없는 날짜는 404 `RATE_DAY_NOT_FOUND`, 요금제가 없는 유형은 404 `ROOM_TYPE_RATE_PLAN_NOT_FOUND`다. 금액은 0원 이상 10,000,000원 이하, 한 요청 92일 위반은 400이다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `created=false`로 같은 결과를 돌려주고, 응답 유실 뒤 새 키로 같은 내용을 보내도 `staff_id`·지점·유형·요금제·일자·금액의 SHA-256 지문이 같아 같은 결과를 돌려준다. 재호출은 요청했던 일자만 돌려준다. 이미 확정된 예약의 금액은 건드리지 않는다.
- 관리자 `/dashboard/inventory`의 그리드에 `요금 조정` 열과 대화상자를 추가했다. 대화상자를 열면 서버에서 현재 일자별 요금을 읽어 칸별로 미리 채우고, **바뀐 날짜만 서버에 보낸다.** 서버 검증 실패 시 대화상자를 닫지 않고 안내를 보여준다.
- API 전체 suite **556건**(실패 0, 건너뜀 3), 관리자 `tsc --noEmit`·`eslint`(경고 3건, 기존 패턴)·Playwright `inventory-viewer` **14건**·인접 suite 28건이 종료 코드 0이다. 라이브 DB·compose 재빌드·브라우저 확인은 실행하지 않았다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-20-hq-rate-day-adjust.md)을 따른다.

## 본사 직원 계정 - 역할·소속 지점 수정 (2026-09-20)

- `admin-menu-roadmap.md` 3번 `직원 계정 관리`에서 남은 다음 항목인 "역할 변경·지점 재할당"을 구현했다. 본사가 직원의 역할을 바꾸거나 지점을 옮길 수단이 전혀 없었다.
- `PATCH /api/staff/staff/{staffId}`가 역할과 소속 지점을 바꾼다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401, 없는 직원은 404, 없는 지점은 404다.
- **역할과 지점의 짝은 서버가 최종 판단한다.** 본사 역할은 지점을 가질 수 없고, `BRANCH_STAFF`는 지점이 필수다. 위반은 400이고 값을 바꾸지 않는다. 역할을 본사 역할로 올리면 지점을 비우고, 지점 직원으로 내리면 요청한 지점을 채운다.
- **본인 계정의 변경은 409 `STAFF_SELF_MODIFICATION_FORBIDDEN`로 거부한다.** 본사 관리자가 실수로 본인 권한을 내려 본사 메뉴에 다시 들어오지 못하는 것을 막는다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `created=false`로 같은 결과를 돌려주고, 응답 유실 뒤 새 키로 같은 내용을 보내도 `staff_id`·`modified_by`·역할·지점의 SHA-256 지문이 같아 같은 결과를 돌려준다. 세션은 `token_hash` 기반이라 역할을 바꿔도 기존 세션이 즉시 만료되지 않는다.
- 관리자 `/dashboard/staff`의 직원 표에 `역할·지점` 열과 수정 버튼·대화상자를 추가했다. 본사만 버튼이 보이고, 대화상자는 현재 역할·소속 지점으로 미리 채운다. 서버 검증 실패 시 대화상자를 닫지 않고 안내를 보여준다.
- API `mvnw -Dtest=StaffAccountIntegrationTest` **35건**(기존 22 + 신규 13), 관리자 `tsc --noEmit`·`eslint`(경고 2건 `react-hooks/set-state-in-effect`, 기존 패턴)·Playwright `staff-accounts` **13건**이 종료 코드 0이다. 라이브 DB·compose 재빌드·브라우저 확인은 실행하지 않았다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-20-hq-staff-role-hotel-update.md)을 따른다.

## 본사 재고·가격 - 객실 유형별 일자 총량 조정 (2026-09-20)

- `admin-menu-roadmap.md` 2번 `재고·가격`의 첫 쓰기 동작을 구현했다. 본사가 일자 재고를 볼 수만 있었고, 객실 유형을 추가해 심은 총량을 바꿀 수단이 없었다.
- `PATCH /api/staff/hotels/{hotelId}/inventory`가 객실 유형의 일자 총량을 바꾼다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401, 없는 지점·다른 지점의 유형·없는 유형은 404다.
- **총량을 내릴 때는 서버가 강하게 검증한다.** 해당 일자의 확정·보류 건수 아래로 내리면 409 `INVENTORY_CAPACITY_CONFLICT`로 거부하고 재고를 바꾸지 않는다. 재고는 음수가 될 수 없기 때문이다. 총량 0은 판매 중지와 같고, 확정·보류가 없는 일자만 허용한다.
- 시드되지 않은 일자는 재고 행이 없어서 404 `INVENTORY_DAY_NOT_FOUND`다. 총량 범위 0~1,000, 한 요청 92일 위반은 400이다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `created=false`로 같은 결과를 돌려주고, 응답 유실 뒤 새 키로 같은 내용을 보내도 `staff_id`·지점·유형·일자·총량의 SHA-256 지문이 같아 같은 결과를 돌려준다. 재호출은 요청했던 일자만 돌려줘서 다른 날짜의 재고가 바뀐 뒤에도 응답이 달라지지 않는다.
- 관리자 `/dashboard/inventory`의 그리드에 `총량 조정` 열과 대화상자를 추가했다. 총량 입력은 `type="text"` + `inputMode="numeric"`을 썼다. 브라우저가 `max` 초과로 폼 제출을 막으면 서버 검증 응답이 도달하지 않으므로(공통 정책·객실 유형 화면에서 이미 같은 문제가 있었다) 범위 판단은 서버에 뒀다. 서버 검증 실패 시 대화상자를 닫지 않고 안내를 보여준다.
- API **전체 522건**(실패 1, 건너뜀 3)이다. 실패 1건은 `TossSettlementIntegrationTest.resumes_failed_page_without_deleting_previous_snapshot`이고, 작업 트리를 비우고 HEAD에서 따로 실행하면 통과하므로 이 변경과 무관한 비결정적 실패다. 재고 쓰기 16건은 종료 코드 0이다. 관리자 `tsc --noEmit`·`eslint`(경고 2건 `react-hooks/set-state-in-effect`, 기존 패턴)·Playwright `inventory-viewer` 10건·`hotel-catalog` 13건이 종료 코드 0이다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-20-hq-inventory-adjust.md)을 따른다.

## 관리자 호텔 및 객실 - 한글 깨짐 원인과 조식·기본 요금 입력 (2026-09-20)

- 사용자가 "관리자 페이지 호텔 및 객실 페이지에서 한글이 깨졌다"고 보고했다. **소스 파일에는 깨진 글자가 없었다.** 터미널(Windows PowerShell) 코드 페이지가 한글을 표시하지 못해서 콘솔에서만 글자가 깨져 보였다. `.tmp/hangul-check.cjs`로 UTF-8 코드포인트를 직접 점검해 파일은 0건 수정했다.
- 같은 화면의 실제 결함을 함께 고쳤다. **조식 포함 여부와 기본 요금 입력이 아예 없었다.** 객실 유형을 추가하면 조식이 항상 `false`로 고정됐고, 본사가 수정 화면에서 현재 조식 여부·요금을 볼 수도 없었다.
- `POST /api/staff/hotels/{hotelId}/room-types`가 `breakfastIncluded`·`defaultRateKrw`를 받아 기본 요금제와 90일분 일자 요금·재고를 심는다. 값은 `room_type.seed_breakfast_included`·`seed_default_rate_krw`에 보관된다.
- `PATCH /api/staff/hotels/{hotelId}/room-types/{roomTypeId}`가 조식 포함 여부와 기본 요금을 바꾼다. 둘 다 null이면 변경하지 않는다. **조식 포함 여부는 확정 예약이 현재 조건으로 예약돼 있으면 409 `ROOM_TYPE_BREAKFAST_CONFLICT`로 거부**한다. 예약은 `rate_plan_id`만 가지므로 계약 내용이 달라지면 안 되기 때문이다. 기본 요금은 일자별 금액 전체를 같은 폭으로 옮겨서 주말·계절 차등을 보존한다. 요금제가 없는 유형은 404 `ROOM_TYPE_RATE_PLAN_NOT_FOUND`다.
- `GET /api/staff/hotels/{hotelId}/room-types/{roomTypeId}/defaults`가 수정 화면에 미리 채울 현재값을 돌려준다. SELECT만 사용한다. 카탈로그 본문의 객실 유형에도 `breakfastIncluded`·`defaultRateKrw`가 추가됐다.
- 요금 입력은 `type="text"` + `inputMode="numeric"`을 썼다. 브라우저가 `max` 초과로 폼 제출을 막으면 서버 검증 응답이 도달하지 않으므로(공통 정책 화면에서 이미 같은 문제가 있었다) 범위 판단은 서버에 뒀다.
- API **전체 506건**(건너뜀 3)이 종료 코드 0이고, 관리자 `tsc --noEmit`·`eslint`(경고 2건)·Playwright `hotel-catalog` 15건·전체 회귀 **182 passed**가 종료 코드 0이다. 라이브에서 본사 세션으로 생성 201 `breakfastIncluded=true defaultRateKrw=95000`·수정 200·같은 키 재호출 200 `created=false`·음수·초과 요금 400·`defaults` 200을 확인했고, 고객 `GET /api/availability` 응답에 새 유형이 `remaining=8 total=330000 breakfastIncluded=true`로 포함됐다. 검증용 유형은 삭제해 라이브를 원래 4종으로 복원했다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-20-hq-room-type-breakfast-rate.md)을 따른다.

## 본사 객실 유형 - 이름·최대 인원 수정 (2026-09-20)

- `admin-menu-roadmap.md` 1번 `지점·객실 유형 관리`에서 남은 다음 항목인 "객실 유형 수정·삭제" 중 수정을 구현했다. 본사가 객실 유형 이름이나 최대 인원을 바꿀 수단이 전혀 없었다.
- `PATCH /api/staff/hotels/{hotelId}/room-types/{roomTypeId}`가 이름·최대 인원을 바꾼다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401, 없는 지점·다른 지점의 유형·없는 유형은 404다.
- **최대 인원을 내릴 때는 서버가 강하게 검증한다.** 확정 예약 중 새 인원을 초과하는 예약이 있으면 409 `ROOM_TYPE_OCCUPANCY_CONFLICT`로 거부하고 값을 바꾸지 않는다. `max_occupancy`가 예약 가능 조건에 직결되기 때문이다. 올리는 것은 제한 없이 허용한다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `created=false`로 같은 결과를 돌려주고, 응답 유실 뒤 새 키로 같은 내용을 보내도 같은 결과를 돌려준다.
- 삭제는 이번 범위에서 뺐다. `reservation`·`reservation_change_request`·`website_page_room_type` 외래키가 걸려 있어 안전한 삭제 조건을 따로 설계해야 한다.
- API **전체 500건**(건너뜀 3)이 종료 코드 0이고, 관리자 `tsc --noEmit`·내 파일 `eslint`(경고 2건)·Playwright `hotel-catalog` 12건이 종료 코드 0이다. 라이브에서 본사 세션으로 수정 200·같은 키 재호출 200 `created=false`·새 키 같은 내용 200·확정 예약이 있는 유형의 인원을 1로 내리면 409를 확인했다. 검증용 수정 명령은 모두 삭제하고 바뀐 인원도 원래값으로 복원했다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-20-hq-room-type-update.md)을 따른다.

## 본사 객실 유형 - 기본 요금제·일자 재고 시드 (2026-09-20)

- `admin-menu-roadmap.md` 1번 `지점·객실 유형 관리`에서 남은 첫 항목인 "새 객실 유형의 초기 재고·요금 생성"을 구현했다.
- `RoomTypeCommandService.create`는 `room_type` 행만 만들었다. `rate_plan`·`rate_day`·`inventory_day`가 없으면 고객 `GET /api/availability`가 그 유형을 빼버려서, 본사가 추가한 객실 유형은 즉시 판매되지 않았다. 라이브 DB의 `스탠다드 트윈`이 이 상태(`plans=0`)였다.
- 이제 `POST /api/staff/hotels/{hotelId}/room-types`가 기본 요금제 1개와 90일분 일자 요금·재고를 객실 유형과 **같은 트랜잭션**에 심는다. 실패하면 유형 행도 롤백돼서 "유형은 있는데 가격·재고가 없어 예약 불가" 상태가 생기지 않는다.
- 시드 시작일은 지점 현지 시간대 기준 오늘이다. `Clock`이 UTC이고 지점이 `Asia/Seoul`이면 UTC 자정이 한국 시간 전일 09:00가 돼서 오늘 도착 검색이 빈 결과를 돌려받는다. `clock.withZone(hotelZone)`으로 보정했다.
- 멱원 재시도가 같은 일자를 두 번 만들지 않는다. `ON CONFLICT DO NOTHING`으로 막는다. 금액은 첫 생성 시점의 환경 변수(`hotel.seed.*`) 값을 따르고, 이후 환경 변수를 바꿔도 이미 만든 유형에 영향을 주지 않는다.
- API **전체 494건**(건너뜀 3)이 종료 코드 0이고, 관리자 `tsc --noEmit`·내 파일 `eslint`(경고 8건)·Playwright `hotel-catalog` 9건 + 인접 suite 33건이 종료 코드 0이다. 라이브에서 본사 세션으로 생성 201 `seed.pricedDays=90 defaultRateKrw=100000 inventoryCapacity=8`·같은 키 재호출 200 `created=false`를 확인했고, 고객 `GET /api/availability` 응답에 새 유형이 `remaining=8`·`total=300000`으로 포함됐다. 검증용 유형은 삭제해 라이브를 원래 4종으로 복원했다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-20-hq-room-type-seed.md)을 따른다.

## 본사 공통 정책 - 예약 변경 승인 한도 변경·이력 조회 (2026-09-20)

- `admin-menu-roadmap.md` 5번 `공통 정책 관리`의 둘째 단계를 구현했다. `ReservationChangePolicy.directLimitKrw()`가 `application.yml` 고정값을 쓰고 있어 본사가 한도를 바꿀 수단이 없었다.
- `PUT /api/staff/policies/change-limit`가 지점 직접 승인 한도를 변경한다. 0원 이상 10,000,000원 이하이고 위반은 400, 멱원 키 누락도 400이다. `HQ_ADMIN`만 호출 가능하고 지점 직원은 403, 잘못된 세션은 401이다.
- 멱원은 취소 정책과 같은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `created=false`로 같은 revision을 돌려주고, 응답 유실 뒤 새 키로 같은 내용을 보내도 같은 결과를 돌려준다.
- `GET /api/staff/policies/revisions`가 정책 변경 이력을 최신순으로 반환한다. SELECT만 사용하고 `limit` 1~100, `offset` 0 이상이며 위반은 400이다. 진행 중인 예약 변경 요청은 이미 저장된 `approval_limit_krw`를 그대로 쓴다.
- 관리자 `/dashboard/policies`의 예약 변경 승인 카드에 현재값·변경 대화상자·변경 이력 표·페이징을 추가했다.
- 한도 입력이 `type="number" required min max`였다. 브라우저가 `max` 초과로 폼 제출을 막아 서버 검증 응답이 도달하지 않았으므로 `type="text"` + `inputMode="numeric"`으로 바꾸고 정수 파싱만 클라이언트가 확인한다. 범위 판단은 서버에 뒀다.
- API `mvnw -Dtest=PolicyIntegrationTest,StaffAccountIntegrationTest` **43건**, 관리자 `tsc --noEmit`·`eslint`(경고 6건)·Playwright 21건 + 인접 suite 33건이 종료 코드 0이다. 라이브에서 본사 세션으로 변경 201·같은 키 재호출 200 `created=false`·새 키 같은 내용 200·10,000,001원 400·이력 200 `totalCount=1`을 확인했다. 검증용 revision 2건은 삭제해 기본값으로 복원했다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-20-hq-policy-change-limit.md)을 따른다.

## 본사 직원 계정 - 비밀번호 재발급 (2026-09-20)

- `admin-menu-roadmap.md` 3번 `직원 계정 관리`의 둘째 단계를 구현했다. 직원이 16자 임시 비밀번호를 분실하면 `StaffDevAccountInitializer`의 환경 변수 비밀번호에 의존해야만 했다.
- `POST /api/staff/staff/{staffId}/password`가 새 임시 비밀번호를 발급한다. `HQ_ADMIN`만 호출 가능하고 지점 직원은 403, 잘못된 세션은 401, 없는 직원은 404, 멱원 키 누락은 400이다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `created=false`를 돌려주고, 새 키로 쿨다운(기본 300초) 내 재발급을 시도하면 409 `STAFF_PASSWORD_RESET_COOLDOWN`로 거부한다. 비밀번호가 두 번 바뀌지 않는다.
- 임시 비밀번호 원문은 DB에 보관하지 않는다. BCrypt 해시만 저장하고 재호출은 `temporaryPassword: null`을 내려주므로 UI가 "이미 발급된 임시 비밀번호는 다시 볼 수 없다"고 안내한다. 세션은 `token_hash` 기반이라 재발급이 기존 세션을 끊지 않는다.
- 관리자 `/dashboard/staff`의 직원 표에 재발급 버튼과 결과 대화상자를 추가했고, 재발급마다 새 멱원 키를 발급한다.
- API `mvnw -Dtest=StaffAccountIntegrationTest,PolicyIntegrationTest` **43건**, 관리자 `tsc --noEmit`·`eslint`(경고 6건)·Playwright 21건 + 인접 suite 33건이 종료 코드 0이다. 라이브에서 재발급 201·같은 키 재호출 200 `created=false temporaryPassword=null`·쿨다운 내 새 키 409·발급된 임시 비밀번호로 로그인 201을 확인했다. 검증용 재발급 기록은 삭제했고, 바뀐 `sokcho@hotel-chain.local` 비밀번호는 `StaffDevAccountInitializer`의 `ON CONFLICT DO UPDATE`로 복원해 환경 변수 비밀번호 로그인 201을 확인했다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-20-hq-staff-password-reset.md)을 따른다.


## 하네스·스킬·플러그인 현황 (2026-09-20)

프로젝트 하네스와 Impeccable 로컬 구성, 사용자 전역 스킬, 플러그인 활성화·세션 제공·캐시 상태를 [현황 문서](../harness/installed-harness-skills-plugins.md)에 정리했다. `harness/`의 B2B-STM 이식 원문과 현재 설치·사용 근거를 구분한다. 이번 작업은 문서화이며 스킬·플러그인 실행이나 외부 연결 검증은 포함하지 않는다.

## 본사 운영 통계·리포트 조회 (2026-09-20)

- `admin-menu-roadmap.md` 4번 `운영 통계·리포트`의 첫 단계를 구현했다. `reservation`·`reservation_change_request`·`inventory_day`에 데이터가 쌓이고 있었으나 집계 쿼리와 본사 화면이 없었다.
- `GET /api/staff/reports/operations`가 지점별로 예약 건수·취소·노쇼·만료·매출·점유율과 예약 변경 승인 대기·완료 건수를 반환한다. 3개 집계 쿼리 모두 SELECT이므로 가격·재고·예약 상태를 변경하지 않는다.
- 매출은 실제 숙박으로 이어진 예약만 인정한다. 취소뿐 아니라 노쇼 예약의 금액도 합계에서 뺐다. `from`·`to`·`hotelId` 필터를 지원하고 기본값은 현지 시간대 기준 최근 7일이다. 기간은 최대 92일이고 위반은 400, 없는 지점은 404, 역순 기간은 400이다. `HQ_ADMIN`만 호출 가능하고 지점 직원은 403, 잘못된 세션은 401이다.
- 관리자 `/dashboard/reports`에 기간 프리셋(7일·14일·30일)·시작일·종료일, 전체 합계 카드 4종, 지점별 표를 추가했다. `nav.ts`의 `본사 관리` 그룹은 `HQ_ADMIN`에만 노출되고, 다른 역할은 본사 전용 안내를 본다.
- API `mvnw -Dtest=OperationsReportIntegrationTest` 10건, 관리자 `tsc --noEmit`·`eslint`(경고 2건)·Playwright 46건(운영 통계 6 + 인접 suite 40)이 종료 코드 0이다. 라이브에서 compose `api` 재빌드 뒤 본사 세션으로 `GET /api/staff/reports/operations` 200으로 속초 `reservations=21, revenueKrw=6,100,000, changeRequestsPending=2, changeRequestsCompleted=5, occupancyRate=16.1%`가 내려왔고, 역순 기간 400·112일 400·없는 지점 404·지점 직원 403·세션 없음 401을 확인했다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-20-hq-reports.md)을 따른다.

## 본사 감사 이력 통합 조회 (2026-09-20)

- `admin-menu-roadmap.md` 7번 `감사·이력 조회`의 첫 단계를 구현했다. V29~V37 감사 표와 `reservation_change_event`는 데이터가 쌓이고 있었으나 본사가 읽는 API와 화면이 없었다.
- `GET /api/staff/audit`가 8종 감사 이력(`GUEST_UPDATE`·`PARTY_UPDATE`·`ROOM_REASSIGNMENT`·`STAY_CHANGE`·`CANCELLATION`·`ROOM_OPERATIONAL_TRANSITION`·`CHECKED_IN_ROOM_MOVE`·`CHANGE_REQUEST_EVENT`)을 `UNION ALL`로 묶어 발생 시각 내림차순으로 반환한다. SELECT만 사용하고 상태를 변경하지 않는다.
- `reservationId`·`hotelId`·`from`·`to`·`limit`·`offset` 필터를 지원한다. `limit`은 1~200, `offset`은 0 이상, 기간 끝날짜는 시작 날짜 이후, 위반은 400이고 없는 지점은 404다. `HQ_ADMIN`만 호출 가능하고 지점 직원은 403, 잘못된 세션은 401이다.
- 직원 취소만 감사에 노출하고 고객 취소(`actor_type = 'CUSTOMER'`)는 제외한다. `reservation_change_event`는 `actor_staff_id`가 있는 전환만 노출한다. 각 이력에는 처리 직원 이메일·이름·역할, 지점 이름, 예약 id·고객 이름(있을 때), 객실 번호(있을 때), 이전·이후 요약이 포함되고 원본 `payload` JSON은 노출하지 않는다.
- 관리자 `/dashboard/audit`에 유형별 표와 더 보기 페이지 이동을 추가했다. `nav.ts`의 `본사 관리` 그룹은 `HQ_ADMIN`에만 노출되고, 다른 역할은 본사 전용 안내를 본다.
- API `mvnw -Dtest=AuditIntegrationTest` 13건, 관리자 `tsc --noEmit`·`eslint`(경고 2건)·Playwright 40건이 종료 코드 0이다. 라이브에서 본사 세션으로 `GET /api/staff/audit?limit=5` 200으로 예약 변경 요청 2건·직원 취소 1건이 내려왔고, 지점 직원 403·세션 없음 401·limit 0·201 400·offset -1 400·없는 지점 404·다른 지점 필터 0건을 확인했으며, 관리자 4001 브라우저에서 `감사 이력` 메뉴와 `예약 변경 요청`·`속초 오션 호텔` 표시를 확인했다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-20-hq-audit.md)을 따른다.

## 본사 공통 정책 조회·취소 정책 변경 (2026-09-20)

- `admin-menu-roadmap.md` 5번 `공통 정책 관리`의 첫 단계를 구현했다. 취소 정책과 예약 변경 승인 한도가 코드와 `application.yml`에 고정돼 있어 본사가 현재 값을 볼 수단이 없었다.
- `GET /api/staff/policies`가 취소 정책(마감 일수·마감 시각·시간대)·예약 변경 승인 한도·정산 활성 여부·revision 수를 반환한다. SELECT만 쓴다. `PUT /api/staff/policies/cancellation`이 취소 마감 일수(1~30)와 마감 시각(`HH:MM`)을 변경한다. `HQ_ADMIN`만 호출 가능하고 지점 직원은 403, 잘못된 세션은 401이다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `created=false`로 같은 revision을 돌려준다. 응답 유실 뒤 새 키로 같은 내용을 보내도 `staff_id`·정책 키·본문 SHA-256 지문이 같아 같은 결과를 돌려준다. 정책 키 단위 `pg_advisory_xact_lock`으로 동시 변경을 직렬화한다.
- V48 additive 마이그레이션으로 `policy_revision` 테이블을 만든다. 현재값은 항상 가장 최근 revision에서 읽고, 없으면 `application.yml`의 `policy.cancellation.*` 기본값을 쓴다. 기존 `reservation.policy_snapshot`을 변경하지 않는다.
- 변경은 항상 새 revision을 append 한다. 기존 revision을 덮어쓰지 않으므로 이미 확정된 예약의 취소 조건은 그대로 남고, `ReservationService`가 `CurrentPolicy`에서 읽은 값으로 신규 예약의 `policy_snapshot`을 만든다.
- 관리자 `/dashboard/policies`에 취소 정책 카드·예약 변경 승인 카드·변경 대화상자를 추가했다. `nav.ts`의 `본사 관리` 그룹은 `HQ_ADMIN`에만 노출되고, 다른 역할은 본사 전용 안내를 본다. 서버 검증 실패 시 대화상자를 닫지 않고 안내를 보여준다.
- API `mvnw` 178건(정책 11 + 예약·변경·직원·카탈로그·재고 167), 관리자 `tsc --noEmit`·`eslint`(경고 2건)·Playwright 33건이 종료 코드 0이다. 라이브에서 본사 세션으로 조회 200·변경 201·멱원 재호출 200 `created=false`·새 키 같은 내용 200·일수 31 400·시각 `25:00` 400·키 누락 400·지점 직원 403·세션 없음 401을 확인했고, 관리자 4001 브라우저에서 `체크인 1일 전` → 변경 → `체크인 3일 전` 전환과 새로고침 후 유지를 확인했다. 검증용 revision 2건은 모두 삭제해 기본값으로 복원했다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-20-hq-policies.md)을 따른다.

## 본사 직원 계정 조회·생성 (2026-09-19)

- `admin-menu-roadmap.md` 3번 `직원 계정 관리`의 첫 단계를 구현했다. `StaffDevAccountInitializer`가 환경 변수 비밀번호로 개발 계정을 초기화할 뿐, 애플리케이션에서 직원을 만들거나 조회할 수단이 없었다.
- `GET /api/staff/staff`가 전 직원 목록(이메일·이름·역할·소속 지점)을 반환하고 `POST /api/staff/staff`가 새 직원을 만든다. `HQ_ADMIN`만 호출 가능하고 지점 직원은 403, 잘못된 세션은 401이다.
- 이메일은 형식·254자, 이름은 1~100자, 역할은 4종 중 하나, `BRANCH_STAFF`는 지점 필수·본사 역할은 지점 없음이다. 위반은 400, 같은 이메일은 409, 없는 지점은 404다.
- 임시 비밀번호는 서버가 16자 무작지로 만들어 응답에 한 번만 내보내고, 저장은 BCrypt 해시만 한다. 멱원 키 재호출은 200에 `created=false`로 같은 직원을 돌려준다.
- V47 `staff_account_command` 테이블이 멱원 키를 보관한다. additive이고 기존 `staff_member`를 변경하지 않는다.
- 관리자 `/dashboard/staff`에 직원 표와 추가 대화상자를 추가했다. `nav.ts`의 `본사 관리` 그룹은 `HQ_ADMIN`에만 노출되고, 역할이 `BRANCH_STAFF`일 때만 소속 지점 선택이 나타난다.
- API `mvnw` 41건(직원 14 + 객실 유형 11 + 카탈로그 7 + 재고 9), 관리자 `tsc --noEmit`·`eslint`(경고 2건)·Playwright 23건이 종료 코드 0이다. 라이브에서 본사 세션으로 목록 200·생성 201·멱원 재호출 200 같은 ID·이메일 중복 409·형식·역할·지점 위반 400·없는 지점 404·지점 직원 403·세션 없음 401을 확인했고, 발급된 임시 비밀번호로 실제 로그인 201을 확인했다. 관리자 4001 브라우저에서 `직원 6명` → 추가 → `7명` 전환과 임시 비밀번호 표시를 확인했다. 검증용 직원과 멱원 기록·세션은 모두 삭제해 원래 6명으로 복원했다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-19-hq-staff-accounts.md)을 따른다.

## 본사 객실 유형 추가 (2026-09-19)

- `admin-menu-roadmap.md` 1번 `지점·객실 유형`의 첫 쓰기 동작을 구현했다. 읽기 전용 카탈로그만 있어서 본사가 객실 유형을 만들 수단이 없었다.
- `POST /api/staff/hotels/{hotelId}/room-types`가 이름·최대 인원을 받아 객실 유형을 만든다. `HQ_ADMIN`만 호출 가능하고 지점 직원은 403, 잘못된 세션은 401, 없는 지점은 404다. 이름은 1~100자, 최대 인원은 1~20이고 위반은 400이다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `created=false`로 같은 객실 유형을 돌려준다. 응답 유실 뒤 새 키로 같은 내용을 보내도 `staff_id`·`hotel_id`·본문 SHA-256 지문이 같아 같은 결과를 돌려준다. 지점 행을 `for update`로 잡아 동시 쓰기를 직렬화한다.
- V46 `room_type_command` 테이블이 멱원 키와 요청 지문을 보관한다. additive이고 기존 `room_type`·`staff_member`를 변경하지 않는다.
- 관리자 `/dashboard/hotels`에 객실 유형 추가 버튼과 대화상자를 추가했다. `HQ_ADMIN`만 버튼이 보이고, 성공하면 카탈로그를 새로고침한다. 서버 검증 실패 시 대화상자를 닫지 않고 안내를 보여준다.
- API `mvnw` 27건(객실 유형 쓰기 11 + 카탈로그 7 + 재고 9), 관리자 `tsc --noEmit`·`eslint`(경고 2건)·Playwright 8건이 종료 코드 0이다. 라이브에서 본사 세션으로 생성 201·같은 키 재호출 200 같은 ID·새 키 같은 내용 200 같은 ID·공백·0인·21인·키 누락 400·없는 지점 404·지점 직원 403·세션 없음 401을 확인했고, 관리자 4001 브라우저에서 `객실 유형 3종` → 추가 → `4종` 전환을 확인했다. 검증용 객실 유형과 멱원 기록은 모두 삭제해 원래 3종으로 복원했다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-19-hq-room-type-create.md)을 따른다.

## 본사 재고·가격 읽기 전용 조회 (2026-09-19)

- `admin-menu-roadmap.md` 2번 `재고·가격`의 읽기 전용 1차를 구현했다. `inventory_day`는 예약·만료·변경 작업이 내부적으로만 변경하고 본사가 일자 재고를 볼 수단이 없었다.
- `GET /api/staff/hotels/{hotelId}/inventory`가 객실 유형별 일자 재고(`capacity`·`held`·`confirmed`·`remaining`)를 반환한다. SELECT만 쓰고 재고를 변경하지 않는다.
- 관리자 `/dashboard/inventory`가 지점·기간·7일·14일·30일 프리셋을 선택해 객실 유형(행) × 숙박일(열) 그리드를 보여주고 잔여가 0이면 매진으로 표시한다. `nav.ts`의 `카탈로그` 그룹은 `HQ_ADMIN`에만 노출된다.
- 첫 버전은 객실 유형을 select로 하나씩만 볼 수 있었고 14일이 세로로 쌓여 흐름이 보이지 않았다. 사용자가 이 점을 불편하다고 해서 모든 유형을 한 화면에 비교하는 그리드로 바꿨다.
- API 통합 테스트 16건(재고 9 + 카탈로그 7), 관리자 `tsc --noEmit`·`eslint`(경고 2건)·Playwright 18건이 종료 코드 0이다. 390×844에서 가로 넘침이 없음을 확인했다. 라이브에서 본사 세션으로 속초 재고가 `capacity=12, confirmed=2, remaining=10`으로 내려왔고, 지점 타 지점 403·없는 지점 404·역순 기간 400·세션 없음 401을 확인했다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-19-hq-inventory-read-only.md)을 따른다.

## 본사 지점·객실 유형 카탈로그 읽기 전용 조회 (2026-09-19)

- `admin-menu-roadmap.md` 1번 `지점·객실 유형 관리`의 읽기 전용 1차를 구현했다. `hotel`·`room_type`·`rate_plan`·`rate_day` 테이블은 있었으나 본사가 카탈로그를 보는 API와 화면이 없었다.
- `GET /api/staff/hotels/{hotelId}/room-types`가 객실 유형별로 요금제와 일자별 요금의 `pricedDays`·`min`·`max`·`avg` 금액을 반환한다. SELECT만 쓰고 가격·재고·예약 상태를 변경하지 않는다.
- 관리자 `/dashboard/hotels`가 지점 선택기와 객실 유형 표를 보여준다. `nav.ts`의 `카탈로그` 그룹은 `HQ_ADMIN`에만 노출되고, 다른 역할은 본사 전용 안내를 본다.
- API 통합 테스트 7건, 관리자 `tsc --noEmit`·`eslint`(경고 2건)·카탈로그 Playwright 5건이 종료 코드 0이다. 라이브에서 본사 세션으로 속초 카탈로그가 `totalCount=3`·`pricedDays=90`으로 내려왔고, 지점 타 지점 403·없는 지점 404·세션 없음 401을 확인했다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-19-hq-hotel-room-type-catalog.md)을 따른다.

## 관리자 AI 운영 메뉴와 LLM 측정 노출 (2026-09-19)

- `admin-menu-roadmap.md` 8번 `AI 운영`의 첫 단계를 구현했다. 이전 작업이 `llm.outcome`을 INFO 로그로 남기게 했지만 로그를 grep하지 않으면 호출 수·실패율·평균 지연을 볼 수단이 없었다.
- concierge `GET /metrics/llm`이 프로세스 단위 카운터(`count`·`totalElapsedMs`·`avgElapsedMs`)를 노출한다. `extract_with_llm`이 매 호출마다 카운터에 더한다. 정규식 폴백 동작은 그대로다.
- 관리자 `/dashboard/ai-operations`가 모델·전체 호출·성공 외 호출 카드와 결과별 집계 표를 읽기 전용으로 보여준다. `nav.ts`의 `AI` 그룹은 `HQ_ADMIN`에만 노출되고, 다른 역할은 본사 전용 안내를 본다.
- `next.config.ts`의 rewrite가 `/concierge/:path*`를 `127.0.0.1:9000`으로 보낸다. concierge에 세션 검증이 없으므로 노출 범위는 compose의 루프백 포트 바인딩으로 제한한다.
- Python `pytest` 27건, 관리자 `tsc --noEmit`·`eslint`(경고 1건)·AI 운영 Playwright 4건이 종료 코드 0이다. concierge 재빌드 뒤 라이브 `/chat` 200으로 `success count=1 avgElapsedMs=3392.3`이 쌓이고 `/metrics/llm` 200으로 같은 값이 내려왔다. 관리자 4001의 `/concierge/metrics/llm`도 200이다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-19-concierge-llm-metrics-and-admin-menu.md)을 따른다.

## 관리자 메뉴 로드맵 (2026-09-19)

- 구현된 관리자 메뉴 5종(운영 대시보드·예약 관리·오늘의 운영·정산·대사·웹사이트 CMS)과 설계서의 본사 범위를 비교해 8개 메뉴를 [로드맵](../plans/admin-menu-roadmap.md)으로 정리했다. 지점·객실 유형, 재고·가격, 직원 계정, 통계·리포트, 공통 정책, 고객 요청, 감사 이력, AI 운영이다.
- 그중 지점·객실 유형, 재고·가격, 직원 계정, 공통 정책은 API가 전혀 없다. 통계·감사는 데이터는 있으나 조회 API와 화면이 없다. AI 운영은 측정 분기만 있고 수집·노출이 없다.



- 고객 웹이 `/chat` 400(정책 위반)과 502(연결 장애)를 같은 "도우미에 연결하지 못했습니다"로 처리했다. 도우미가 "결제해 줘" 같은 요청을 정책상 거절한 건을 연결 장애로 오해했으므로 안내를 나눴다.
- `concierge-panel.tsx`의 `submit`이 응답 상태를 검사해 400이면 `도우미가 처리할 수 없는 조건입니다. 객실 검색에서 직접 선택해 주세요.`를 보여준다. 서버 `detail` 원문을 노출하지 않고 상태 코드만으로 안내를 고른다. `.concierge-error`에 `line-height`·`overflow-wrap`을 넣어 390px에서 안내문이 넘치지 않게 했다.
- `app/llm.py`에 LLM 한 번 호출의 결과(`success`·`schema_rejected`·`unparsable`·`empty_response`·`api_error`·`no_key`)와 소요 시간을 측정하는 분기를 추가했다. `extract_with_llm`과 `measure_llm_outcome`이 같은 `_run_llm`을 써서 Gemini 호출을 두 번 수행하지 않는다. message 원문은 로그에 남기지 않는다.
- 측정을 `logger.debug`에서 `logger.info`로 올려 운영 로그에서 지속 수집되게 했다. 처음엔 실패할 때만 `debug`로 남겼으나 성공률·지연이 안 보이고, uvicorn의 root 로거가 기본 WARNING이라 실패마저 숨었다. `app/main.py`가 `logging.basicConfig(level=logging.INFO, ...)`와 `logging.getLogger("app").setLevel(logging.INFO)`로 로거를 구성한다.
- Python `pytest` 25건, 고객 웹 `tsc --noEmit`·concierge Playwright 4건이 종료 코드 0이다. concierge 이미지를 재빌드한 뒤 라이브 `/chat` 200과 `docker logs`의 `INFO app.llm llm.outcome model=gemini-3.6-flash outcome=success elapsed_ms=3392.39`를 확인했고, 조건에 `payment`를 넣었을 때 400을 확인했다. "결제해 줘"만으로는 LLM이 결제로 해석하지 않아 200이 되는데, `assert_no_forbidden_fields`가 `criteria`의 금지 필드만 거절하고 자연어 암시까지 차단하지 않기 때문이다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-19-concierge-policy-notice-and-llm-telemetry.md)을 따른다.

## AI 도우미 Gemini LLM 연결 (2026-09-19)

- 루트 `.env`의 `GOOGLE_API_KEY`로 Gemini를 연결했다. LLM은 조건 해석만 담당하고 가격·빈 객실·예약 확정은 여전히 Spring Boot에 있다. `app/llm.py`가 `gemini-3.6-flash`에 JSON 스키마·시스템 지시문을 전달해 조건을 추출하고 `merge`가 LLM 우선·정규식 폴백 순서로 동작한다.
- 스키마는 단일 타입만 쓴다. union 타입을 쓰면 빈 응답이 내려오고, `additionalProperties: false`를 넣으면 모델이 키를 `destination`·`check_in`처럼 임의로 바꿔서 내려온다. 빈 문자열·0·`False`를 `_coerce`가 "추출하지 못함"으로 해석한다.
- 도우미가 "어른 넷이랑 아이 둘"·"3박 할건데" 같은 정규식 불가 표현을 해석하게 됐다. 누락된 값은 LLM이 비워두므로 `check_missing`이 "체크인 날짜, 체크아웃 날짜를 알려주세요."로 이어진다.
- `compose.yaml`의 `concierge` 서비스가 `GOOGLE_API_KEY`를 컨테이너로 넘긴다. 키가 없으면 빈 문자열로 둬 폴백 동작을 유지한다. `gemini-2.5-flash`는 API가 404로 거부해서 `gemini-3.6-flash`를 쓴다.
- Python `pytest` 17건(그래프 3 + 정책 6 + LLM 8)이 종료 코드 0이다. Docker 재빌드 뒤 `POST /chat` 200으로 LLM 추출 결과가 내려오고, 같은 조건의 Spring `GET /api/availability`와 금액·잔여 수가 `MATCH true`로 일치했다. 조건에 `payment`를 넣으면 LLM 경로라도 400으로 거부됐다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-19-concierge-gemini-llm.md)을 따른다.

## AI 도우미 정책 임베딩과 고객 대화 E2E suite (2026-09-19)

- 도우미가 지키는 경계를 주석·설계 문서에서 `app/policy.py`의 검증 가능한 코드로 옮겼다. `POLICY_TEXT`를 추천 응답의 `policy` 필드로 내보내고, `missing_fields`가 빈 문자열·0·None을 모두 누락으로 계산한다. `assert_no_forbidden_fields`가 `payment`·`confirm`·`reservationId`·`roomId`가 조건에 들어가면 조회 전에 거부하고, `assert_offers_from_api`가 금액·잔여 필드가 없는 후보를 거부한다.
- `app/main.py`는 정책 위반(`ValueError`)을 400으로, 나머지를 기존 502로 매핑한다. 정책 위반을 502로 감싸면 도우미가 조회 실패로 회피하는 것과 같으므로 코드를 나눴다.
- `apps/web/test/concierge-panel.spec.ts` 3건을 영구 suite로 추가했다. 추천 적용 시 AI 후보를 예약 카드에 복사하지 않고 `GET /api/availability`가 새로 발생하는지, ASK 응답과 390px 가로 넘침, `/chat` 502의 직접 검색 안내를 확인한다.
- Python `pytest` 9건(기존 3 + 정책 6), 고객 웹 `tsc --noEmit`·concierge Playwright 3건이 종료 코드 0이다. Docker 재빌드 뒤 `POST /chat` 200으로 `policy` 전문이 내려가고 추천 1건이 Spring `GET /api/availability`와 정확히 일치했다. 조건에 `payment`를 넣으면 400 `AI는 payment 조건을 처리할 수 없습니다.`로 거부했다.
- 전체 Playwright 회귀는 28 passed / 5 failed다. 실패 5건은 toss SDK 로딩·세션 타이밍 영역이며 이번 변경을 작업 트리에서 제외해도 동일하게 실패해 무관함을 확인했다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-19-concierge-policy-embedding.md)을 따른다.

## 저장 초안 미리보기 만료 시간 설정 (2026-09-19)

- 저장 초안 미리보기 grant의 만료 시간이 10분으로 고정돼 있었다. `WebsitePreviewGrantService`의 상수 TTL을 `website.preview.ttl-minutes`(기본 10, 1~60) 생성자 주입으로 바꿔 환경에서 조정할 수 있게 했다. 1~60분 밖의 값은 시작을 거부한다.
- `WebsitePreviewExpiryIntegrationTest`를 새로 작성해 클럭을 조작하지 않고 TTL 1분 + 여유 5초를 실제 시간으로 기다린 뒤 200이 410 `WEBSITE_PREVIEW_UNAVAILABLE`로 바뀌는지 검증했다.
- API `mvnw test-compile`과 해당 테스트 1건이 종료 코드 0이다. 라이브로 본사 세션으로 홈 초안 저장(200, draftVersion 6) → grant 발급(200, `expiresAt` 명시) → 고객 4000에서 200 → 만료 시각 12초 후 410 `WEBSITE_PREVIEW_UNAVAILABLE` 전환을 확인했다. 검증용 grant는 폐기(204)했고 초안도 원래 제목으로 복원(200, draftVersion 7)했다.
- 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-19-website-preview-ttl.md)을 따른다.

## 본사 정산 실행 생성·재시도 (2026-09-19)

- 본사가 원하는 기간의 정산 실행을 직접 만들고, 실패한 실행을 다시 대기 상태로 옮길 수 있게 했다. 실행 제어만 추가하고 결제·재고 상태를 직접 바꾸는 동작은 두지 않았다.
- `POST /api/staff/settlements/runs`가 기간을 받아 `PENDING` run을 만들고 `POST /api/staff/settlements/runs/{runId}/retry`가 `FAILED` run을 다시 대기로 옮긴다. `TossSettlementRunService.retry`가 실행 존재 여부와 상태를 먼저 확인해 404·409를 구분한다.
- 관리자 4001에 기간 입력 폼과 `FAILED` 카드의 재실행 버튼을 추가했고, 빈 목록 안내를 실행 요청 폼으로 바꿨다.
- API `mvnw compile`·`test-compile`, 정산·변경 정산 집중 영역 42건, 관리자 `tsc --noEmit`·`eslint`(에러 0·경고 3건)이 종료 코드 0이다. 브라우저에서 본사 로그인 뒤 실행 생성과 409·404 응답까지 1개 Playwright 시나리오로 확인하고 스크립트·데이터는 삭제했다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-19-hq-settlement-run-control.md)을 따른다.

## 정산·대사를 토스 test 환경까지 허용 (2026-09-19)

- 이전 세션이 정산 스택을 `toss-live` 전용에서 토스 환경 전용(`fake` 이외)으로 확장하다가 중단됐다. `TossPaymentEnvironment` import가 빠져 `mvnw compile`·`test-compile`이 실패하는 상태였고, 테스트는 여전히 3-arg 생성자를 호출했다.
- 5개 서버 파일의 import와 로드 조건을 마저 맞추고 대사 SQL·run insert의 provider를 상수에서 `TossPaymentEnvironment.from(providerMode).providerCode()` 바인딩으로 바꿨다. `TossSettlementController`·`TossSettlementQueryService`도 worker와 같은 조건으로 따라간다.
- V45 additive migration으로 `toss_settlement_run.provider` CHECK를 `TOSS_LIVE`에서 `IN ('TOSS_TEST','TOSS_LIVE')`로 확장했고, `compose.yaml`이 `TOSS_SETTLEMENT_*` 변수를 컨테이너로 전달하게 했다. 로컬 `.env`는 `PAYMENT_PROVIDER=toss-test`이므로 정산이 로드되지 않던 원인이 해소됐다.
- API `mvnw compile`·`test-compile`과 정산·결제 집중 영역 77건이 종료 코드 0이다. test DB·개발 DB 모두 V45 `success=t`, API 재빌드 뒤 health `UP`를 확인했다.
- 루트 `.env`에서 `TOSS_SETTLEMENT_ENABLED=true`로 켜고 라이브로 확인했다. 엔드포인트가 404에서 401로 바뀌었고, 본사 세션으로 `GET /api/staff/settlements/runs`가 200을 반환했다. 관리자 4001에서 본사 로그인 뒤 `/dashboard/settlements`의 실행 카드·상세·상태 필터, 지점 직원의 본사 전용 안내까지 3개 Playwright 시나리오로 확인했다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-19-settlement-toss-test-mode.md)을 따른다.

## 본사 읽기 전용 정산·대사 조회 (2026-09-19)

- 토스 정산 snapshot·내부 거래 대사 결과를 본사 관리자가 읽기 전용으로 확인하는 API와 관리자 화면을 추가했다. 서버의 정산·결제·재고 변경 동작은 그대로 두고 조회만 연결했다.
- `TossSettlementQueryService`·`TossSettlementController`가 `GET /api/staff/settlements/runs`·`GET /api/staff/settlements/runs/{runId}`를 제공한다. SELECT만 사용하고, `limit`은 runs 1~100·rows 1~500으로 고정하며, `status` 필터는 허용된 6개 대사 상태만 받는다. 본사 관리자 세션만 `requireHeadquarters`로 통과한다.
- 관리자 `/dashboard/settlements`에 실행 목록 카드, 실행 상세의 요약·상태 필터·대사 표, 새로고침을 구현했다. `nav.ts`의 "재무" 그룹은 `HQ_ADMIN` 메뉴에만 노출된다.
- `TossSettlementQueryIntegrationTest` 7건, 같은 실행에서 기존 settlement 4건·변경 정산 16건이 종료 코드 0이다. API `compile`·`test-compile`, 관리자 `tsc --noEmit`과 `eslint`(에러 0·경고 3건), 웹 `tsc -b`도 종료 코드 0이다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-19-hq-settlement-reconciliation-view.md)을 따른다. 라이브 정산 환경에서의 브라우저 확인은 사용자가 수행한다.

## 중단 작업 복구 확인 (2026-09-19)

- 이전 세션의 작업은 `0d5df51` 커밋으로 이미 완료돼 있었다. 남아 있던 `wip-customer-change-zero-refund-and-docs` 스태시는 병합 이전 상태 기반이라 내용이 커밋에 포함됐는지 파일별로 대조한 뒤 삭제했다.
- 3개 변경 기록 문서의 상태 줄을 `작업 트리에만 있고 커밋하지 않은 상태`에서 `0d5df51`로 커밋됨`으로 바로잡았다.
- 중단 전 검증을 현재 HEAD에서 다시 실행해 종료 코드 0을 확인했다. 웹 계약 테스트 25종·`tsc -b`, API `mvnw compile`·`CustomerSelfServiceReservationChangeIntegrationTest` 6건(오류 0). Vite production build, `ReservationChangeSettlementIntegrationTest`, Playwright 브라우저 회귀, 라이브 PostgreSQL 개발 DB는 실행하지 않았다.
- `.gitattributes` 한글 주석이 드라이브에 깨진 UTF-8로 내려와 있어 올바른 UTF-8로 다시 썼다. git blob은 정상 UTF-8이므로 작업 트리만 교체했다.

## origin/main 병합 및 고객 예약 변경 작업 통합 (2026-09-18)

- 로컬 `main`이 `origin/main`보다 13개 커밋 뒤처져 있었다. Toss 라이브 결제·정산 조정(`V43`, `V44`, `TossSettlementWorker`)과 고객 변경 결제 복구 강화를 받았다.
- 진행 중이던 고객 예약 변경 변경 3종과 같은 파일·같은 줄이 충돌해 작업 트리를 stash로 보관한 뒤 fast-forward 병령하고 stash를 복원해 3개 충돌을 수동으로 해결했다. 업스트림의 `toss-live` provider 지원과 내 `REFUND_PENDING` 동선을 모두 유지했다.
- 복원 과정의 손상 2건을 수정했다. `ReservationChangeSettlementService.java`의 중복 `lockRefundableTransaction` 정의를 제거하고, `reservation-change-payment-page.tsx`의 잘못된 토큰을 제거했다.
- `.gitignore`에 `.tmp/`를 추가하고 참조하지 않는 2.6MB 임시 이미지를 삭제했으며 `README.md`를 UTF-16에서 UTF-8로 교체해 git이 바이너리로 취급하던 문제를 풀었다.
- API compile·test-compile, `CustomerSelfServiceReservationChangeIntegrationTest` 6건, `ReservationChangeSettlementIntegrationTest` 10건, 웹 계약 테스트 25종·TypeScript·production build가 종료 코드 0이다. 작업 트리에만 있고 커밋하지 않았다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-18-origin-main-merge-integration.md)을 따른다.

## 고객 예약 변경 0원·환불 동선 안내 (2026-09-18)

- 고객이 직접 예약을 변경할 때 차액이 없거나 환불이 발생하면 추가 결제 화면이 아닌 예약 상세로 보내도록 수정했다. 해당 화면의 세션과 결제 버튼은 추가 결제 시도에만 발급되므로 0원·환불 변경은 진행 상태를 알기 어려웠다.
- `changeSettlementDestination`이 `AWAITING_PAYMENT`만 결제 화면으로 구분하고, `REFUND_PENDING`·`READY_TO_APPLY`는 예약 상세에서 폴링한다. 결제 화면은 `REFUND_PENDING` 상태를 표시하고 폴링하며, 환불 중에는 결제 창 복구 버튼을 숨기고 예약 상세 링크를 제공한다.
- 고객 변경 시작 거부 코드 6종(매진·가격 변경·진행 중 변경·정산 비활성화·결제 수단 미지원·환불 내역 부족)을 한국어·영어 행동 안내로 매핑했다.
- 웹 계약 테스트 25종·TypeScript·production build, API compile과 `CustomerSelfServiceReservationChangeIntegrationTest` 6건이 종료 코드 0이다. 작업 트리에만 있고 커밋하지 않았다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-18-customer-change-zero-refund-destination.md)을 따른다. 브라우저와 실제 Toss 운영 결제 검증은 사용자가 수행한다.

## 고객 예약 변경 환불 대상 거래 선택 (2026-09-18)

- 고객이 직접 예약을 변경해 차액 환불이 발생할 때 환불 대상을 항상 원결제로 고정하던 문제를 수정했다. 원결제가 이미 부분 환불돼 잔액이 부족하고 뒤이은 변경 추가 결제에 잔액이 있으면, 해당 추가 결제 거래를 환불 대상으로 선택한다.
- `ReservationChangeSettlementService`의 `lockRefundableTransaction`이 `ORIGINAL_CHARGE`·`CHANGE_CHARGE` 후보 중 잔액이 충분한 최신 거래를 `for update`로 선택한다. 공급자·통화·상점·게이트웨이 일치 검증과 활성 공급자 호환 확인은 유지한다.
- `CustomerSelfServiceReservationChangeIntegrationTest` 6건, 웹 계약 테스트 25종, TypeScript 검사와 production build가 종료 코드 0이다. 작업 트리에만 있고 커밋하지 않았다. 상세 범위와 미검증 항목은 [변경 기록](../changes/2026-09-18-customer-change-refund-target-selection.md)을 따른다. 브라우저와 실제 Toss 운영 결제 검증은 사용자가 수행한다.

## 고객 예약 최종 리뷰 보완 (2026-09-15)

- 최초 fake 결제 완료 동선, 요청 전 관리 token·멱등 key 보존, 만료/확정 확보 정리, 완료 화면 새로고침 복구를 보강했다. 예약 상세에는 객실·인원·결제·구조화 취소 정책과 관리 token으로 조회하는 승인/환불 변경 상태를 표시한다.
- 메인 병합에서는 기존 Toss 초기·추가 결제·부분/전체 환불 어댑터를 보존했다. 취소 미리보기와 변경 정산은 활성 공급자와 예약 거래가 일치할 때만 허용하며 fake/Toss 혼합 처리를 차단한다. UNKNOWN 조회·상점/금액 검증·환불 완료 전 재고 보존은 기존 메인 규칙을 따른다.
- 전용 예약 화면과 기존 날짜·인원·호텔 선택기의 영문 UI, 같은 origin의 `/en/booking/complete` 복귀를 보완했다. 이메일 보안 링크·다른 기기 복구는 미지원으로 표시하며 자동 전달을 주장하지 않는다.
- 최종 검증 상세와 사용자 미실행 범위는 [검증 기록](../changes/2026-09-15-customer-booking-journey-redesign.md)을 따른다.

## 고객 예약·변경 여정 재설계 비브라우저 검증 (2026-09-15)

- 고객 예약 흐름을 홈 검색 시작점, 전용 결과·checkout·완료, 동일 브라우저 예약 관리, 변경 비교 결제 화면으로 분리했다. 가격·재고·예약·변경 차액의 판단은 Spring Boot 응답에 계속 남고, URL에는 검색 조건만 둔다.
- 고객 순수 계약 11개, 관리자 링크 상태 계약 1개, 고객·관리자 TypeScript와 production build, API compile·test-compile 및 Toss HTTP/설정 비DB 단위 테스트 6건을 현재 HEAD에서 실행해 종료 코드 0을 확인했다. 자세한 명령·범위는 [고객 예약·변경 여정 재설계 검증 기록](../changes/2026-09-15-customer-booking-journey-redesign.md)에 남겼다.
- 사용자 소유 범위인 Playwright·브라우저/UI, 라이브 API·PostgreSQL 및 DB 통합, Toss 승인·추가 결제·부분 환불·webhook은 실행하지 않았다. 따라서 실제 예약·재고·거래 정합성은 아직 통과로 기록하지 않으며, 같은 기록의 사용자 체크리스트로 확인한다.

## 토스페이먼츠 테스트 결제 (2026-09-14)

- 실제 토스 위젯 테스트 결제창·340,000원 승인·예약 확정과 고객 결과 화면을 확인했다. 직원 취소 API로 같은 거래의 전액 환불 1건·잔액 0원·예약 CANCELLED·숙박일별 재고 반환까지 검증했다. 위젯 키 연동, 실제 MID 설정 및 만료 재승인 차단을 반영했다. 추가 결제·부분 환불·고객 취소 UI 클릭·외부 webhook은 미검증이며 [결제창 진단 기록](../changes/2026-09-14-toss-checkout-diagnostics.md)에 상세 근거가 있다.

- 테스트 키만 허용하는 토스 신규 예약·변경 추가 결제·부분 환불·전체 취소를 추가했다. 서버 주문·금액·상점·거래를 검증하고, UNKNOWN은 재조회 전까지 재고와 hold를 보존한다. 기존 fake 결제는 provider 분리로 유지한다.
- 취소는 모든 Toss 거래의 검증된 환불 전까지 완료·재고 반환으로 처리하지 않는다. 명시 실패는 같은 저장 환불 계획과 PG 멱등 키로만 재시도하고, 성공·UNKNOWN 결과는 보존한다. callback과 webhook은 성공 권한이 아니며 webhook은 알려진 주문 lookup 힌트다.
- 고객은 서버 provider에 따라 SDK 또는 fake 버튼만 표시하고, callback query를 API 호출 전 제거한다. 권한 있는 UNKNOWN 재조회와 NEW callback 재전송을 구분하며 대기·실패를 완료로 표시하지 않는다. 상세 검증·실제 sandbox 미검증 범위는 [토스페이먼츠 테스트 결제 연결](../changes/2026-09-14-toss-test-payment.md)을 따른다.

## 실제 객실 운영 상태·투숙 중 객실 이동 (2026-09-14)

- 실제 객실의 청소 상태와 점검·판매 중지 운영 상태를 분리하고, 영향 예약을 확인하는 지점별 상태 전환과 감사 이력을 추가했다. 판매 중지는 활성·미래 확정 배정이 있으면 서버가 차단하고, 운영 복구는 청소 완료 객실만 허용한다.
- 체크인 중인 고객은 같은 호텔·객실 유형의 청결하고 운영 가능한 빈 객실로 원자 이동할 수 있다. 이전 객실은 청소·점검 필요로 전환되며 이동과 운영 이벤트를 함께 감사한다. 신규 배정·체크인 전 재배정·체크인도 운영 불가 객실을 제외한다.
- 백엔드 직접·인접 회귀 64건과 기존 4001을 재사용한 관리자 Chromium 13건, TypeScript·production build가 통과했다. Compose API 4080 재빌드 후 V36·V37과 readiness 200, 실제 인앱 브라우저의 운영 요약·카드를 읽기 전용으로 확인했다. 전체 backend suite의 선행 미디어 scheduling test 1건은 기능 전 `main`에서도 같은 bean 부재로 실패한다. 상세 범위는 [실제 객실 운영 상태·투숙 중 이동 기록](../changes/2026-09-14-room-operational-status-checked-in-move.md)을 따른다.

## 예약 변경 승인·차액 정산 (2026-09-13)

- 체크인 전 미배정 확정 예약의 일정·객실 유형 변경을 요청·견적·역할별 승인·재고 hold·차액 정산·원자 적용 흐름으로 전환했다. 지점은 절대 차액 100,000원까지 직접 승인하고, 초과 요청은 본사가 승인 또는 반려한다.
- 추가 결제 고객 링크, 원결제 부분 환불, outbox lease와 멱등 처리, 불명확 결과의 본사 reconciliation, 고객 fragment→HttpOnly 세션 교환을 구현했다. 가격·재고·정산 성공 판단과 최종 예약 적용 권한은 Spring Boot가 가진다.
- 서버 대상 회귀 80건, 관리자 Chromium 10건, 고객 결제 Chromium 2건, 관리자·고객 production build가 통과했다. 개발 API 4080은 V35·readiness `UP`, 기존 관리자 4001은 새 승인 대기열과 예약 목록 정상 표시를 확인했다. 상세 범위와 실제 PG 전 미검증 항목은 [예약 변경 승인·차액 정산 기록](../changes/2026-09-13-reservation-change-settlement-approval.md)을 따른다.

## 체크인 전 예약 일정·객실 유형 변경 (2026-09-13)

- 예약 상세에서 `CONFIRMED`이고 지점 현지 체크인일 전이며 실제 객실이 배정되지 않은 예약의 날짜와 객실 유형을 변경할 수 있다.
- 직원 전용 미리보기는 예약 자신의 기존 확정 재고를 가용 수량에 반영하고 최신 일별 요금·총액 차이를 반환한다. 변경 확정은 예약과 기존·신규 재고를 잠근 뒤 재고 이전, 예약·일별 요금 교체와 V33 감사를 한 트랜잭션에서 처리한다.
- 현재 테스트 결제 범위에서는 실제 추가 결제·부분 환불 없이 예약 총액과 감사 차액만 갱신한다. 관련 서버 회귀 54건, 예약 관리 Chromium E2E 6건과 변경 프런트 ESLint를 통과했으며 상세 범위는 [체크인 전 예약 일정·객실 유형 변경 기록](../changes/2026-09-13-staff-reservation-stay-change.md)을 따른다.

## 체크인 전 투숙 인원 변경 (2026-09-13)

- 예약 상세에서 `CONFIRMED` 예약의 성인·아동 수만 변경할 수 있다. 서버는 성인 1명 이상, 아동 0명 이상과 `객실 유형 최대 수용 인원 × 예약 객실 수` 한도를 확인한다.
- 예약 행 잠금과 직원 포함 멱등 요청 지문을 적용하고, V32 감사 테이블에 이전·신규 인원과 처리 직원을 기록한다. 날짜·객실 유형·객실 수·가격·결제는 변경하지 않는다.
- 상세 범위와 검증은 [체크인 전 투숙 인원 변경 기록](../changes/2026-09-13-staff-reservation-party-update.md)을 따른다.

## 체크인 전 배정 객실 변경 (2026-09-13)

- 예약 상세에서 `CONFIRMED` 예약의 현재 배정 한 실을 같은 호텔·객실 유형의 청결 객실로 변경할 수 있다. 후보 조회 후 서버가 예약과 두 객실을 잠그고 숙박 기간 충돌을 다시 검증한다.
- V31 감사 테이블에 이전·신규 객실 번호와 처리 직원을 기록하고, 직원까지 포함한 멱등 요청 지문으로 응답 유실 재시도를 보장한다. 선택을 바꾸면 UI가 새 요청 키를 발급한다.
- 체크인 이후 변경·배정 해제·예약 조건 변경은 포함하지 않았다. 상세 범위와 검증은 [체크인 전 배정 객실 변경 기록](../changes/2026-09-13-staff-room-reassignment.md)을 따른다.

## 예약 상세 미배정 객실 배정 (2026-09-13)

- 확정 예약에 미배정 객실이 남으면 예약 상세에서 기존 후보 조회·배정 API로 한 실씩 배정할 수 있다. 성공 시 목록을 다시 조회하며 완전 배정 뒤에는 추가 배정 기능을 숨긴다.
- 동일 예약·동일 객실 재호출은 중복 없이 성공으로 처리하고, POST 시점의 객실 청결 상태도 잠금 뒤 다시 확인한다. 직원 운영 통합 테스트 14건, 예약 관리 Chromium E2E 3건, 변경 파일 ESLint, UI detector와 production build를 통과했다.
- 날짜·객실 유형·가격·결제 변경은 포함하지 않았다. 상세 범위와 검증은 [예약 상세 미배정 객실 배정 변경 기록](../changes/2026-09-13-reservation-detail-room-assignment.md)을 따른다.

## 직원 예약자 정보 정정 (2026-09-13)

- 예약 상세에서 `CONFIRMED` 예약의 예약자 이름·이메일만 정정할 수 있다. 본사 관리자 또는 예약 지점 직원을 서버에서 확인하고, 예약 행 잠금과 멱등 키로 중복 처리를 막는다.
- V30 감사 테이블에는 이전값·변경값·처리 직원을 기록하며, 요청 지문에도 처리 직원을 포함한다. 무변경, 유효하지 않은 입력, 확정 이외 상태와 타 지점 요청은 예약과 감사 이력을 변경하지 않는다.
- 백엔드 대상 테스트 29건, 예약 관리 Chromium E2E 2건, 변경 파일 ESLint, UI detector와 production build를 통과했다. 개인정보 감사 이력은 이번 MVP에서 자동 파기하지 않으며 상세 범위는 [직원 예약자 정보 정정 변경 기록](../changes/2026-09-13-staff-reservation-guest-update.md)을 따른다.

## 직원 예약 취소 (2026-09-13)

- 예약 상세에서 확정 예약의 저장된 취소 정책·예상 환불액·마감 시각을 서버로 확인하고, 본사 관리자 또는 예약 지점 직원이 명시적 확인 후 취소할 수 있다.
- 취소는 기존 환불·재고 복구·멱등 처리 코어를 재사용하며, 처리 직원은 `cancellation_attempt`에 감사 정보로 남는다. 실패 시 확인창에서 원인을 확인하고 같은 요청을 안전하게 재시도할 수 있다.
- 통합 테스트 9건, 예약 관리 Chromium E2E, 변경 파일 ESLint와 production build를 통과했다. 상세 범위는 [직원 예약 취소 변경 기록](../changes/2026-09-13-staff-reservation-cancellation.md)을 따른다.

## 직원 예약 조회 (2026-09-13)

- 지점 권한과 기준일을 적용하는 직원 예약 조회 API를 추가했다. 고객 이름·이메일·예약 번호 검색과 상태 필터를 지원하며, 응답에는 객실 유형·요금제·투숙 인원·금액·배정 객실을 포함한다.
- 관리자에 `예약 관리` 화면을 추가해 지점 선택, 날짜 달력, 검색, 상태 필터, 반응형 예약 표와 상세 대화상자를 한 화면에서 제공한다. 날짜·객실·인원·금액 변경과 당일 운영 액션은 포함하지 않았다.
- 상세 구현과 검증 범위는 [직원 예약 조회 변경 기록](../changes/2026-09-13-staff-reservation-search.md)을 따른다.

## 고객 공개 SEO 메타데이터 (2026-09-13)

- 고객 공개 페이지는 현재 origin과 정규화된 공개 경로로 canonical과 `og:url`을 만들고 발행 SEO를 title·description·Open Graph에 반영한다. 공개 화면은 `robots=index,follow`, 저장 초안 미리보기는 `noindex,nofollow`다.
- 직접 계약 테스트, production build와 실제 영문 CMS 경로의 브라우저 DOM을 확인했다. OG 대표 이미지 편집·sitemap·크롤러용 서버 렌더링은 포함하지 않았다. 상세 범위는 [변경 기록](../changes/2026-09-13-public-seo-metadata.md)을 따른다.

## 저장 초안 URL 미리보기 (2026-09-13)

- V24 grant로 저장된 최신 한국어·영어 홈/지점 랜딩/일반 페이지를 실제 고객 URL에서 10분간 검토한다. 기존 저장 전 인메모리 dialog와 별개이며 미저장 변경사항은 발급 전에 저장한다.
- 본사 관리자·편집자·게시자만 발급하고 발급자 또는 본사 관리자가 폐기한다. token 해시만 DB에 저장하고 fragment는 고객 요청 전에 제거한다. 일반 이동·종료·만료 시 검토 상태를 격리하고 예약·결제·취소·AI·콘텐츠 CTA는 실행하지 않는다.
- 개발 API를 현재 코드로 재빌드해 V24~V27 적용과 readiness를 확인했다. 실제 관리자 4001에서 발급한 저장 초안을 고객 4000에서 열어 배너·남은 시간·예약/AI 차단·종료 후 공개본 복귀를 확인했다. 고객 390×844에서 한글 제목이 어절 중간에 분리되지 않도록 회귀 검사와 CSS를 보강했다.
- 직접 테스트, 운영 HTTPS 설정과 미검증 범위: [변경 기록](../changes/2026-09-13-authenticated-saved-draft-url-preview.md). 실제 운영 배포·공개본 저장/발행·실제 예약/결제·10분 만료 시간 경과 검증은 수행하지 않았다.

## CMS 비동기 미디어 variant (2026-09-13)

- V25는 활성 업로드 PNG/JPEG에 원본 폭 이하의 640px·1280px WebP 작업을 멱등 enqueue/backfill하고, V26은 READY 메타데이터 제약을 additive하게 보강하며, V27은 새 worker claim을 attempt와 함께 식별하는 nullable `claim_token`을 추가한다. V25 파일은 최초 적용 checksum을 유지한다.
- 전용 단일 스레드의 2초 worker는 예약 만료 scheduler와 분리하며 `FOR UPDATE SKIP LOCKED`, 5분 lease, 30초·2분 backoff와 최대 3회 시도를 사용한다. 완료 파일은 claim별 고유 immutable key로 발행하고 덮어쓰지 않으며 rollback은 자기 파일만 정리하고 `STATUS_UNKNOWN`은 보존한다. 만료된 PROCESSING attempt 3은 `SKIP LOCKED LIMIT 1`로 한 건씩 terminal FAILED로 정리해 다른 작업 선점을 막지 않는다.
- 공개 READY variant는 `image/webp`와 1년 immutable cache로 전달한다. 관리자 미디어 선택기는 상태·규격·용량·실패와 폭별 재시도를 표시하고 ACTIVE 자산의 PENDING/PROCESSING·FAILED 1·2회에서 polling한다. polling/재시도 응답은 편집 메타데이터·버전을 유지하고 이전 API의 누락 variants도 정규화한다. 원본 URL·한국어/영어 페이지 JSON은 유지하며, 공개·저장 초안 미리보기 응답은 참조 중인 READY variant를 응답 전용 map으로 제공한다. 고객 HERO와 이미지 갤러리는 이를 검증한 뒤 `<picture>`·`srcset`으로 사용하고 원본을 fallback으로 둔다.
- 서버 변경 범위 92건, 관리자 variant 1건과 영문 제목 미디어 회귀 18건, 관리자 TypeScript, 고객 parser 2개와 production build가 통과했다. 별도 4082 API·`db-test` 내 일회성 DB·전용 storage에서 V25→V26→V27, health `UP`, 실제 업로드·640/1280 READY, 640 WebP HTTP 200·정확한 캐시 헤더·RIFF/WEBP·원본 checksum 보존을 확인하고 컨테이너·DB·volume을 제거했다. 개발 4080 컨테이너와 개발 DB는 변경하지 않았다.
- rolling 배포에서는 이전 worker를 먼저 drain해야 하며, 실제 운영 다중 인스턴스 장기 부하와 CDN·객체 저장소는 후속이다. 고객 연결 결과는 [반응형 미디어 변경 기록](../changes/2026-09-13-customer-responsive-media.md), worker 상세는 [비동기 variant 변경 기록](../changes/2026-09-13-async-media-variants.md)을 따른다.
- 최종 리뷰 보완 검증은 서버 99건, 관리자 미디어 E2E 27건·API 경계 7건과 TypeScript를 통과했다. 실제 PostgreSQL terminal 잠금 회귀와 인코더 차단 중 예약 만료 스케줄 실행을 포함한다. 개발 API/DB mutation과 서버 재시작은 수행하지 않았다.

## CMS 미디어 저장소 점검 (2026-09-13)

- `HQ_ADMIN`은 관리자 미디어 선택기에서 DB의 업로드 원본·READY variant 참조와 로컬 named volume 파일을 수동으로 읽기 전용 비교할 수 있다. 누락 파일, 참조 없는 일반 파일, 10분 이상 지난 참조 없는 `.tmp`를 분리하고 최근 `.tmp`와 `.trash`는 제외한다.
- 응답은 상대 storage key만 제공하며 자동 삭제·복구·정기 실행은 하지 않는다. 실제 개발 API/DB/volume 점검은 health `UP`, 누락 0, orphan 0, 오래된 임시 파일 0이었다.
- 서버 관련 46건, 관리자 Chromium 2건(390×844 포함), TypeScript와 실제 CMS 정상 표시를 확인했다. 상세 범위는 [저장소 점검 변경 기록](../changes/2026-09-13-media-storage-audit.md)을 따른다.










## 목표와 범위





호텔 고객 웹·예약·직원 운영·본사 관리·LangGraph 예약 도우미. 상세 범위는 project-brief.md 참조.





- 국내 지점 위치 확정: 속초·설악산·제주도. 속초와 설악산은 별도 지점이며, 지점 메타데이터와 속초 개발 재고를 생성했다.











## 현재 상태

- 고객 웹의 한국어·영문 본문, UI, 브랜드와 제목을 Pretendard로 통일했다. 고객 웹에 `pretendard` 1.3.9를 고정하고 로컬 가변 다이나믹 서브셋을 사용하며, 기존 Google Fonts 요청과 `DM Sans`·`Noto Sans KR`·`Playfair Display` 선언을 제거했다. 영문 CMS 1280px·390px, 한국어 헤더 1186px·1024px, 데스크톱 액션 1440px Playwright 회귀 5건과 production build를 통과했다. 실제 한국어·영문 화면에서 대상 글리프 로딩과 계산 스타일, 가로 오버플로 부재를 확인했다. 상세 기록은 [고객 웹 Pretendard 변경 기록](../changes/2026-09-12-customer-pretendard-font.md)을 따른다.

- 고객 설악산 페이지의 약 1186px 화면에서 상단 메뉴가 글자 단위로 줄바꿈되던 문제를 수정했다. 메뉴 링크와 고정 헤더 요소를 한 줄로 유지하고 간격을 가용 폭에 맞게 조정하며, 1100px 이하에서는 헤더만 메뉴 버튼으로 전환한다. 데스크톱 우측의 `예약 조회`와 현재 언어 표시는 동일한 44px 높이와 하단 기준선으로 정렬했다. 1440px 액션 정렬, 1186px 한 줄 표시, 1024px 메뉴 열기, 영문 CMS 1280px·390px 회귀와 고객 production build를 통과했다. 실제 1441px 화면에서 두 액션의 상·하단과 높이가 일치하고 가로 오버플로가 없음을 확인했다. 상세 기록은 [고객 헤더 반응형 내비게이션 변경 기록](../changes/2026-09-12-customer-header-responsive-navigation.md)을 따른다.

- 사용자의 명시적 요청으로 CMS 트리의 편집 가능한 홈·지점 3개·브랜드 3개를 실제 영어로 번역해 모두 발행했다. 공개 경로는 `/en`, `/en/stays/{sokcho|seoraksan|jeju}`, `/en/brand/{story|haneul-story|forest-gallery-demo}`다. 영문 홈 발행 뒤 고객 파서가 block ID가 있는 `HOME` 응답을 거부하는 오류를 TDD로 수정했고, 고객 영문 E2E 1280px·390px 2건, 관련 직접 테스트 4건, production build와 실제 7개 URL 렌더링을 확인했다. 기존 한국어 원문·이미지·기간·연결은 유지했으며 예약 CTA는 아직 한국어 예약 화면으로 연결됨을 표시한다. 상세 기록은 [전체 영문 발행 변경 기록](../changes/2026-09-12-all-english-publication.md)을 따른다.

- V21은 영어 번역 초안을 version에 묶어 `DRAFT → IN_REVIEW → APPROVED → PUBLISHED`로 제한하고 V22는 검토 version 제약을 보강했다. V23은 `HQ_EDITOR`와 `HQ_PUBLISHER`를 추가해 영어 작성·검토 요청과 승인·반려·발행을 분리하고 자가 승인을 서버에서 거부한다. 관리자는 역할별 action과 승인자 읽기 전용 편집기를 표시한다. 번역 서버 통합 18건, 역할 UI Chromium 1건, 관리자 TypeScript 검사, 개발 DB V23 적용과 두 개발 계정의 로그인·CMS 트리 조회를 확인했다. 전체 관리자 E2E·전체 backend suite·한국어 역할 분리는 반복하거나 확장하지 않았다. 상세 결과와 이전 binary 롤백 제약은 [영어 번역 검토·승인 변경 기록](../changes/2026-09-12-cms-translation-review.md)을 따른다.

- V20은 기존 기획의 한국어·영어 독립 초안/발행 첫 단계를 구현했다. 기존 한국어 데이터/API를 유지하고 영어 번역·메뉴·SEO·alt/캡션·연결 snapshot·version/history·media usage를 분리한다. 본사는 명시적 초안 가져오기 후 직접 번역·저장·발행하며 고객 `/en`에는 영어 발행본만 제공한다. 관련 PostgreSQL 통합 55건, 관리자/고객 직접 Chromium E2E 13건, 관리자 TypeScript·고객 직접 테스트/production build가 통과했다. 직접 lint는 오류 0·경고 10건이다. Docker API 재빌드·V20 성공·health `UP`, 기존 한국어 페이지와 usage 체크섬 보존 및 미발행 영어 404/빈 공개 목록을 읽기 전용으로 확인했다. V20 구현 검증 당시에는 실제 사용자 CMS mutation이 없었고, 이후 사용자 요청에 따른 실제 발행은 위 최신 항목을 따른다. 임의 언어별 slug·SECTION 번역·번역 승인·영어 이력 복원/비교·예약/결제/AI 전체 영어 UI는 후속이다. 상세 결과와 롤백 제약은 [다국어 변경 기록](../changes/2026-09-12-cms-locales.md)을 따른다.





- Spring Boot 4.1.1 API, PostgreSQL 16.15 개발·테스트 DB, Flyway, Docker 빌드와 readiness를 구성했다. 속초 3개 객실 유형의 90일 개발 재고·요금과 3개 지점 목록, 검색 API를 구현했다.





- 예약 임시 확보 API와 조회를 구현했다. 날짜순 행 잠금으로 전 날짜 재고를 확보하고, 서버 재계산 금액·32바이트 관리 토큰 해시·멱등 키·정책 스냅샷을 저장한다.





- [첫 예약 구현 계획](../superpowers/plans/2026-09-10-first-reservation.md)의 작업 1~3을 완료했다. 실행 환경 확인 결과는 [변경 기록](../changes/2026-09-10-execution-plan.md)에 정리했다.





- 참조 프로젝트의 Reports·Accounts·APIs·Settings·Advanced → Automations 화면 확인 완료. [화면 분석과 Archify 검증 결과](../architecture/admin-reference-analysis.md)에 적용 제안과 미검증 범위를 기록했다. 구조도는 구현 전 설계다.





- 관리자 디자인 참조: `SDTPL_ADM/` 및 `http://localhost:2455/dashboard`. 2026-09-10 사용자가 열어둔 Chrome의 로그인된 대시보드를 시각 확인했다. 밝은 회색 배경·흰색 카드·얇은 테두리·둥근 모서리·큰 지표 숫자·절제된 차트 색상을 참고한다. 제공 테마를 재사용한 첫 관리자 진입 화면을 구현했으며, 시각적 모바일 검증은 아직 수행하지 않았다.





- 루트 작업 규칙과 하네스 기반 문서 진입 구조 작성.





- 첫 예약 흐름의 검토용 설계 작성.





- 제공된 SDTPL_ADM/package.json에서 Next.js 16.2.7, React 19.2.4 및 테이블·달력·차트 의존성 확인.





- 테스트 결제·만료·취소와 고객 웹 기본 흐름을 구현했다. 결제 실패 후 재시도와 세션 예약 복구도 실제 브라우저에서 확인했다. 이후 AI 대화 패널과 직원 운영 화면 연결을 추가했으며 각 영역의 상세 검증은 관련 변경 기록을 따른다.





- 관리자 4001에서 본사·속초·설악산·제주 컨텍스트를 전환하는 정적 운영 준비 화면을 구현했다. 본사에는 지점별 준비 상태를, 지점에는 도착·출발·객실 배정·청소 업무 준비 카드를 표시한다. 이는 초기 진입 화면 구현 범위이며, 이후 추가한 로그인·서버 권한·운영 API 연결은 아래 직원 접근·운영 항목을 따른다.





- 직원 접근 기반을 추가했다. 직원 비밀번호와 세션은 해시만 저장하며 본사는 전 지점, 지점 직원은 소속 지점만 서버에서 확인한다. 개발 프로필은 환경 변수로 받은 비밀번호로 본사·3개 지점 계정을 초기화한다. 관리자 루트는 로그인 화면이며, 성공한 세션은 대시보드로 이동한다. 역할별 컨텍스트 제한과 대시보드 진입 시 서버 세션 재검증을 구현했다.





- 실제 객실·객실 배정·청소 상태 기반의 직원 운영 API를 추가했다. 배정된 청결 객실만 체크인할 수 있고, 체크아웃은 객실을 청소 필요로 전환한다. 관리자 당일 운영 화면은 도착·출발·청소 필요 목록을 표시하고 실제 객실 후보 선택·배정·체크인·체크아웃·청소 완료·노쇼 처리를 실행한다. 별도 예약 관리 화면은 기준일·고객·상태로 예약을 찾고 상세에서 다객실 추가 배정과 체크인 전 배정 객실 변경을 처리한다.




- 고객 웹 CMS는 지점 랜딩의 전체 필수 스키마, 경험·오퍼 카드 순서 변경, 고객 화면 형태의 미리보기, 선택 SEO 제목·설명 편집을 지원한다. 지점 랜딩의 페이지·경로·메뉴·콘텐츠 발행 기준은 `website_page`이며 `/stays/sokcho`, `/stays/seoraksan`, `/stays/jeju`를 고객 웹 직접 경로와 공개 메뉴에 연결했다. V10~V24에서 일반·홈 페이지, 미디어 카탈로그, 페이지 수명주기·이력·미리보기·부모 이동·redirect, 한국어·영어 독립 발행과 영어 검토 역할, 저장 초안 URL 미리보기를 단계적으로 구현했다. 보관 업로드 영구 삭제, 현재 위치 파일 교체, 활성 한국어·영어 초안 사용 위치 일괄 교체, V25~V27 비동기 WebP variant와 고객 HERO·갤러리 반응형 선택도 구현했다. 기존 호텔 콘텐츠 endpoint와 원본 미디어 URL은 유지한다. CDN·객체 저장소, 언어별 임의 슬러그와 예약 발행은 후속 범위다.




- 로컬 관리자 개발 계정은 Git에서 제외한 `.env`로 초기화한다. 본사와 3개 지점 계정을 API 4080에서 실제 로그인 확인했으며, 관리자 개발 서버는 4001에서 실행 중이다. 비밀번호 원문은 문서에 기록하지 않는다.










## 검증과 환경





- 초기 예약 작업 당시 PostgreSQL 통합 테스트 11개 통과(현재 전체 테스트 집계가 아님): DB 1개, 검색 5개, 예약 5개. 예약 테스트는 마지막 객실 동시 확보, 다박 재고 롤백, 가격 변경, 멱등 재요청, 관리 토큰 접근을 검증한다. 라이브 확인은 작업 2 기준 API readiness UP, 지점 3개, 속초 검색 offer 3개다.





- 로컬 Temurin JDK `21.0.12.1` 및 Python `3.12.10` 설치 완료. java·javac·pip `25.0.1` 실행과 임시 가상환경 생성·pip 실행을 확인했다. JAVA_HOME과 Python 사용자 PATH도 등록되어 있다. 기존 IDE는 재시작해야 새 환경 변수를 반영할 수 있다.





- 확정된 개발 포트: 고객 웹 `4000`, 관리자 `4001`, API `4080`, AI `9000`. API 4080과 관리자 4001 HTTP 응답을 확인했다.




- harness에 기록된 기존 프로젝트의 도구 검증을 현재 프로젝트 검증으로 취급하지 않는다.




- V10 일반 콘텐츠 페이지의 코드 수준 검증은 서버 `WebContentIntegrationTest,WebsitePageIntegrationTest` 12건, 관리자 TypeScript 검사와 Playwright 3건, 고객 경로·문서 파서 검사와 프로덕션 빌드로 통과했다. 로컬 API `4080` readiness `UP` 후 본사 세션으로 `/brand/story`를 생성·발행(초안 v1, 발행 v2)했고, 공개 navigation·resolve의 `CONTENT_PAGE`·`hotelId: null`, 고객 웹 직접 경로의 제목·메뉴·HERO/TEXT/CTA·로컬 CTA 링크, 관리자 `4001` CMS 새로고침의 브랜드 SECTION > 브랜드 이야기 트리 항목을 확인했다.




- V11 홈페이지 CMS는 대상 서버 통합 테스트 `WebContentIntegrationTest,WebsitePageIntegrationTest` 13건(실패·오류 0), 관리자 TypeScript 검사와 Playwright 4건, 고객 콘텐츠 파서 검사와 프로덕션 빌드로 통과했다. API 컨테이너 재빌드에서 Flyway V11 적용을 확인한 뒤, 개발 API의 본사 세션으로 기존 홈 초안을 내용 변경 없이 저장·발행해 초안·발행 버전 모두 2가 되도록 검증했다. 공개 `/` resolve는 `HOME_PAGE`·`hotelId: null`을 반환하고 navigation은 루트 항목을 제외했다. 실제 in-app browser에서 고객 `4000` 홈페이지의 CMS 히어로·예약 검색/AI·본문/CTA 및 관리자 `4001` CMS 트리의 고정 홈 항목을 확인했다.




- V12 미디어 카탈로그는 대상 서버 통합 테스트 17건, 관리자 TypeScript 검사와 Playwright 7건, 고객 콘텐츠 파서 검사와 프로덕션 빌드로 통과했다. API 재빌드에서 Flyway V12·반복 데이터 migration과 `/app/media` named volume의 `hotel` 사용자 쓰기 권한을 확인했다. 공개 홈 resolve는 번들 자산 UUID와 안전한 `/images/...` 전달 경로를 반환했다. 실제 in-app browser에서 본사 CMS의 자산 1건, 초안·발행 사용 위치 10건, 미리보기·선택·페이지별 alt 입력을 확인했다. V12에 자산 삭제·보관이 없어 검증용 업로드를 공개 페이지에 저장·발행하지 않았다.




- V13 보완·V14 페이지 수명주기는 대상 서버 통합 테스트 `WebsiteMediaIntegrationTest,WebContentIntegrationTest,WebsitePageIntegrationTest` 22건, 관리자 TypeScript 검사와 CMS Playwright 15건을 통과했다. V14 Docker API 재빌드에서 Flyway 적용과 readiness `UP`을 확인했다. 활성 `/brand/story`는 `CONTENT_PAGE`, `hotelId: null`으로 resolve되고 공개 navigation에도 남아 있었다. 실제 본사 CMS에서는 기존 속초 랜딩만 읽기 전용으로 열었고, 사용자 페이지나 자산을 저장·보관·삭제하지 않았다.



- V15 발행본 콘텐츠 초안 복원은 `WebsitePageIntegrationTest` 12건, 관리자 TypeScript 검사, CMS Playwright 전체 16건을 통과했다. 현재 저장 초안 대체 경고를 추가한 뒤 복원 대상 E2E 1건과 TypeScript 검사를 다시 통과했다. 테스트는 과거 콘텐츠의 초안 복원, 현재 공개본 유지, 재발행 뒤 공개 반영, 초안 미디어 usage·audit, 0·현재 발행본·없는 version, 권한·보관 거부를 확인한다. Docker API 재빌드에서 Flyway V15와 readiness `UP`, 기존 `/brand/story` 공개 resolve를 읽기 전용으로 확인했다. 실제 CMS 저장·복원·발행은 하지 않았다.





- V16 발행본 비교는 `WebsitePageIntegrationTest` 13건, 관리자 TypeScript 검사, 비교 대상 Chromium Playwright 1건을 통과했다. 테스트는 두 snapshot의 metadata·content·발행 시각, 현재 초안·공개본·수명주기·audit·media usage 불변성, 보관 페이지·권한·잘못된 version·다른 페이지 version·손상 snapshot 거부와 UI의 version 선택·닫기·Escape·포커스 복귀·변경 요청 부재를 확인한다. Docker API 재빌드가 성공했고 4080 health `UP`과 기존 `/brand/story` 공개 resolve를 읽기 전용으로 확인했다. 실제 사용자 페이지의 저장·발행·복원·비교 요청은 보내지 않았다.

- V17 일반 페이지 초안 미리보기는 대상 Chromium Playwright 1건과 관리자 TypeScript 검사를 통과했다. 저장하지 않은 HERO와 TEXT 입력, 안전한 내장 이미지의 고객 웹 origin 경로, 닫기·Escape 뒤 포커스 복귀, staff website PUT/POST 부재를 확인했다. 관리자 전용 읽기 UI 변경이므로 API 재빌드나 실제 사용자 페이지 저장·발행은 수행하지 않았다.

- V18 일반 페이지 영구 삭제는 `WebsitePageIntegrationTest` 14건, 대상 Chromium Playwright 1건, 관리자 TypeScript 검사를 통과했다. 보관된 page의 page-scoped version/audit/media usage와 parent row 삭제, 자산 유지, 활성·오래된 version·SECTION·지점 권한 거부, 확인 dialog·DELETE body·홈 전환·트리 제거를 확인했다. Docker API 재빌드, 4080 health `UP`, 기존 `/brand/story` 공개 resolve와 유효하지 않은 세션 DELETE의 401도 확인했다. 실제 사용자 페이지에는 보관·삭제 요청을 보내지 않았다.





## 다음 작업

1. Toss `GET /v1/settlements` 기반 정산·대사의 본사 읽기 전용 화면과 정산·대사의 토스 test 환경 지원, 본사 정산 실행 생성·재시도를 완료했다. 다음은 실제 라이브 거래가 자료에 나타난 뒤 외부 대사를 검증하는 것이다.

2. 저장 초안 미리보기의 만료 시간 경과 검증을 완료했다. 만료 시간을 `website.preview.ttl-minutes`로 조정 가능하게 하고 실제 시간 경과로 410 전환을 확인했다. 다음은 영구 E2E suite와 관리자 UI에서 만료 시간 노출 여부를 정하는 것이다.

3. AI 도우미의 정책 임베딩을 코드로 두고 고객 대화 E2E suite를 추가했으며, Gemini LLM을 연결해 정규식 불가 표현을 해석하게 했다. 정책 위반 400을 고객 웹에 구분해 안내하고 LLM 결과·소요 시간을 INFO 로그로 올려 운영 로그에서 지속 수집되게 했다. `/metrics/llm`과 본사 `AI 운영` 메뉴로 결과별 호출 수·평균 지연을 읽기 전용으로 확인하게 했다. 다음은 측정을 영구 보관·시계열 집계하고 정책 위반 건수도 노출하는 것이다.

4. Playwright 전체 회귀의 실패 5건(toss SDK 로딩·세션 타이밍) 원인을 제거한다.

5. `admin-menu-roadmap.md` 8번 `AI 운영` 1차, 1번 `지점·객실 유형 관리` 읽기 전용, 2번 `재고·가격` 읽기 전용을 완료했다. 다음은 1·2번의 쓰기 동작(지점 생성·수정·판매 중지, 요금제·일자별 요금 변경, 재고 조정·판매 중지 구간)이다.

- 2026-09-11: 사용자는 롯데리조트 속초 수준의 객실·다이닝·부대시설·프로모션 운영을 CMS 목표로 재확인했다. 구현을 추가하기 전에 콘텐츠 유형·트리·블록·도메인 연결·번역·검토·발행·SEO를 다시 정의했고, [설계 문서](../architecture/lotte-resort-level-cms-functional-design.md)와 [목표 구조도](../architecture/lotte-resort-cms-target.html)를 작성했다. 이후 구현은 이 설계를 기준으로 기능 단위 계획을 작성한 뒤 진행한다.










## 관련 문서





- [프로젝트 개요](project-brief.md)





- [전체 사이트 구현 설계서](../architecture/full-site-implementation-design.md)

- [직원 예약 취소 변경 기록](../changes/2026-09-13-staff-reservation-cancellation.md)

- [직원 예약자 정보 정정 변경 기록](../changes/2026-09-13-staff-reservation-guest-update.md)





- [의사결정](../decisions/decision-log.md)





- [첫 예약 설계](../superpowers/specs/2026-09-10-reservation-foundation-design.md)











## 최신 고객 웹 검증





- 고객 웹 빌드 성공. 실제 브라우저 검색 → 확보 → 테스트 결제 → 새로고침 조회 → 취소 완료 확인.





- 상세 결과와 미완료 항목: [고객 예약 변경 기록](../changes/2026-09-10-customer-booking.md).





- 관리자 진입 화면 구현·검증·미연결 범위: [관리자 대시보드 변경 기록](../changes/2026-09-10-admin-entry-dashboard.md).





- 직원 접근 기반 구현·검증·다음 작업: [직원 접근 변경 기록](../changes/2026-09-10-staff-access.md).





- 직원 객실 운영 API 구현·검증·다음 작업: [직원 운영 변경 기록](../changes/2026-09-10-staff-operations.md).











- 고객 웹을 리조트 경험 중심으로 업그레이드했다. 속초·설악산·제주도별 히어로·경험·오퍼·현장 안내 콘텐츠를 제공하고, 기존 실제 재고·요금·예약 흐름은 유지한다. 고객 웹 빌드와 데스크톱·390×844 화면, API readiness·호텔 목록·속초 검색 결과·설악산 콘텐츠 전환을 확인했다. 이번 화면 변경 뒤 테스트 결제·취소 전체 재검증은 남아 있다.





- LangGraph 예약 도우미를 9000 포트의 FastAPI 서비스로 추가했다. 현재는 정해진 한국어 조건 패턴으로 동작하며, Spring Boot 검색 API의 실제 객실·가격·잔여 수량만 반환한다. 고객 웹은 현재 예약 조건을 `/chat`에 보내고, AI 적용 시 Spring availability를 직접 다시 조회해 그 결과만 카드에 사용한다. 늦게 도착한 이전 검색 응답은 요청 순서 보호로 무효화한다. 실제 `/chat`·Spring 응답 비교와 headless 고객 웹의 대화→적용→재검색을 예약 생성 없이 확인했다. LLM·정책 임베딩과 영구 고객 대화 E2E suite는 후속 작업이다.











- 고객 웹 CMS의 지점 랜딩 기반은 초안·발행본·버전·감사 로그 및 본사 관리 API와 3개 지점 초기 콘텐츠 이관으로 구성했다. 관리자 편집기는 미저장 발행 방지·지점 전환 경고·전체 랜딩 스키마·카드 순서 변경·미리보기·선택 SEO 편집을 제공한다. 실제 HTTP에서 본사 저장·발행·공개 조회·감사 로그, 지점 조회·저장·발행 403, 오래된 저장·발행 409와 SEO 400/저장/발행을 확인했고, 고객 웹의 브라우저 제목·설명 메타도 확인했다. 후속 V9~V11의 실제 관리자·고객 브라우저 검증 결과는 아래 기록과 [CMS 변경 기록](../changes/2026-09-10-web-content-management.md)을 따른다.




- 고객 웹 CMS 페이지 트리 기반을 추가했다. V9은 기존 3개 지점 랜딩을 페이지·경로·메뉴·콘텐츠 발행 문서로 이관하고, 개발 시드는 새 DB에도 이를 보장한다. 관리자에는 실제 페이지 트리와 슬러그·메뉴 설정이 표시되며, 고객은 `/stays/:slug`을 공개 resolve API로 읽는다. API readiness, 공개 메뉴 3개 지점, 속초 resolve, 본사 트리와 지점 직원 403, 고객 직접 경로, 서버 통합 테스트 8건과 관리자 E2E 1건을 확인했다. 실제 로그인 UI의 변경값 발행은 남아 있다.




- V10은 일반 콘텐츠 페이지를 `SECTION`의 직계 자식으로 제한해 `CONTENT_PAGE.hotelId = null`을 보장한다. 본사만 page ID 기반 생성·조회·저장·발행·버전 조회를 할 수 있으며, 문서는 `seo` 및 단일 `HERO`로 시작하는 `HERO`·`TEXT`·`CTA` 블록만 허용한다. 고객 웹은 `/brand/story` 같은 최대 두 단계의 공개 경로에서 allowlist 렌더러를 사용한다. 실제 본사 세션으로 `/brand/story`를 생성·발행하고 공개 메뉴·resolve, 고객 직접 경로, 관리자 CMS 트리까지 확인했다.




- V11은 `HOME_PAGE`를 `website_page`의 단일 고정 루트 페이지로 추가했다. `hotelId`와 부모는 `null`, 경로는 `/`, 메뉴 노출은 `false`이고 홈 전용 본사 API만 초안 저장·발행·버전 조회를 처리한다. 일반 page publish API는 HOME_PAGE를 거부한다. 고객 홈은 CMS 히어로 뒤에 코드 소유 예약 검색·AI를 두고 CMS 본문/CTA와 기존 지점 동적 영역을 이어서 표시한다. 실제 본사 저장·발행, 공개 resolve·navigation 제외, 고객 홈과 관리자 홈 트리를 확인했다.




- V12는 이미지 파일을 고객 CMS 문서의 자유 경로가 아니라 카탈로그 자산 UUID로 관리한다. 본사만 업로드·목록·사용 위치를 조회하고, 서버가 실제 PNG/JPEG 형식과 한도를 검사한 뒤 UUID 공개 경로를 문서에 넣는다. V13은 본사 자산명·기본 alt의 버전 기반 저장, 참조 없는 자산만 가능한 보관·복원, 보관 업로드의 공개 전달 차단과 재검증 캐시를 추가했다. V14는 일반 페이지를 보관해 공개를 중단하고 초안으로 복원하는 상태 전환을 추가했다. Docker named volume은 파일을 이미지 재생성 뒤에도 유지한다. 영구 삭제·현재 위치 파일 교체·활성 초안 사용 위치 일괄 교체, V25~V27 비동기 WebP variant와 고객 HERO·갤러리 선택 연결까지 구현했다.

- V16 통합 리조트 콘텐츠 모델은 콘텐츠 종류별 블록과 초안·발행 연결(객실 유형·대상 지점·관련 페이지)을 추가했다. `WebsitePageIntegrationTest` 22건, 고객 파서·예약 의도·갤러리 검사와 production build, 관리자 Chromium E2E 4건·TypeScript 검사를 통과했다. API 재빌드 뒤 health `UP` 및 기존 `/brand/story` 공개 resolve를 읽기 전용으로 확인했으며, 실제 사용자 페이지·미디어에 저장·발행·수명주기 요청을 보내지 않았다. 상세 범위와 미구현 항목은 [변경 기록](../changes/2026-09-12-unified-resort-content-model.md)을 따른다.

- V17은 미발행 콘텐츠 페이지의 부모 이동과 최대 4단계 트리를 추가했다. 본사 impact 조회 뒤에만 이동을 실행하며, 이동은 하위 초안 경로·draft version·audit만 갱신한다. 발행된 root 또는 하위 페이지는 redirect 정책 전까지 서버에서 거부한다. 서버 통합 23건, 관리자 이동 E2E 1건, TypeScript 검사, Docker API 재빌드와 health `UP`, 기존 `/brand/story` 읽기 전용 resolve를 확인했다. 실제 사용자 CMS 페이지·자산에는 이동 요청을 보내지 않았다. 상세 결과는 [부모 이동 변경 기록](../changes/2026-09-12-content-page-parent-move.md)을 따른다.

- V18은 발행 콘텐츠 페이지 이동 때 이전 공개 경로를 영구 301 redirect로 보존한다. 서버는 root·하위 published path와 snapshot 메타데이터, redirect row, audit을 transaction에서 함께 갱신하고 redirect chain·cycle을 거부한다. 공개 resolve는 현재 공개본 우선, 없을 때 한 번만 301을 반환한다. 관리자는 impact에서 301 경로를 확인하고 발행 버전을 포함해 이동한다. `WebsitePageIntegrationTest` 26건, 발행/미발행 이동 Chromium E2E 2건과 TypeScript 검사를 통과했다. Docker API 재빌드의 Flyway V18 적용과 health `UP`, 기존 `/brand/story` 공개 resolve를 확인했으며 실제 사용자 CMS 변경 요청은 보내지 않았다. 사용자 페이지 이동이 없으므로 runtime old-path 301/new-path resolve 쌍은 서버 통합 테스트에서만 확인했다. 상세 결과는 [redirect 변경 기록](../changes/2026-09-12-content-page-redirect.md)을 따른다.

- 관리자 사이드바를 현재 동작하는 호텔 운영 기능 중심으로 정리했다. 본사에는 운영 대시보드·오늘의 운영·웹사이트 CMS를, 지점 직원에게는 운영 메뉴만 표시하며 메뉴 검색에도 같은 권한 필터를 적용한다. 템플릿 샘플 메뉴는 제거했고, 아직 화면이 없는 메뉴는 추가하지 않았다. 관리자 내비게이션 Playwright 2건과 TypeScript 검사를 통과했으며 데스크톱과 390×844 모바일 사이드바를 확인했다. 상세 결과는 [관리자 내비게이션 변경 기록](../changes/2026-09-12-admin-navigation.md)을 따른다.
- 제공된 관리자 템플릿의 전체 메뉴 정의는 `SDTPL_ADM/src/lib/template-nav.ts`로 분리해 보존했다. 현재 역할별 운영 메뉴에는 연결하지 않고, 기능을 실제 구현할 때 기존 템플릿 화면·공통 컴포넌트와 함께 선택적으로 재사용한다.
- V19는 참조 없는 보관 업로드 미디어를 보관 후 30일이 지난 경우에만 영구 삭제한다. 본사 전용 DELETE API는 자산 버전·출처·상태·사용 위치·유예 기간을 확인하고, 파일을 같은 볼륨에 격리한 뒤 DB 트랜잭션 롤백 시 복구·커밋 시 제거한다. 관리자는 삭제 가능일과 복구 불가 확인을 거쳐 실행한다. Docker 환경의 V19 적용과 API `4080` 포트 헬스 `UP`, 실제 관리자 화면의 활성 자산 6건 조회를 확인했으며 사용자 자산에는 삭제 요청을 보내지 않았다. 상세 범위는 [변경 기록](../changes/2026-09-12-media-permanent-delete.md)을 따른다.
- 미디어 파일 교체는 공통 이미지 필드에서 새 PNG/JPEG 자산을 업로드하고 기존·새 이미지 확인 뒤 현재 편집 위치만 교체한다. 페이지별 alt·기존 파일·다른 위치와 페이지·발행 이력은 유지하며 초안 저장과 명시적 발행으로만 공개를 바꾼다. 기존 본사 업로드·페이지 API를 재사용해 migration·파일 덮어쓰기는 없다. 관리자 직접 E2E 13건, 미디어 서버 통합 15건, TypeScript 검사를 통과했다. 데스크톱·390×844 확인창과 로컬 API health `UP`·기존 페이지 공개 resolve를 확인했으며 실제 사용자 CMS mutation은 하지 않았다. 직접 lint는 오류 0·경고 12건이다. 상세 범위와 미검증 항목은 [파일 교체 변경 기록](../changes/2026-09-12-media-file-replacement.md)을 따른다.
- 미디어 초안 사용 위치 일괄 교체는 `HQ_ADMIN`이 새 업로드 자산과 영향 목록을 확인한 뒤 활성 한국어·영어 초안만 한 트랜잭션에서 바꾼다. 자산 UUID·전달 URL 외의 페이지별 alt·caption·블록 순서는 유지하고, 공개본·발행 usage·snapshot·보관 페이지·기존 자산과 파일은 보존한다. 영어 검토·승인은 일반 저장 규칙대로 무효화한다. `WebsiteMediaIntegrationTest` 19건과 관리자 Chromium E2E 4건, TypeScript 검사를 통과했다. 실제 사용자 CMS mutation, 전체 suite, 고객 예약 회귀는 실행하지 않았다. 상세 범위는 [변경 기록](../changes/2026-09-13-media-draft-usage-bulk-replacement.md)을 따른다.
- CMS 미디어 저장소를 provider 중립 계약으로 분리하고 `local`, `mirror`, `s3-primary` 모드를 구현했다. 모든 모드가 로컬 사본과 기존 공개 URL을 유지하며, `mirror`는 명시적 100개 단위 S3 backfill을 제공하고 `s3-primary`는 S3 오류 때 로컬 fallback을 계측한다. 격리 기반 영구 삭제의 commit·rollback도 양쪽 저장소에 적용한다. 격리된 Compose 환경에서 실제 업로드·WebP 생성, backfill, S3 우선 읽기·강제 fallback, local 롤백을 확인했고 서버 119건과 관리자 E2E 8건이 통과했다. 운영 provider·CDN 연동은 아직 검증하지 않았다. 상세 결과는 [S3 미디어 저장소 이관 기록](../changes/2026-09-13-s3-media-storage-migration.md)을 따른다.

- 2026-09-15: 고객이 예약 상세에서 날짜·객실·요금제·성인·아동을 직접 재견적하고 차액 결제·환불·0원 변경을 시작하는 셀프서비스 흐름을 추가했다. 서버가 관리 토큰, 변경 가능 조건, 최신 재고와 가격을 잠금 상태에서 재검증하며 고객 actor와 변경 전후 인원을 감사한다. 설계는 [고객 직접 예약 변경 설계](../superpowers/specs/2026-09-15-customer-self-service-reservation-change-design.md), 실행 단계는 [구현 계획](../superpowers/plans/2026-09-15-customer-self-service-reservation-change.md), 자동 검증과 사용자 브라우저 체크리스트는 [변경 기록](../changes/2026-09-15-customer-self-service-reservation-change.md)을 따른다. 브라우저와 실제 Toss 운영 결제 검증은 사용자가 수행한다.

- 2026-09-15: Toss 운영 결제 1단계로 test/live 키·provider snapshot을 분리하고 라이브 신규 checkout kill switch, 고객 웹 라이브 위젯 경로, provider별 webhook 수신·재조회·중복 제거와 애플리케이션 속도 제한을 추가했다. 실제 키·외부 webhook·과금은 열지 않았다. 구현·검증 범위와 배포 전 확인은 [변경 기록](../changes/2026-09-15-toss-live-payment-core.md)을 따른다. 다음 단계는 정산 snapshot·대사·본사 읽기 전용 화면이다.
