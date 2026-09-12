# 홈페이지 CMS 구현 계획

## 목표

본사 관리자가 홈페이지의 SEO, 히어로, 본문, CTA를 초안으로 저장하고 발행한다. 고객 홈페이지는 발행된 콘텐츠만 표시하되, 객실 검색, 날짜·인원 선택, AI 예약 도우미, 재고·가격 조회와 예약 확정은 현재 React·Spring 예약 흐름이 계속 소유한다.

## 범위와 결정

- HOME_PAGE를 website_page의 단일 고정 유형으로 추가한다. CONTENT_PAGE를 루트까지 확장하지 않는다. 일반 페이지는 계속 SECTION의 직계 자식만 허용한다.
- 홈은 hotel_id와 parent_id가 모두 NULL이며, slug는 home, 경로는 /, 메뉴 노출은 false로 고정한다. 홈을 navigation 응답에 넣지 않아 브랜드 링크와 중복시키지 않는다.
- 콘텐츠 계약은 기존 ContentPageValidator의 seo + HERO/TEXT/CTA allowlist를 재사용한다. HTML, 원격 URL, 원격 이미지, 가격, 재고, 객실 ID, 고객·예약 정보는 허용하지 않는다.
- 고객 화면의 순서는 CMS HERO → 코드 소유 예약 검색·AI → CMS TEXT/CTA → 현재 지점 경험·오퍼·객실·도착 안내 흐름으로 둔다. 이번 범위에서 지점 경험·오퍼·도착 안내의 소유권은 바꾸지 않는다.
- 본사 API는 홈 전용 read/save/publish/versions 경로를 제공한다. 홈의 슬러그, 경로, 메뉴 설정은 API와 UI 어느 곳에서도 수정하지 않는다.
- 페이지 삭제, 이동, 중첩, 다국어, 예약된 발행, 미디어 업로드, 복원은 이 증분에 넣지 않는다.

## 완료 기준

1. [x] Flyway V11이 한 개의 발행된 HOME_PAGE와 버전 1 스냅샷을 만든다.
2. [x] HQ_ADMIN은 홈을 조회·초안 저장·발행·버전 조회할 수 있고, 지점 직원은 모두 403이다.
3. [x] 저장한 초안은 공개 / 응답과 고객 화면에 보이지 않으며, 발행 뒤에만 보인다.
4. [x] 공개 resolve('/')는 HOME_PAGE, hotelId: null, 구조화 콘텐츠를 반환하고 공개 navigation에는 홈을 포함하지 않는다.
5. [x] 관리자 트리에는 고정 홈 항목이 있고, 구조화 편집기로 콘텐츠만 저장·발행할 수 있다.
6. [x] 고객 루트는 발행된 홈 HERO와 나머지 블록을 렌더링하면서 실제 호텔 목록·재고 검색 흐름을 유지한다.

## 작업 순서

### Task 1: 서버 계약과 마이그레이션 — 완료

대상 파일:

- Create: services/api/src/main/resources/db/migration/V11__home_page.sql
- Create: services/api/src/main/java/team/hotelchain/webcontent/SaveHomePageRequest.java
- Modify: services/api/src/main/java/team/hotelchain/webcontent/WebsitePageService.java
- Modify: services/api/src/main/java/team/hotelchain/webcontent/WebsitePageManagementController.java
- Modify: services/api/src/test/java/team/hotelchain/webcontent/WebsitePageIntegrationTest.java

1. 먼저 HOME_PAGE의 공개·초안 분리, HQ 권한, 버전, navigation 제외를 검사하는 실패 통합 테스트를 추가한다.
2. V10의 type/owner 제약과 draft/published path 제약을 교체해 HOME_PAGE와 루트 경로를 허용한다. 기존 PATH 규칙은 그대로 두고 /만 추가한다.
3. HOME_PAGE partial unique index와 안정적인 UUID를 추가한다. 기본 문서는 현재 고객 홈페이지의 브랜드 메시지를 사용하고 HERO, TEXT, CTA만 포함한다. 초기 발행 버전 1 snapshot과 MIGRATED audit도 함께 만든다.
4. WebsitePageService에 homeDraft, saveHomeDraft, publishHome, homeVersions를 추가한다. 저장은 고정 metadata와 기존 ContentPageValidator를 사용하고, 기존 일반 page publish endpoint로 HOME_PAGE를 우회 발행할 수 없게 한다.
5. resolvePublished는 /와 HOME_PAGE를 허용하고, navigation과 지점 랜딩 호환 계약은 유지한다.

