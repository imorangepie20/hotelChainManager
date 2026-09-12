# 일반 콘텐츠 페이지 CMS 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**목표:** 본사 관리자가 섹션 아래의 일반 콘텐츠 페이지를 생성하고, 안전한 블록을 편집·발행하여 고객 웹의 독립 주소와 메뉴에서 제공한다.

**아키텍처:** 기존 `website_page`를 단일 페이지·경로·메뉴·발행 기준으로 유지하고 `CONTENT_PAGE`를 추가한다. 첫 단계의 일반 페이지는 `SECTION`의 직계 자식이며 호텔·재고·가격 데이터와 연결하지 않는다. Spring Boot가 JSON 블록 계약·경로·권한·발행 버전을 검증하고, React 고객 웹은 신뢰 가능한 발행 응답만 allowlist 렌더러로 표시한다.

**기술 스택:** PostgreSQL 16/Flyway, Java 21/Spring Boot/JdbcTemplate, Next.js/React 19 제공 관리자 테마, Vite/React 19 고객 웹, Playwright, TypeScript.

**설계 근거:** `docs/superpowers/specs/2026-09-10-web-content-management-design.md`, `docs/superpowers/plans/2026-09-10-web-content-management.md`, `docs/superpowers/plans/2026-09-11-website-page-tree.md`, `docs/architecture/full-site-implementation-design.md`

## 전역 제약

- 첫 범위의 일반 페이지 유형은 `CONTENT_PAGE` 하나이며, 상위는 이미 존재하는 `SECTION`만 허용한다. 섹션 생성·삭제, 페이지 이동, 중첩, 드래그 앤 드롭은 포함하지 않는다.
- `CONTENT_PAGE.hotel_id`는 항상 `NULL`이다. 일반 페이지는 객실 재고, 가격, 예약 토큰, 고객 정보, 결제 데이터를 저장하거나 표시하지 않는다.
- 일반 페이지 문서는 `seo`와 `blocks`만 사용한다. 블록은 `HERO`, `TEXT`, `CTA` 세 유형만 허용하며, HTML/Markdown/스크립트/원격 이미지/외부 CTA URL을 허용하지 않는다.
- 이미지 경로는 `/images/`로 시작하는 로컬 정적 경로만 허용한다. CTA `href`는 `/`, `/#fragment` 또는 안전한 로컬 경로와 그 hash fragment만 허용한다. React는 모든 문자열을 텍스트로 렌더링하고 `dangerouslySetInnerHTML`을 사용하지 않는다.
- 경로·메뉴·콘텐츠는 같은 초안과 발행 버전으로 저장한다. 발행되지 않은 초안 경로와 메뉴는 공개 API에서 반환하지 않는다.
- 모든 콘텐츠 페이지 관리 API는 Spring 서버에서 `HQ_ADMIN`을 확인한다. 지점 직원은 403이다.
- 이미 더티한 현재 작업 트리에서는 관련 없는 파일을 stage, reset, checkout, commit하지 않는다.
- 검증은 변경 영역에 한정한다. PostgreSQL 통합 테스트, 관리자 단일 E2E, 고객 웹 빌드와 새 발행 URL의 실제 확인을 수행한다.

## 파일 구조

