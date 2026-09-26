# 서버 배포 및 Cloudflare 터널 연결 - 배포 차단 결함 수정

> 날짜: 2026-09-26
> 작업 트리에만 있고 커밋하지 않은 상태.

## 배경

`infra/` 배포 구성(compose·Dockerfile·스크립트·문서)은 이미 거의 완성돼
있었고, 서버 배포와 Cloudflare 터널 연결 작업을 진행 중이었다. 배포를
실행하기 전에 구성을 읽어 확인했을 때 **첫 기동을 깨거나 화면 하나를
통째로 쓸 수 없게 만드는 결함 4가지**가 있었다. 모두 코드가 아니라
"어느 환경에서 무엇을 가리키는가"에 관한 것이었다.

## 변경 내용

### 1. dev 프로필 시드가 속초 지점만 심었다 (고객 웹 결함)

`services/api/src/main/resources/db/dev/R__demo_data.sql`은 3개 지점
행은 만들었지만 **객실 유형·요금제·일자 요금·재고를 속초 3개 유형만**
심었다. `AvailabilityService.search()`가 `room_type → rate_plan →
rate_day + inventory_day`를 모두 join해야 오퍼를 내주므로, 설악산·제주를
선택하고 검색하면 **오퍼가 한 건도 나오지 않았다**. `R__web_content_data.sql`은
3개 지점 모두 콘텐츠를 심었으므로 지점 선택기는 세 지점을 다 보여줬고,
이 둘의 불일치가 "들어갈 수는 있는데 예약할 수는 없는" 상태를 만들었다.

- 설악산 3개 유형(`2100…011~013`)·제주 3개 유형(`2100…021~023`)과 각
  요금제·90일분 일자 요금·재고를 추가했다. 속초 3개 유형은 그대로 둬서
  **기존 로컬 DB의 속초 행이 영향을 받지 않는다.**
- 모든 insert에 `ON CONFLICT … DO NOTHING`을 유지했으므로 이미 심어진
  로컬·서버 DB에서는 아무것도 바꾸지 않고, 첫 기동하는 DB에만 3개
  지점이 모두 판매된다.
- 이 파일은 `classpath:db/dev`이고 `spring.flyway.locations`가 dev
  프로필에서만 이 경로를 추가한다. **테스트는 기본 프로필을 쓰므로
  영향을 받지 않는다.** `application-test.yml`에 flyway locations
  override가 없는 것을 확인했다.

### 2. 관리자 `/concierge` rewrite가 컨테이너 자신을 가리켰다 (배포 차단)

`SDTPL_ADM/next.config.ts`의 rewrite는 `CONCIERGE_PROXY_TARGET`가
정의되지 않으면 `http://127.0.0.1:9000`을 기본값으로 썼다. **컨테이너
안에서 `127.0.0.1:9000`은 관리자 컨테이너 자신**이고, 거기에는 도우미가
없다. 결과적으로 AI 운영 메뉴를 열면 연결 거부가 났다. `DEPLOY-ZORIN.md`는
"도우미는 이 배포에 포함하지 않는다"고 쓰여 있었지만 구성은 도우미가
있다고 가정하고 있었다.

- `CONCIERGE_PROXY_TARGET`이 빈 문자열이면 **rewrite 자체를 만들지
  않는다**. `/concierge/*`가 404로 떨어지고, 빈 응답을 기다리지 않는다.
- `compose.zorin.yml`의 admin build args에
  `CONCIERGE_PROXY_TARGET: ${CONCIERGE_PROXY_TARGET-}`을 추가했다.
  변수가 정의되지 않으면 **빈 값**이 전달된다 (`:-`가 아니라 `-`를
  써야 `compose.env`에 빈 값을 뒀을 때 빈 값이 그대로 간다).
- `SDTPL_ADM/infra/Dockerfile`에 `ARG/ENV CONCIERGE_PROXY_TARGET`을
  추가했다.