### Task 2: 관리자 홈 편집 흐름 — 완료

대상 파일:

- Modify: SDTPL_ADM/src/lib/staff-api.ts
- Modify: SDTPL_ADM/src/components/hotel-admin/website-page-tree.tsx
- Modify: SDTPL_ADM/src/components/hotel-admin/content-page-editor.tsx
- Modify: SDTPL_ADM/src/components/hotel-admin/website-content-editor.tsx
- Modify: SDTPL_ADM/e2e/website-content-editor.spec.ts

1. staff-api에 홈 전용 API 타입과 호출을 추가한다. WebsitePageDocument pageType union에 HOME_PAGE를 반영한다.
2. 페이지 트리는 SECTION 목록 위에 고정된 홈 leaf를 표시한다. 새 일반 페이지 생성 dialog의 상위 후보에는 넣지 않는다.
3. ContentPageEditor는 홈일 때 페이지 정보 편집을 숨기고, SEO와 HERO/TEXT/CTA 폼 및 저장·발행 흐름을 동일하게 제공한다.
4. WebsiteContentEditor는 홈 선택, 초기 로드, 미저장 이동 경고, 저장 뒤 tree/versions 갱신을 page ID가 아닌 홈 전용 API로 연결한다. 기존 호텔 랜딩과 CONTENT_PAGE의 흐름을 바꾸지 않는다.
5. Playwright는 홈 선택, 수정 뒤 발행 비활성화, 홈 save/publish 요청 본문, 다른 페이지 전환 시 더티 경고를 검증한다.

### Task 3: 고객 루트 렌더링 — 완료

대상 파일:

- Modify: apps/web/src/lib/api.ts
- Modify: apps/web/src/components/content-page.tsx
- Modify: apps/web/src/App.tsx
- Modify: apps/web/src/lib/content-page.test.ts

1. 공개 page type union에 HOME_PAGE를 추가하고, 고객 route의 home 분기에서 api.websitePage('/')를 조회한다.
2. 응답이 HOME_PAGE와 hotelId null이고 문서 파서 검증을 통과할 때만 홈 CMS 상태에 적용한다. 조회 실패 또는 불량 문서는 기존 정적 홈 hero fallback을 유지한다.
3. ContentPage에서 HERO 뒤 블록만 렌더할 수 있는 재사용 가능한 renderer를 추출한다. App은 CMS HERO 뒤에 기존 예약 검색 shell을 두고 나머지 CMS blocks를 이어서 렌더한다.
4. 발행본 SEO를 문서 title과 description meta에 반영한다. 예약 search, selectHotelForStay, availability 호출과 customer page 라우팅은 변경하지 않는다.

### Task 4: 검증과 문서 — 완료

대상 파일:

- Modify: docs/changes/2026-09-10-web-content-management.md
- Modify: docs/overview/current-development-context.md
- Modify: docs/architecture/full-site-implementation-design.md
- Modify: docs/decisions/decision-log.md
- Modify: docs/superpowers/plans/2026-09-11-home-page-cms.md

1. 대상 Spring 통합 테스트, 관리자 TypeScript 검사와 CMS Playwright, 고객 파서 검사와 production build만 실행한다. 전체 테스트 재실행은 하지 않는다.
2. API 컨테이너를 한 번 재빌드한 뒤 HQ 세션으로 홈 초안 저장·발행, 공개 resolve('/'), navigation 제외를 확인한다. 검증 내용은 무의미한 문구 변경이 남지 않도록 최종 기본 콘텐츠 또는 명시적인 개발 데모 문서로 정리한다.
3. 고객 4000 루트를 새로 열어 CMS hero, 예약 검색 UI, CMS 본문/CTA가 같이 표시되는 것을 확인한다. 관리자 4001의 실제 세션에서도 고정 홈 항목을 확인한다.
4. 문서에 실제 명령, 결과, 미검증 후속 범위를 한국어로 기록한다.