| 파일 | 책임 |
| --- | --- |
| `services/api/src/main/resources/db/migration/V10__content_pages.sql` | `CONTENT_PAGE` 유형과 소유·부모 제약을 안전하게 확장하고 브랜드 섹션을 초기화한다. |
| `services/api/src/main/java/team/hotelchain/webcontent/ContentPageValidator.java` | 일반 페이지 블록·SEO·로컬 링크·로컬 이미지 계약을 한 곳에서 검사한다. |
| `services/api/src/main/java/team/hotelchain/webcontent/CreateWebsitePageRequest.java` | 일반 페이지 생성 요청 계약이다. |
| `services/api/src/main/java/team/hotelchain/webcontent/SaveWebsitePageRequest.java` | 일반 페이지 초안 저장 요청 계약이다. |
| `services/api/src/main/java/team/hotelchain/webcontent/PublishWebsitePageRequest.java` | 일반 페이지 발행의 낙관적 버전 요청 계약이다. |
| `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageService.java` | page ID 기반 생성·조회·저장·발행·버전, 재귀가 아닌 첫 단계 직계 트리와 공개 resolve를 구현한다. |
| `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageManagementController.java` | `/api/staff/website/pages` page resource API를 노출한다. |
| `services/api/src/test/java/team/hotelchain/webcontent/WebsitePageIntegrationTest.java` | 생성, 공개/비공개 분리, 권한, 블록·경로 거부를 검증한다. |
| `SDTPL_ADM/src/lib/staff-api.ts` | page ID 기반 관리자 API 타입과 호출을 제공한다. |
| `SDTPL_ADM/src/components/hotel-admin/website-page-tree.tsx` | 섹션과 직계 leaf를 재귀적으로 보이는 선택 가능한 페이지 트리로 바꾼다. |
| `SDTPL_ADM/src/components/hotel-admin/content-page-editor.tsx` | 일반 페이지의 페이지 정보·SEO·HERO/TEXT/CTA 구조화 폼을 제공한다. |
| `SDTPL_ADM/src/components/hotel-admin/website-content-editor.tsx` | 기존 호텔 랜딩 편집기를 유지하면서 선택·생성·미저장 이동 보호를 page ID 기준으로 연결한다. |
| `SDTPL_ADM/e2e/website-content-editor.spec.ts` | 일반 페이지 생성→블록 저장→발행 가능 상태를 한 흐름으로 검증한다. |
| `apps/web/src/lib/customer-route.ts` | 홈과 안전한 CMS 경로를 정규화한다. |
| `apps/web/src/lib/content-page.ts` | 공개 일반 페이지 문서를 방어적으로 파싱한다. |
| `apps/web/src/components/content-page.tsx` | allowlist 블록만 고객 화면에 렌더링한다. |
| `apps/web/src/lib/api.ts` | nullable `hotelId`를 포함한 공개 페이지 계약을 표현한다. |
| `apps/web/src/App.tsx` | 호텔 랜딩과 일반 페이지를 분기하고 공개 메뉴와 공통 헤더를 안전하게 연결한다. |
| `docs/changes/2026-09-10-web-content-management.md` 등 | 구현·검증·남은 범위를 현재 상태 문서에 남긴다. |

---

### Task 1: 스키마와 서버 계약을 고정한다