- `compose.env.example`에 `CONCIERGE_PROXY_TARGET=`와
  `GOOGLE_API_KEY=`를 추가하고, 도우미를 넣는 방법을 적었다.
- `staff-api.ts`의 `getConciergeLlmMetrics()`가 404를 만나면
  "AI 도우미가 실행 중이 아닙니다. 이 배포에는 도우미가 포함되지
  않았습니다."를 보여준다. 502는 여전히 일반 장애 안내로 둬서
  도우미가 있을 때의 동작을 보존했다.
- `e2e/ai-operations.spec.ts`에 404 케이스를 추가했다.

### 3. `NEXT_PUBLIC_CUSTOMER_WEB_ORIGIN`이 런타임 environment에만 있었다

`compose.zorin.yml`은 admin 서비스의 `environment:`에
`NEXT_PUBLIC_CUSTOMER_WEB_ORIGIN`을 넣었지만, **`NEXT_PUBLIC_*` 변수는
Next.js 빌드 시점에 클라이언트 번들에 구워진다.** 런타임 환경변수로는
아무것도 바꾸지 않으므로, 배포된 관리자의 미디어 미리보기·콘텐츠
미리보기·저장 초안 링크가 전부 **로컬 개발 주소 `http://127.0.0.1:4000`**을
가리킬 뻔했다. `media-field.tsx`·`media-picker-dialog.tsx`·
`content-page-preview-dialog.tsx`·`website-content-editor.tsx`·
`website-saved-draft-preview-action.tsx` 5곳이 이 변수로 URL을 만든다.

- admin Dockerfile에
  `ARG/ENV NEXT_PUBLIC_CUSTOMER_WEB_ORIGIN=http://127.0.0.1:4000`을
  추가했다. 로컬 개발 기본값은 그대로 유지된다.
- compose build args에
  `NEXT_PUBLIC_CUSTOMER_WEB_ORIGIN: ${CUSTOMER_WEB_ORIGIN:-https://hcm.approid.team}`을
  추가해서 배포 주소가 빌드에 구워지게 했다. 런타임 `environment:`의
  같은 변수는 노드 서버 프로세스용으로 그대로 뒀다.

### 4. DEPLOY-ZORIN.md에 검증 상태와 한계를 기록

문서 끝에 "검증한 배포 상태" 표를 추가했다. compose 파일을
`docker compose config`로 검증하지 않았고, 서버에서 `up`하지도
않았다는 것을 적었다. 실행한 것은 읽기 확인(마이그레이션 순서·
additive 여부·파일 인코딩)과 로컬 타입·테스트 검증뿐이다.

## 검증 결과

- **인프라 파일 인코딩**: `apps/web/infra/Dockerfile`·
  `apps/web/infra/nginx.conf`·`SDTPL_ADM/infra/Dockerfile`·
  `infra/compose.zorin.yml`·`infra/DEPLOY-ZORIN.md`·스크립트 4개·
  예제 env 2개는 전부 UTF-8 BOM 없음, 잘못된 UTF-8 시퀀스 0건이다.
  터미널 코드 페이지로 인해 콘솔에서만 깨져 보였다. `deploy-zorin.ps1`만
  UTF-8 BOM이 있고 이것은 PowerShell 스크립트로 올바른 상태다.
- **Flyway 마이그레이션**: 64개 파일이 전부 UTF-8 BOM 없음. V58(고객
  요청)·V60(지점 active)·V63(판매 중지) 등은 기존 표를 변경하지 않는
  additive 마이그레이션이다. `V1__baseline.sql`은 `schema_marker`만
  만든다. V42·V64가 `ALTER … DROP NOT NULL`을 쓰지만 앞선 마이그레이션이
  만든 칼럼에 대한 것이므로 순서가 맞다.
- **rewrite 동작**: `CONCIERGE_PROXY_TARGET=""` → 빈 배열(rewrite 없음),
  정의 안 함 → `http://127.0.0.1:9000`(로컬 개발, 기존 동작 보존),
  `http://concierge:9000` → 해당 rewrite. node로 직접 확인했다.