## 위험과 방어

- 루트 경로는 기존 PostgreSQL/Java 정규식에서 거부된다. 마이그레이션과 서버 PATH 검증을 함께 변경하고 root 외의 기존 정규식은 넓히지 않는다.
- homepage metadata가 수정되면 navigation과 canonical 규칙이 흐려진다. 저장 요청에는 content와 expectedDraftVersion만 받고 고정 metadata를 서버에서 사용한다.
- 공개 홈 API가 실패해 예약 화면을 가리면 안 된다. 고객은 검증된 발행 홈만 적용하고 실패하면 현재 static/destination fallback을 계속 렌더한다.
- CMS 문서가 가격·재고에 개입하면 예약 권한이 흐려진다. HOME_PAGE document와 renderer에는 예약 데이터를 넣지 않고, 기존 search 함수만 Spring API를 호출한다.

## 구현 및 검증 결과

- `V11__home_page.sql`은 루트 경로 `/`를 가진 단일 `HOME_PAGE`를 만들고, 초기 발행본·버전 1 스냅샷·이관 감사 기록을 생성한다. 홈은 `hotel_id`, `parent_id`가 `NULL`이며 `slug=home`, 메뉴 비노출 메타데이터가 고정된다.
- 본사 전용 홈 API는 `GET`·`PUT /api/staff/website/home`, `POST /api/staff/website/home/publish`, `GET /api/staff/website/home/versions`이다. 저장 요청은 콘텐츠와 초안 버전만 받고 고정 메타데이터는 서버가 적용한다. 기존 일반 page 발행 API는 `HOME_PAGE`를 거부한다.
- 관리자 CMS 트리는 `SECTION`보다 앞에 고정 `홈` leaf를 표시한다. 홈을 선택하면 slug·경로·메뉴 노출·순서 같은 페이지 정보 입력은 숨기고 SEO 및 `HERO`·`TEXT`·`CTA`만 편집한다.
- 고객 루트는 검증된 발행 `HOME_PAGE`를 `CMS HERO → 코드 소유 예약 검색·AI → CMS TEXT/CTA → 기존 지점 경험·오퍼·객실·도착 안내` 순서로 렌더링한다. 홈 문서가 없거나 유효하지 않으면 기존 정적 홈을 계속 표시한다.
- 대상 서버 통합 테스트 `WebContentIntegrationTest,WebsitePageIntegrationTest`는 13건 통과했고 실패·오류는 0건이었다. 홈의 공개/초안 분리, 본사 권한, 루트 resolve, navigation 제외, 일반 page API의 우회 발행 차단을 확인했다.
- 관리자 `pnpm.cmd exec tsc --noEmit`과 `website-content-editor.spec.ts` Chromium Playwright는 통과했으며 Playwright는 4건을 실행했다. 고객 웹의 콘텐츠 파서 검사와 `pnpm.cmd build`도 통과했다.
- API 컨테이너를 재빌드해 Flyway V11 적용을 확인했다. 개발 API에서 본사 세션으로 기존 홈 초안을 내용 변경 없이 저장하고 발행하여 초안·발행 버전이 모두 2가 되었고, 공개 `/` resolve는 `type: HOME_PAGE`, `hotelId: null`을 반환했으며 navigation에는 루트 항목이 없었다.
- 실제 in-app browser에서 고객 `4000` 홈페이지의 CMS 히어로·예약 검색/AI·본문/CTA 조합과 관리자 `4001` CMS 트리의 고정 홈 항목을 확인했다.

## 남은 범위

- 자산 카탈로그와 안전한 업로드·대체 텍스트·사용 위치 추적, 페이지 삭제·부모 이동·깊은 트리, 한국어·영어 번역과 승인 흐름은 아직 구현하지 않았다.
- 예약 발행, 예약 발행 시점 예약, 변경 비교·버전 복원, 인증된 초안 미리보기와 canonical·OG·robots도 후속 범위다.