**Files:**
- Create: `services/api/src/main/resources/db/migration/V10__content_pages.sql`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/ContentPageValidator.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/CreateWebsitePageRequest.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/SaveWebsitePageRequest.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/PublishWebsitePageRequest.java`
- Modify: `services/api/src/test/java/team/hotelchain/webcontent/WebsitePageIntegrationTest.java`

**Interfaces:**
- Consumes: V9 `website_page`의 `SECTION`, `HOTEL_LANDING`, 초안/발행 JSONB·버전 필드.
- Produces: `CONTENT_PAGE`를 저장할 수 있는 테이블 제약과 `ContentPageValidator.validate(Map<String, Object>)`.

- [x] **Step 1: 실패하는 일반 페이지 통합 테스트를 작성한다.**

  `WebsitePageIntegrationTest`에 다음 시나리오를 추가한다.

  ```java
  @Test
  void createsPublishesAndResolvesAContentPageWithoutHotelData() {
      StaffSessionView session = staffAccess.login("pages-hq@example.com", "hq-password");
      WebsitePageDocument created = pages.createContentPage(
          session.token(), BRAND_SECTION,
          new WebsitePageDraftMetadata("story", "브랜드 스토리", true, 10),
          validContentPage("브랜드 스토리"));

      assertThat(created.pageType()).isEqualTo("CONTENT_PAGE");
      assertThat(created.hotelId()).isNull();
      assertThatThrownBy(() -> pages.resolvePublished("/brand/story"))
          .isInstanceOf(WebsitePageNotFoundException.class);

      WebsitePageDocument published = pages.publishPage(
          session.token(), created.id(), created.draftVersion(), created.publishedVersion());
      PublishedWebsitePage resolved = pages.resolvePublished("/brand/story");

      assertThat(published.publishedMetadata().path()).isEqualTo("/brand/story");
      assertThat(resolved.type()).isEqualTo("CONTENT_PAGE");
      assertThat(resolved.hotelId()).isNull();
  }
  ```

- [x] **Step 2: 테스트가 V9 제약 또는 누락 메서드 때문에 실패하는지 확인한다.**

  Run:

  ```powershell
  & $maven '-Dmaven.repo.local=C:\Users\jowoo\.m2\repository' '-Dtest=WebsitePageIntegrationTest' test
  ```

  Expected: `CONTENT_PAGE`를 허용하지 않거나 `createContentPage`가 없어서 실패한다.

- [x] **Step 3: V10 마이그레이션을 작성한다.**

  V9가 자동으로 만든 `website_page_page_type_check`와 `website_page_check2`만 `ALTER TABLE ... DROP CONSTRAINT`로 제거하고 다음 명명 제약을 추가한다.

  ```sql
  ALTER TABLE website_page
      ADD CONSTRAINT website_page_type_check
          CHECK (page_type IN ('SECTION', 'HOTEL_LANDING', 'CONTENT_PAGE')),
      ADD CONSTRAINT website_page_owner_check
          CHECK (
              (page_type = 'SECTION' AND hotel_id IS NULL AND parent_id IS NULL)
              OR (page_type = 'HOTEL_LANDING' AND hotel_id IS NOT NULL AND parent_id IS NOT NULL)
              OR (page_type = 'CONTENT_PAGE' AND hotel_id IS NULL AND parent_id IS NOT NULL)
          );
  ```

  `SECTION` 부모 여부는 PostgreSQL CHECK에서 다른 행을 안전하게 조회할 수 없으므로 서비스에서 확인한다. `BRAND_SECTION_ID`를 가진 `/brand` 섹션은 발행 메뉴에 노출하되 콘텐츠는 비워 두고, 그 아래의 초기 일반 페이지는 자동 생성하지 않는다. 생성 이력을 남길 수 있도록 `website_page_audit`의 action check도 `CREATED`를 허용하도록 확장한다.

- [x] **Step 4: 구조화 콘텐츠 validator를 최소 계약으로 구현한다.**

  `ContentPageValidator`는 아래 JSON만 허용한다.

  ```json
  {
    "seo": { "title": "브랜드 스토리 | STAY HANEUL", "description": "..." },
    "blocks": [
      {
        "type": "HERO",
        "imageSrc": "/images/sokcho-coast-hero.png",
        "imageAlt": "STAY HANEUL의 풍경",
        "eyebrow": "STAY HANEUL",
        "title": "브랜드 스토리",
        "description": "...",
        "cta": { "label": "숙소 둘러보기", "href": "/stays/sokcho" }
      },
      { "type": "TEXT", "eyebrow": "OUR STORY", "title": "머무름의 기준", "paragraphs": ["...", "..."] },
      { "type": "CTA", "eyebrow": "BOOK YOUR STAY", "title": "여정을 시작하세요", "description": "...", "cta": { "label": "객실 예약", "href": "/stays/sokcho#booking" } }
    ]
  }
  ```

  `blocks`는 1~12개, 첫 블록은 `HERO`, 각 텍스트 필드는 정해진 길이 이내, `TEXT.paragraphs`는 1~6개의 문자열, `HERO`는 한 번만 허용한다. `imageSrc`는 `/images/` 아래의 빈 경로 조각·`.`·`..`이 없는 경로만 허용한다. `href`는 `/`, `/#fragment`, 또는 소문자 영숫자·하이픈으로 구성된 로컬 경로와 선택 hash fragment만 허용한다. 모든 계약 오류는 `IllegalArgumentException("콘텐츠 형식 오류: ...")`로 반환한다.

- [x] **Step 5: validator 거부와 마이그레이션 적용을 포함한 서버 테스트를 다시 실행한다.**

  Run:

  ```powershell
  & $maven '-Dmaven.repo.local=C:\Users\jowoo\.m2\repository' '-Dtest=WebsitePageIntegrationTest' test
  ```

  Expected: 일반 페이지의 외부 CTA·원격 이미지·잘못된 상위/슬러그를 검사하는 새 케이스를 포함해 통과한다.

### Task 2: page ID 기반 CMS API와 공개 발행 경로를 구현한다