- **seed SQL**: `room_type`·`rate_plan`·`rate_day`·`inventory_day`의
  칼럼 순서와 NOT NULL 제약을 `V2__hotel_inventory.sql`과 대조해
  유효함을 확인했다.
- **관리자**: `tsc --noEmit` 종료 코드 0, `eslint src/lib/staff-api.ts
  e2e/ai-operations.spec.ts` 종료 코드 0 (경고 0건).
- **고객 웹**: `tsc --noEmit` 종료 코드 0, `tsx` 검증 스크립트
  **25건** 전부 종료 코드 0.
- **API** (`mvnw`, 테스트 DB `localhost:55433`):
  - `WebsiteMediaIntegrationTest` **26건** 종료 코드 0.
  - `AvailabilityIntegrationTest`·`HotelCatalogQueryIntegrationTest`·
    `InventoryQueryIntegrationTest` **22건** 종료 코드 0.
  - `GuestRequestIntegrationTest` **22건** 종료 코드 0.
- **Cloudflare 터널 비밀값**: `.gitignore`가
  `infra/secrets/tunnel.env`·`infra/secrets/compose.env`·
  `infra/secrets/*.env`를 무시하고 `!*.env.example`로 예제는 살려둔다.
  `git check-ignore`이 두 파일 모두 무시됨을 확인했다.

## 미검증 항목

- **서버에서 `docker compose up`을 실행하지 않았다.** compose 파일의
  유효성을 `docker compose config`로 확인하지도 않았다. YAML 구문과
  변수 보간만 읽어서 확인했다. 사용자가 `-Push -Up`으로 실제 기동해야
  한다.
- **컨테이너 빌드를 실행하지 않았다.** `docker build`가 한 번도
  돌지 않았으므로 admin Dockerfile의 새 build arg가 실제로
  `next.config.ts`·`NEXT_PUBLIC_*`에 구워지는지는 빌드해야 알 수 있다.
- **라이브 DB·터널·브라우저를 확인하지 않았다.**
  `getent hosts hcm.approid.team`, Cloudflare 대시보드의 Public Hostname
  등록, `verify-deployment.sh`의 터널 경유 검사는 서버에서 사용자가
  실행한다.
- **터널의 Public Hostname 순서는 Cloudflare 대시보드에 있다.**
  `/api/*`가 전체 경로보다 먼저 매칭돼야 한다는 것은 문서에 적혀 있지만
  이 작업에서는 대시보드를 보지 않았다.
- **dev 프로필 시드의 3개 지점 분량은 DB에서 실행하지 않았다.**
  `ON CONFLICT DO NOTHING`이므로 첫 기동하는 DB에만 적용되고, 기존
  로컬 DB의 속초 행은 바뀌지 않는다.
- **고객 웹 Playwright(`apps/web/test/*.spec.ts`)는 실행하지 않았다.**
  이 spec들은 사용자가 미리 띄운 고객 웹 서버를 쓴다. 내가 변경한
  파일이 고객 웹의 동작을 바꾸지 않으므로 범위 밖이다.

## 다음 작업

1. 서버에서 `.\infra\scripts\deploy-zorin.ps1 -Push -Up`을 실행해
   첫 기동한다. `--wait`을 쓰면 헬스체크가 통과할 때까지 기다린다.
2. `./infra/scripts/verify-deployment.sh`로 로컬 헬스체크와 터널 경유
   도메인을 검사한다.
3. `compose.env`에서 `STAFF_DEV_ENABLED=true`와 직원 비밀번호를 채우고
   기동하면 개발용 직원 계정이 만들어진다. 비밀번호는 **첫 기동 이후에는
   바꿔도 이미 만들어진 계정에 영향을 주지 않는다.**
4. CMS 메뉴에서 홈페이지·콘텐츠 페이지를 만들지 않으면 고객 웹이
   빈 페이지로 보일 수 있다. 단 3개 지점의 객실 유형·요금·재고와
   랜딩 콘텐츠는 이제 시드된다.