**Files:**
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageService.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageManagementController.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/PublishedWebsitePage.java`
- Modify: `services/api/src/test/java/team/hotelchain/webcontent/WebsitePageIntegrationTest.java`

**Interfaces:**
- Consumes: Task 1의 `CONTENT_PAGE`, validator, `WebsitePageDraftMetadata`.
- Produces: `createContentPage`, `pageDraft`, `saveContentPageDraft`, `publishPage`, `pageVersions`와 page resource HTTP API.

- [x] **Step 1: 페이지 생성·초안 저장·발행의 실패 테스트를 추가한다.**

  페이지 생성은 SECTION이 아닌 부모와 경로 충돌을 거부하고, 저장 후 발행 전에는 navigation·resolve에 드러나지 않아야 한다. 저장한 메뉴명·슬러그만 달라져도 트리 상태는 `CHANGED_AFTER_PUBLISH`여야 한다. 지점 세션의 page create/read/save/publish는 모두 403이어야 한다.

- [x] **Step 2: 공통 page ID 메서드를 최소 구현한다.**

  ```java
  public WebsitePageDocument createContentPage(String token, UUID parentId,
      WebsitePageDraftMetadata metadata, Map<String, Object> content);
  public WebsitePageDocument pageDraft(String token, UUID pageId);
  public WebsitePageDocument saveContentPageDraft(String token, UUID pageId,
      int expectedDraftVersion, WebsitePageDraftMetadata metadata, Map<String, Object> content);
  public WebsitePageDocument publishPage(String token, UUID pageId,
      int expectedDraftVersion, int expectedPublishedVersion);
  public List<WebContentVersion> pageVersions(String token, UUID pageId);
  ```

  생성 시 `parentId`의 실제 페이지 유형이 `SECTION`인지 `FOR KEY SHARE` 조회로 확인하고, `draftPath`와 `publishedPath` 모두에 대한 충돌을 검사한다. 성공한 생성은 `website_page_audit`에 `CREATED`와 실제 초안 경로를 기록한다. `snapshot(PageRow)`는 실제 `parent_id`, nullable `hotel_id`를 저장하며, 하드코딩된 숙소 SECTION ID를 사용하지 않는다. 기존 `landingDraft`/`saveLandingDraft`/`publishLanding` facade는 같은 공통 저장·발행 구현을 사용해 기존 계약을 유지한다.

- [x] **Step 3: HTTP API를 추가한다.**

  `WebsitePageManagementController`에 다음 엔드포인트를 구현한다.

  ```text
  POST /api/staff/website/pages
  GET  /api/staff/website/pages/{pageId}
  PUT  /api/staff/website/pages/{pageId}
  POST /api/staff/website/pages/{pageId}/publish
  GET  /api/staff/website/pages/{pageId}/versions
  ```

  생성 body는 `{ parentId, page, content }`, 저장 body는 `{ expectedDraftVersion, page, content }`, 발행 body는 `{ expectedDraftVersion, expectedPublishedVersion }`이다. 각 엔드포인트는 `X-Staff-Session`을 service에 전달하고 본사 권한을 service에서 확인한다.

- [x] **Step 4: 공개 page/navigation/tree를 일반 페이지에 맞춘다.**

  `resolvePublished`는 `CONTENT_PAGE`와 `HOTEL_LANDING`의 발행 콘텐츠를 반환하며 `PublishedWebsitePage.hotelId`를 nullable로 둔다. navigation과 staff tree는 `SECTION`의 직계 children을 유지하고, `CONTENT_PAGE`도 순서·발행 상태에 따라 포함한다. 상태는 `published_content`가 비었으면 `DRAFT`, 그렇지 않고 `published_from_draft_version == draft_version`이면 `PUBLISHED`, 그 외에는 `CHANGED_AFTER_PUBLISH`이다.

- [x] **Step 5: 서버 테스트를 실행한다.**

  Run:

  ```powershell
  & $maven '-Dmaven.repo.local=C:\Users\jowoo\.m2\repository' '-Dtest=WebContentIntegrationTest,WebsitePageIntegrationTest' test
  ```

  Expected: 기존 호텔 랜딩 호환 테스트와 일반 페이지 생성·발행·권한·초안 비공개 테스트가 모두 통과한다.

### Task 3: 관리자 트리와 일반 페이지 구조화 편집기를 연결한다

**Files:**
- Modify: `SDTPL_ADM/src/lib/staff-api.ts`
- Modify: `SDTPL_ADM/src/components/hotel-admin/website-page-tree.tsx`
- Create: `SDTPL_ADM/src/components/hotel-admin/content-page-editor.tsx`
- Modify: `SDTPL_ADM/src/components/hotel-admin/website-content-editor.tsx`
- Modify: `SDTPL_ADM/e2e/website-content-editor.spec.ts`

**Interfaces:**
- Consumes: Task 2 API와 `WebsitePageDocument`/tree 항목.
- Produces: HQ의 `+ 페이지` 생성 Dialog, page ID 선택, 콘텐츠 블록 편집·저장·발행 UI.

- [x] **Step 1: 관리자 API 타입과 호출의 실패 E2E를 작성한다.**

  `website-content-editor.spec.ts`에서 `/brand` SECTION과 `CONTENT_PAGE` 응답을 mock하고, `+ 페이지` Dialog가 `{ parentId, page, content }`을 POST하며 저장 전 발행은 비활성화되는 시나리오를 추가한다.

- [x] **Step 2: page ID 기준 트리로 바꾼다.**

  `WebsitePageTree`는 `WebsitePageTreeItem[]`, `selectedPageId`, `onSelectPage`를 받아 `SECTION` → child 목록을 렌더링한다. SECTION은 접고 펼칠 수 있고 leaf는 `button`, `aria-current="page"`, `focus-visible` ring, 유형 아이콘과 상태 텍스트를 제공한다. 기존 랜딩은 선택 가능한 leaf로 그대로 남긴다.

- [x] **Step 3: 콘텐츠 페이지 편집기와 생성 Dialog를 구현한다.**

  `content-page-editor.tsx`는 표준 테마 `Input`, `Textarea`, `Checkbox`, `Select`, `Dialog`, `Button`을 사용한다. 페이지 정보(슬러그·메뉴명·노출·순서), SEO(60/160자), 필수 HERO, 추가 가능한 TEXT/CTA를 제공하고 각 블록을 위·아래로 이동하거나 삭제한다. 문자열 JSON 입력란과 임의 블록 유형 선택은 노출하지 않는다.

  `+ 페이지` Dialog에는 상위 SECTION, 메뉴 이름, 주소 슬러그, 메뉴 노출, 메뉴 순서만 받는다. 클라이언트는 유효한 기본 HERO/SEO 문서를 함께 보내며, 서버가 최종 계약을 검증한다.

- [x] **Step 4: 기존 랜딩 흐름을 보존하며 선택 상태를 일반화한다.**

  `WebsiteContentEditor`는 `selectedPageId`를 단일 선택 기준으로 관리한다. `HOTEL_LANDING` 선택은 기존 호텔 facade와 모든 랜딩 폼을 그대로 사용하고, `CONTENT_PAGE` 선택은 새 page ID API와 `ContentPageEditor`로 분기한다. 미저장 편집 중 다른 leaf를 누르면 기존 AlertDialog로 변경을 버릴지 확인한다. 저장·발행 성공 시 tree와 versions를 재조회한다.

- [x] **Step 5: 대상 E2E와 타입 검사를 실행한다.**

  Run:

  ```powershell
  pnpm.cmd --dir SDTPL_ADM exec tsc --noEmit
  .\node_modules\.bin\playwright.CMD test e2e/website-content-editor.spec.ts --project=chromium
  ```

  Expected: 기존 랜딩 E2E와 새 일반 페이지 mock 흐름이 통과한다.

### Task 4: 고객 웹의 일반 경로·메뉴·블록 렌더링을 연결한다

**Files:**
- Modify: `apps/web/src/lib/api.ts`
- Modify: `apps/web/src/lib/customer-route.ts`
- Modify: `apps/web/src/lib/customer-route.test.ts`
- Create: `apps/web/src/lib/content-page.ts`
- Create: `apps/web/src/components/content-page.tsx`
- Modify: `apps/web/src/App.tsx`
- Modify: `apps/web/src/styles.css`

**Interfaces:**
- Consumes: Task 2의 nullable `hotelId`, `CONTENT_PAGE`, 공개 navigation/resolve 응답.
- Produces: `/brand/<slug>` 같은 직접 주소에서의 안전한 콘텐츠 페이지와 예약 랜딩의 기존 동작.

- [x] **Step 1: 고객 경로와 콘텐츠 파서의 실패 단위 테스트를 작성한다.**

  ```ts
  expectEqual(resolveCustomerRoute('/brand/story/'), {
    kind: 'page', pathname: '/brand/story', segments: ['brand', 'story'],
  }, '일반 CMS 경로를 정규화한다')
  expectEqual(resolveCustomerRoute('/brand/%2f'), null, '인코딩된 구분자를 거절한다')
  ```

  `content-page.ts`에는 `HERO/TEXT/CTA` 이외 블록과 잘못된 로컬 경로를 제거하고, 유효한 문자열만 렌더 가능한 데이터로 바꾸는 parser 테스트를 추가한다.

- [x] **Step 2: 공개 API와 경로 계약을 일반화한다.**

  `PublishedWebsitePage.hotelId`를 `string | null`로 바꾸고, route는 `/` 또는 정규화 가능한 CMS 경로만 허용한다. percent-encoded slash/backslash, 빈 segment, 3단계 이상의 nested path는 첫 단계에서 거절한다.

- [x] **Step 3: allowlist 콘텐츠 페이지 렌더러를 추가한다.**

  `ContentPage`는 HERO의 이미지·대체 텍스트·H1·선택 CTA, TEXT의 H2·paragraph, CTA의 로컬 링크만 표시한다. 기존 고객 웹의 hero/content-section/outline 스타일 토큰을 재사용하되 HTML 삽입을 하지 않는다. SEO 값은 브라우저 title과 meta description으로 반영한다.

- [x] **Step 4: App의 resolve·헤더를 분기한다.**

  일반 경로 resolve는 hotel 목록 로딩을 기다리지 않고 처리한다. `HOTEL_LANDING`만 기존 지점·예약 바를 표시하고, `CONTENT_PAGE`는 예약 UI 없이 `ContentPage`로 렌더링한다. 공통 헤더는 공개 navigation leaf를 사용하며 일반 페이지에서 깨지는 랜딩 전용 앵커를 숨긴다. `예약 조회`는 언제나 `/#reservation-management`을 가리킨다.

- [x] **Step 5: 고객 웹 검증을 실행한다.**

  Run:

  ```powershell
  pnpm.cmd --dir apps/web build
  ```

  고객 경로·문서 파서 단위 검사와 고객 웹 빌드는 통과했다. 실제 통합 검증에서 API `4080` readiness `UP`을 확인하고, 본사 세션으로 `/brand/story`를 생성·발행했다(초안 v1, 발행 v2). 공개 navigation 노출과 resolve의 `CONTENT_PAGE`·`hotelId: null`을 확인했으며, `http://127.0.0.1:4000/brand/story` 직접 진입에서 제목·메뉴·HERO/TEXT/CTA·로컬 CTA 링크가 접근성 트리에 렌더링됐다. 관리자 `4001`의 실제 로그인 CMS 새로고침에서도 브랜드 SECTION 아래 `브랜드 이야기` 항목을 확인했다. 삭제 API가 아직 없으므로 검증 페이지는 로컬 개발 콘텐츠로 남는다.

### Task 5: 문서와 실제 검증 결과를 업데이트한다

**Files:**
- Modify: `docs/changes/2026-09-10-web-content-management.md`
- Modify: `docs/superpowers/plans/2026-09-10-web-content-management.md`
- Modify: `docs/architecture/full-site-implementation-design.md`
- Modify: `docs/overview/current-development-context.md`
- Modify: `docs/decisions/decision-log.md`
- Modify: `docs/superpowers/plans/2026-09-11-content-pages.md`

**Interfaces:**
- Consumes: Tasks 1–4의 최종 contracts 및 실제 명령 결과.
- Produces: 현재 구현/검증/미검증 범위를 구분한 한국어 프로젝트 문서.

- [x] **Step 1: 구현 결과를 계획 문서에 체크한다.**

  실제 구현된 API, 페이지 경로, block allowlist, 권한, customer renderer를 완료/미완료로 나눈다. 계획과 다르게 구현된 내용이 있으면 계획도 정확한 최종 계약으로 고친다.

- [x] **Step 2: 변경 기록과 사이트 설계서를 갱신한다.**

  V10의 제약 확장, page ID API, `CONTENT_PAGE` 문서, CTA/이미지 안전 규칙, 신규 public route, 랜딩과의 분리를 기록한다. 다국어·미디어 업로드·삭제·이동·깊은 트리·복원은 여전히 후속 범위임을 명확히 적는다.

- [x] **Step 3: 검증 증거와 다음 작업을 갱신한다.**

  실행한 정확한 테스트 명령과 코드·E2E·빌드 결과를 기록했다. 실제 API 생성·발행·공개 resolve, 고객 브라우저 직접 경로, 실제 관리자 CMS 트리 확인 결과도 기록했다. 전체 테스트를 불필요하게 재실행하지 않는다.

## 구현 결과와 현재 검증

- V10은 `CONTENT_PAGE`를 `website_page` 유형에 추가하고, `/brand` `SECTION`을 초기화했다. 일반 페이지 생성 시 `SECTION` 직계 여부를 서버가 확인하며 `hotel_id`는 `NULL`로 유지한다. 초기 일반 콘텐츠 페이지는 자동 생성하지 않는다.
- 일반 페이지는 `seo`와 `blocks`만 가지며, `HERO` 1개를 첫 블록으로 강제한다. 서버와 고객 파서는 같은 `HERO`·`TEXT`·`CTA` allowlist, 필드 길이, 로컬 이미지·CTA 경로 제약을 독립적으로 적용한다.
- 본사 전용 page ID API는 생성·초안 조회·저장·발행·버전 조회를 제공한다. 생성은 `CREATED`, 저장은 `DRAFT_SAVED`, 발행은 `PUBLISHED` 감사 로그를 남긴다. 저장·발행은 양의 초안/발행 버전을 요구하며, 기존 지점 랜딩 facade는 유지한다.
- 관리자에는 접을 수 있는 `SECTION` 트리, `+ 페이지` 대화상자, HERO/TEXT/CTA 구조화 편집기, 미저장 이동 보호가 추가됐다. 고객 웹은 `/brand/story` 같은 최대 2단계 CMS 경로를 정규화하고, `CONTENT_PAGE`는 호텔·예약 바 없이 안전한 블록 렌더러로 표시한다.

### 완료한 코드 수준 검증

```powershell
# services/api
& C:\Users\jowoo\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd `
  '-Dmaven.repo.local=C:\Users\jowoo\.m2\repository' `
  '-Dtest=WebContentIntegrationTest,WebsitePageIntegrationTest' test
# 결과: 12개 테스트 통과, 실패·오류 0

# SDTPL_ADM
pnpm.cmd exec tsc --noEmit
.\node_modules\.bin\playwright.CMD test e2e/website-content-editor.spec.ts --project=chromium
# 결과: 타입 검사 통과, Playwright 3개 통과

# apps/web
node --experimental-strip-types src/lib/customer-route.test.ts
node --experimental-strip-types src/lib/content-page.test.ts
pnpm.cmd build
# 결과: 경로·문서 파서 검사 통과, 고객 웹 프로덕션 빌드 통과
```

V10 마이그레이션이 적용된 로컬 API에서 `CONTENT_PAGE` 생성·발행·공개 메뉴/resolve를 확인했고, 고객 웹 직접 주소와 실제 관리자 CMS 트리까지 확인했다. 검증 페이지는 삭제 기능이 추가되기 전까지 로컬 개발 데이터로 유지한다.

## 계획 자체 검토

- [x] V9 페이지 발행 모델을 그대로 활용하면서 `CONTENT_PAGE`의 데이터·API·관리자·고객 웹 범위를 모두 다룬다.
- [x] 가격·재고·예약은 콘텐츠 페이지에서 제외했고, 호텔 랜딩 facade를 유지한다.
- [x] 모든 새 public 문서는 초안/발행 분리, 경로 충돌, 본사 권한, 안전한 렌더링을 포함한다.
- [x] 첫 범위를 SECTION 직계 콘텐츠 페이지로 제한해 page 이동·중첩·미디어·다국어를 별도 후속 작업으로 남긴다.
- [x] placeholder/TODO 표현을 사용하지 않았고, 각 구현 단위에 대상 파일·계약·검증을 적었다.
