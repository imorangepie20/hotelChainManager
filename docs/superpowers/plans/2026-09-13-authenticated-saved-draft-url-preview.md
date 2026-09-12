# 인증형 저장 초안 URL 미리보기 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 본사 콘텐츠 담당자가 저장된 한국어·영어 초안을 실제 고객 사이트에서 10분간 안전하게 검토하는 URL을 발급한다.

**Architecture:** Spring의 DB 기반 preview grant가 원문을 저장하지 않는 bearer token을 페이지·locale·draft version·경로에 결합한다. 별도 public preview endpoint가 grant를 검증한 뒤 기존 `PublishedWebsitePage` 형태로 저장 초안을 반환하고, 관리자 Next.js는 링크 발급·복사·폐기를 담당한다. 고객 React 앱은 fragment token을 즉시 `sessionStorage`로 옮겨 주소에서 제거하고 기존 renderer를 재사용하되 모든 예약·결제·콘텐츠 CTA를 막는다.

**Tech Stack:** Java 21, Spring Boot 4.1.1, JDBC, PostgreSQL 16, Flyway, JUnit 5, MockMvc, React 19, Next.js 16, Vite 8, TypeScript 5, Base UI Dialog, Playwright 1.60

**Spec:** `docs/superpowers/specs/2026-09-13-authenticated-saved-draft-url-preview-design.md`

## Global Constraints

- 대상은 활성 `HOME_PAGE`, `HOTEL_LANDING`, `CONTENT_PAGE`의 서버에 저장된 최신 초안이다.
- `HQ_ADMIN`, `HQ_EDITOR`, `HQ_PUBLISHER`만 발급하고 `BRANCH_STAFF`는 거부한다.
- 영어 translation row가 없으면 영어 grant를 발급하지 않는다.
- token은 32-byte 난수를 URL-safe Base64 padding 없이 인코딩하고 DB에는 SHA-256 hash만 저장한다.
- grant는 정확히 10분 동안 한 페이지·locale·draft version·정규화 경로에만 유효하다.
- 초안 version·경로 변경, 페이지 보관, 만료, 폐기 뒤에는 `410 WEBSITE_PREVIEW_UNAVAILABLE`이다.
- 알 수 없는 token과 cross-page/path/locale 사용은 `404 WEBSITE_PREVIEW_NOT_FOUND`다.
- preview GET은 DB·audit·페이지·번역·발행 snapshot을 절대 변경하지 않는다.
- preview 오류는 공개 resolve로 자동 fallback하지 않는다.
- fragment는 첫 bootstrap에서 즉시 주소에서 제거하고 token을 로그·분석·영구 저장소에 보내지 않는다.
- 미리보기 중 예약 조회·검색·생성·변경·취소, 결제, AI 예약 적용, 콘텐츠 CTA를 실행하지 않는다.
- 다른 일반 내비게이션 경로로 이동할 때만 preview session을 종료하고 공개 페이지를 표시한다.
- 운영 고객·관리자·API는 HTTPS를 사용하며 실제 사용자 데이터에는 검증용 mutation을 보내지 않는다.
- 기존 공개 resolve, 번역 검토·발행, 예약·재고 권한과 사용자 변경을 보존한다.

## 파일 구조

- Create: `services/api/src/main/resources/db/migration/V24__create_website_preview_grant.sql` — grant 수명주기와 인덱스
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePreviewGrantRequest.java` — locale·기대 version 발급 요청
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePreviewGrantResponse.java` — 원문 token을 한 번 반환하는 발급 응답
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePreviewResult.java` — public body와 만료 header용 내부 결과
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePreviewNotFoundException.java` — 노출 방지용 404
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePreviewUnavailableException.java` — 알려진 grant 무효화용 410
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePreviewGrantService.java` — 발급·폐기·검증·정리
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePreviewGrantController.java` — staff POST·DELETE
- Create: `services/api/src/main/java/team/hotelchain/webcontent/PublicWebsitePreviewController.java` — public preview GET와 no-store header
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageService.java` — 한국어 저장 초안 응답 조립
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteTranslationService.java` — 영어 저장 초안 응답 조립
- Modify: `services/api/src/main/java/team/hotelchain/web/ApiExceptionHandler.java` — preview 404·410 계약
- Create/Test: `services/api/src/test/java/team/hotelchain/webcontent/WebsitePreviewGrantIntegrationTest.java` — 역할·수명주기·응답·불변성
- Modify: `SDTPL_ADM/src/lib/staff-api.ts` — 발급·폐기 타입과 함수
- Create: `SDTPL_ADM/src/components/hotel-admin/website-saved-draft-preview-action.tsx` — 링크 열기·복사·폐기 UI
- Modify: `SDTPL_ADM/src/components/hotel-admin/content-page-editor.tsx` — 홈·일반 페이지 및 영어 action 연결
- Modify: `SDTPL_ADM/src/components/hotel-admin/website-content-editor.tsx` — 한국어 지점 랜딩 action 연결
- Create/Test: `SDTPL_ADM/e2e/website-saved-draft-preview.spec.ts` — 관리자 dirty·팝업·폐기·모바일 흐름
- Create: `apps/web/src/lib/website-preview.ts` — fragment·sessionStorage 수명주기
- Create/Test: `apps/web/src/lib/website-preview.test.ts` — 브라우저 독립 token capture 검사
- Modify: `apps/web/src/lib/api.ts` — preview header 요청과 expiry 응답
- Create: `apps/web/src/components/website-preview-banner.tsx` — 배너·만료·종료 UI
- Modify: `apps/web/src/components/content-page.tsx` — 콘텐츠 CTA 비활성화
- Modify: `apps/web/src/components/concierge-panel.tsx` — preview 중 AI 예약 적용 비활성화
- Modify: `apps/web/src/App.tsx` — preview bootstrap, noindex, 오류 비fallback, 예약 action 차단
- Modify: `apps/web/src/styles.css` — 배너·오류·비활성 action·모바일 스타일
- Create: `SDTPL_ADM/playwright.customer.config.ts` — 고객 Vite를 구동하는 고객 전용 E2E 설정
- Create/Test: `SDTPL_ADM/e2e/customer-saved-draft-preview.spec.ts` — 고객 실제 renderer·보안·상호작용 검사
- Modify: `docs/architecture/cms-functional-specification.md` — 구현된 preview 계약
- Modify: `docs/overview/current-development-context.md` — 현재 상태와 검증 결과
- Create: `docs/changes/2026-09-13-authenticated-saved-draft-url-preview.md` — 한국어 변경 기록

---

### Task 1: Preview grant schema와 발급·폐기 수명주기

**Files:**
- Create: `services/api/src/main/resources/db/migration/V24__create_website_preview_grant.sql`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePreviewGrantRequest.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePreviewGrantResponse.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePreviewNotFoundException.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePreviewUnavailableException.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePreviewGrantService.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePreviewGrantController.java`
- Test: `services/api/src/test/java/team/hotelchain/webcontent/WebsitePreviewGrantIntegrationTest.java`

**Interfaces:**
- Consumes: `StaffAccessService.requireContentStaff(String): StaffPrincipal`, `website_page`, `website_page_translation`
- Produces: `WebsitePreviewGrantService.issue(String, UUID, WebsitePreviewGrantRequest): WebsitePreviewGrantResponse`
- Produces: `WebsitePreviewGrantService.revoke(String, UUID): void`
- Produces: `POST /api/staff/website/pages/{pageId}/preview-grants`
- Produces: `DELETE /api/staff/website/preview-grants/{grantId}`

- [x] **Step 1: 실패하는 grant 수명주기 통합 테스트 작성**

고정 `Clock`을 주입하는 `@SpringBootTest @Transactional` 테스트를 만든다. 네 직원 역할과 활성 한국어 페이지, 존재하는 영어 번역을 seed한다. literal assertion으로 권한, token 비저장, 10분 만료, 이전 grant 폐기, version 충돌, 영어 누락, 폐기 권한과 24시간 정리를 검증한다.

```java
WebsitePreviewGrantResponse grant = previews.issue(
        editor.token(), page.id(), new WebsitePreviewGrantRequest("ko", page.draftVersion()));

assertThat(grant.previewPath()).isEqualTo("/brand/preview-story");
assertThat(grant.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(10)));
assertThat(grant.previewToken()).matches("[A-Za-z0-9_-]{43}");
String storedHash = jdbc.queryForObject(
        "select token_hash from website_preview_grant where id = ?", String.class, grant.grantId());
assertThat(storedHash).matches("[0-9a-f]{64}").isNotEqualTo(grant.previewToken());
```

두 번째 발급 후 첫 행의 `revoked_at/revoked_by`를 확인한다. 다른 `HQ_EDITOR`가 폐기하면 `StaffAccessDeniedException`, 발급자와 `HQ_ADMIN`의 반복 폐기는 성공해야 한다. `clock.advance(Duration.ofHours(25))` 뒤 새 링크를 발급해 오래된 만료·폐기 행만 삭제되는지 확인한다.

- [x] **Step 2: RED 확인**

Run from `services/api`:

```powershell
.\mvnw.cmd '-Dtest=WebsitePreviewGrantIntegrationTest#issuesHashedVersionBoundGrantAndRevokesItWithContentRoles' test
```

Expected: migration table 또는 preview DTO/service가 없어 test compilation/context startup이 실패한다.

- [x] **Step 3: V24 schema와 DTO 구현**

```sql
CREATE TABLE website_preview_grant (
  id UUID PRIMARY KEY,
  token_hash VARCHAR(64) NOT NULL UNIQUE CHECK (char_length(token_hash) = 64),
  page_id UUID NOT NULL REFERENCES website_page(id) ON DELETE CASCADE,
  locale VARCHAR(2) NOT NULL CHECK (locale IN ('ko', 'en')),
  draft_version INTEGER NOT NULL CHECK (draft_version > 0),
  preview_path VARCHAR(255) NOT NULL,
  issued_by UUID NOT NULL REFERENCES staff_member(id),
  issued_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  revoked_by UUID REFERENCES staff_member(id),
  CHECK (expires_at > issued_at),
  CHECK (revoked_at IS NULL OR revoked_at >= issued_at)
);
CREATE INDEX website_preview_grant_issuer_page_locale_idx
  ON website_preview_grant(issued_by, page_id, locale, expires_at);
CREATE INDEX website_preview_grant_cleanup_idx
  ON website_preview_grant(expires_at, revoked_at);
```

DTO signature를 고정한다.

```java
public record WebsitePreviewGrantRequest(String locale, int expectedDraftVersion) {}
public record WebsitePreviewGrantResponse(
        UUID grantId, String previewToken, String previewPath, Instant expiresAt) {}
```

`WebsitePreviewNotFoundException`과 `WebsitePreviewUnavailableException`은 각각 고정 code accessor `WEBSITE_PREVIEW_NOT_FOUND`, `WEBSITE_PREVIEW_UNAVAILABLE`을 제공한다. 원문 token을 exception message에 넣지 않는다.

- [x] **Step 4: 최소 발급·폐기 service 구현**

`WebsitePreviewGrantService`는 `JdbcTemplate`, `StaffAccessService`, `Clock`, `SecureRandom`을 사용한다. 상수는 `TTL = Duration.ofMinutes(10)`, `CLEANUP_RETENTION = Duration.ofHours(24)`다.

```java
@Transactional
public WebsitePreviewGrantResponse issue(
        String staffToken, UUID pageId, WebsitePreviewGrantRequest request)

@Transactional
public void revoke(String staffToken, UUID grantId)
```

발급 시 locale을 `WebsiteTranslationService.locale(request.locale(), null)`로 검증하고 기대 version은 1 이상이어야 한다. 한국어는 `website_page`, 영어는 page와 translation join을 `FOR UPDATE`로 읽어 같은 page의 동시 발급과 초안 저장을 직렬화한 상태에서 active·허용 page type·현재 draft path/version을 얻는다. 영어 row가 없으면 `WebsitePageNotFoundException`, stale이면 `BusinessConflictException("WEBSITE_PAGE_VERSION_CONFLICT", ...)`을 던진다.

새 token은 다음과 같이 만들고 원문을 지역 변수와 응답 외에 전달하지 않는다.

```java
byte[] bytes = new byte[32];
random.nextBytes(bytes);
String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
String tokenHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
        .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
```

같은 `issued_by/page_id/locale`의 `revoked_at is null and expires_at > now` 행을 먼저 폐기한다. 그 뒤 새 행을 insert한다. 발급 transaction 시작 시 `expires_at < now-24h OR revoked_at < now-24h`인 행만 삭제한다.

폐기는 행을 읽고 `issued_by == actor.id || actor.role == HQ_ADMIN`을 검사한다. 남은 행이 이미 만료·폐기 상태면 mutation 없이 성공하고, 활성 상태면 `revoked_at`, `revoked_by`만 기록한다.

- [x] **Step 5: staff controller 연결**

```java
@PostMapping("/pages/{pageId}/preview-grants")
public WebsitePreviewGrantResponse issue(
        @PathVariable UUID pageId,
        @RequestBody WebsitePreviewGrantRequest request,
        @RequestHeader("X-Staff-Session") String token) {
    return previews.issue(token, pageId, request);
}

@DeleteMapping("/preview-grants/{grantId}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void revoke(@PathVariable UUID grantId,
        @RequestHeader("X-Staff-Session") String token) {
    previews.revoke(token, grantId);
}
```

- [x] **Step 6: GREEN과 역할·정리 회귀 확인**

```powershell
.\mvnw.cmd '-Dtest=WebsitePreviewGrantIntegrationTest#issuesHashedVersionBoundGrantAndRevokesItWithContentRoles+rejectsBranchStaleAndMissingEnglishGrantRequests+replacesOnlyTheSameIssuersActiveGrantAndCleansOldRows' test
```

Expected: 3 tests pass, failures 0, errors 0.

- [x] **Step 7: 커밋**

```powershell
git add -- src/main/resources/db/migration/V24__create_website_preview_grant.sql src/main/java/team/hotelchain/webcontent/WebsitePreviewGrantRequest.java src/main/java/team/hotelchain/webcontent/WebsitePreviewGrantResponse.java src/main/java/team/hotelchain/webcontent/WebsitePreviewNotFoundException.java src/main/java/team/hotelchain/webcontent/WebsitePreviewUnavailableException.java src/main/java/team/hotelchain/webcontent/WebsitePreviewGrantService.java src/main/java/team/hotelchain/webcontent/WebsitePreviewGrantController.java src/test/java/team/hotelchain/webcontent/WebsitePreviewGrantIntegrationTest.java
git commit -m "feat: issue expiring website preview grants"
```

### Task 2: 읽기 전용 한국어·영어 초안 preview API

**Files:**
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePreviewResult.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/PublicWebsitePreviewController.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePreviewGrantService.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageService.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteTranslationService.java`
- Modify: `services/api/src/main/java/team/hotelchain/web/ApiExceptionHandler.java`
- Test: `services/api/src/test/java/team/hotelchain/webcontent/WebsitePreviewGrantIntegrationTest.java`

**Interfaces:**
- Consumes: Task 1 hashed grant row
- Produces: `WebsitePageService.previewDraft(UUID, int, String): PublishedWebsitePage`
- Produces: `WebsiteTranslationService.previewDraft(UUID, int, String): PublishedWebsitePage`
- Produces: `WebsitePreviewGrantService.resolve(String, String, String): WebsitePreviewResult`
- Produces: `GET /api/website/pages/preview?path=...&locale=...`

- [x] **Step 1: 실패하는 응답·불변성 HTTP 테스트 작성**

MockMvc를 application context와 `ApiExceptionHandler`로 구성한다. HOME_PAGE, HOTEL_LANDING, CONTENT_PAGE의 한국어 및 영어 저장 초안을 공개본과 다른 literal title로 만든다. grant를 발급한 뒤 public endpoint가 저장 초안을 반환하고 공개 resolve는 이전 발행본을 유지하는지 검증한다.

```java
mvc.perform(get("/api/website/pages/preview")
        .param("path", grant.previewPath())
        .param("locale", "ko")
        .header("X-Website-Preview", grant.previewToken()))
    .andExpect(status().isOk())
    .andExpect(header().string("Cache-Control", containsString("no-store")))
    .andExpect(header().string("X-Website-Preview-Expires-At", grant.expiresAt().toString()))
    .andExpect(jsonPath("$.content.blocks[0].title").value("저장된 한국어 초안"));
```

같은 GET을 두 번 호출하고 전후 `website_preview_grant`, `website_page`, `website_page_translation`, `website_page_audit` count와 JSON/version을 비교한다.

- [x] **Step 2: RED 확인**

```powershell
.\mvnw.cmd '-Dtest=WebsitePreviewGrantIntegrationTest#rendersSavedKoreanAndEnglishDraftsWithoutChangingPublishedState' test
```

Expected: `PublicWebsitePreviewController` 또는 `resolve`가 없어 실패한다.

- [x] **Step 3: locale별 저장 초안 응답 adapter 구현**

`WebsitePageService.previewDraft`는 한 SQL에서 id, active lifecycle, 허용 page type, 기대 draft version과 기대 draft path를 모두 확인한다. 성공하면 기존 `responseContent(...)`와 `pageConnections(page.id(), "DRAFT")`를 재사용한다.

```java
@Transactional(readOnly = true)
PublishedWebsitePage previewDraft(UUID pageId, int expectedDraftVersion, String expectedPath) {
    PageRow page = jdbc.query("select " + PAGE_COLUMNS + " from website_page "
            + "where id = ? and lifecycle_status = 'ACTIVE' "
            + "and page_type in ('HOME_PAGE','HOTEL_LANDING','CONTENT_PAGE') "
            + "and draft_version = ? and draft_path = ?",
            rs -> rs.next() ? row(rs) : null, pageId, expectedDraftVersion, expectedPath);
    if (page == null) throw new WebsitePreviewUnavailableException();
    return new PublishedWebsitePage(page.id(), page.pageType(), contentKind(page), page.draftPath(),
            page.hotelId(), responseContent(page, page.draftContent(), page.draftVersion()),
            pageConnections(page.id(), "DRAFT"));
}
```

`WebsiteTranslationService.previewDraft`는 `website_page_translation`과 `website_page`를 한 번에 join해 같은 조건을 검증하고 translation의 `draft_content`, `draft_connections`, `draft_path`를 `PublishedWebsitePage`로 조립한다. 별도 React renderer나 snapshot을 만들지 않는다. row 없음은 `WebsitePreviewUnavailableException`이다.

- [x] **Step 4: token 검증과 public controller 구현**

```java
public record WebsitePreviewResult(PublishedWebsitePage page, Instant expiresAt) {}

@Transactional(readOnly = true)
public WebsitePreviewResult resolve(String rawToken, String path, String requestedLocale)
```

blank 또는 형식이 다른 token은 hash 조회 전에 `WebsitePreviewNotFoundException`이다. hash에 해당하는 grant가 없거나 요청 path/locale가 저장값과 다르면 같은 404다. 알려진 grant의 `revoked_at != null || !expires_at.isAfter(clock.instant())`이면 410이다. 유효 grant는 locale에 따라 Step 3 adapter를 호출한다. raw token과 전체 URL을 message·log에 넣지 않는다.

```java
@GetMapping
public ResponseEntity<PublishedWebsitePage> preview(
        @RequestParam String path,
        @RequestParam String locale,
        @RequestHeader("X-Website-Preview") String token) {
    WebsitePreviewResult result = previews.resolve(token, path, locale);
    return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header("X-Website-Preview-Expires-At", result.expiresAt().toString())
            .body(result.page());
}
```

- [x] **Step 5: 404·410 exception handler 연결**

```java
@ExceptionHandler(WebsitePreviewNotFoundException.class)
@ResponseStatus(HttpStatus.NOT_FOUND)
public ApiError previewNotFound(WebsitePreviewNotFoundException error) {
    return new ApiError(error.code(), error.getMessage());
}

@ExceptionHandler(WebsitePreviewUnavailableException.class)
@ResponseStatus(HttpStatus.GONE)
public ApiError previewUnavailable(WebsitePreviewUnavailableException error) {
    return new ApiError(error.code(), error.getMessage());
}
```

- [x] **Step 6: 실패 경계와 공개 비변경 회귀 추가**

unknown, malformed, cross-page/path/locale는 404를 검증한다. 고정 clock을 만료 시각 직전과 정확한 만료 시각으로 이동해 전자는 200, 후자는 410임을 확인한다. grant 발급 뒤 한국어·영어 초안 저장, page path 이동, archive를 각각 수행해 410을 확인한다. 어떤 오류도 `/api/website/pages/resolve` 응답으로 fallback하지 않는다.

```java
mvc.perform(get("/api/website/pages/preview")
        .param("path", "/brand/other")
        .param("locale", "ko")
        .header("X-Website-Preview", grant.previewToken()))
    .andExpect(status().isNotFound())
    .andExpect(jsonPath("$.code").value("WEBSITE_PREVIEW_NOT_FOUND"));
```

- [x] **Step 7: 서버 클래스 전체 GREEN 확인**

```powershell
.\mvnw.cmd '-Dtest=WebsitePreviewGrantIntegrationTest' test
```

Expected: 해당 클래스 전체 통과, failures 0, errors 0. SQL query logger나 application logger에 `X-Website-Preview` 값이 전달되는 코드가 없어야 한다.

- [ ] **Step 8: 커밋**

```powershell
git add -- src/main/java/team/hotelchain/webcontent/WebsitePreviewResult.java src/main/java/team/hotelchain/webcontent/PublicWebsitePreviewController.java src/main/java/team/hotelchain/webcontent/WebsitePreviewGrantService.java src/main/java/team/hotelchain/webcontent/WebsitePageService.java src/main/java/team/hotelchain/webcontent/WebsiteTranslationService.java src/main/java/team/hotelchain/web/ApiExceptionHandler.java src/test/java/team/hotelchain/webcontent/WebsitePreviewGrantIntegrationTest.java
git commit -m "feat: serve version-bound saved draft previews"
```

### Task 3: 관리자 링크 발급·복사·폐기 UI

**Files:**
- Modify: `SDTPL_ADM/src/lib/staff-api.ts`
- Create: `SDTPL_ADM/src/components/hotel-admin/website-saved-draft-preview-action.tsx`
- Modify: `SDTPL_ADM/src/components/hotel-admin/content-page-editor.tsx`
- Modify: `SDTPL_ADM/src/components/hotel-admin/website-content-editor.tsx`
- Create/Test: `SDTPL_ADM/e2e/website-saved-draft-preview.spec.ts`

**Interfaces:**
- Consumes: Task 1 staff endpoints and `NEXT_PUBLIC_CUSTOMER_WEB_ORIGIN`
- Produces: `issueWebsitePreviewGrant(...)`, `revokeWebsitePreviewGrant(...)`
- Produces: `WebsiteSavedDraftPreviewAction`

- [x] **Step 1: 실패하는 관리자 Playwright 흐름 작성**

새 spec는 API를 literal fixtures로 intercept한다. 한국어 CONTENT_PAGE에서 action을 눌렀을 때 요청 body와 popup URL을 검증하고, 응답 뒤 복사·폐기 상태를 확인한다.

```ts
expect(issueBody).toEqual({ locale: "ko", expectedDraftVersion: 3 });
expect(openedUrl).toBe(
  "http://127.0.0.1:4000/brand/story#preview=abcdefghijklmnopqrstuvwxyzABCDEFGH123456789",
);
await expect(page.getByText("2026. 9. 13. 오전 10:10에 만료")).toBeVisible();
await page.getByRole("button", { name: "링크 폐기" }).click();
expect(revokedGrantId).toBe("preview-grant-1");
```

별도 테스트에서 편집 후 버튼 disabled, popup `null`일 때 `링크 복사`, 영어 초안 version 0일 때 action 없음, Escape/닫기 focus 복귀와 390px overflow를 검증한다.

- [x] **Step 2: RED 확인**

Run from `SDTPL_ADM`:

```powershell
pnpm exec playwright test e2e/website-saved-draft-preview.spec.ts
```

Expected: `실제 화면 미리보기` action이 없어 실패한다.

- [x] **Step 3: staff API 타입과 함수 구현**

```ts
export type WebsitePreviewGrantResponse = {
  grantId: string;
  previewToken: string;
  previewPath: string;
  expiresAt: string;
};

export function issueWebsitePreviewGrant(
  token: string,
  pageId: string,
  input: { locale: "ko" | "en"; expectedDraftVersion: number },
) {
  return contentRequest<WebsitePreviewGrantResponse>(
    `/api/staff/website/pages/${pageId}/preview-grants`, token,
    { method: "POST", body: JSON.stringify(input) },
  );
}
```

`revokeWebsitePreviewGrant(token, grantId): Promise<void>`는 DELETE와 `X-Staff-Session`을 보내고 `204`에서는 JSON을 읽지 않는다. 오류 body를 읽어 기존 `StaffApiError(status, code)`로 변환한다.

- [x] **Step 4: 재사용 가능한 preview action 구현**

```ts
type WebsiteSavedDraftPreviewActionProps = {
  token: string;
  pageId: string;
  locale: "ko" | "en";
  draftVersion: number;
  draftPath: string;
  dirty: boolean;
  disabled?: boolean;
  onBusyChange?: (busy: boolean) => void;
};
```

버튼은 dirty, draftVersion 0, archived/외부 busy일 때 disabled하고 저장 안내 title을 제공한다. 사용자 click 시 popup 차단을 피하기 위해 동기적으로 빈 탭을 먼저 요청하고 opener를 제거한다. grant 성공 뒤에만 fragment URL을 메모리에서 만들고 빈 탭을 이동시킨다. 빈 탭 생성 실패나 사용자가 응답 전에 탭을 닫은 경우에는 복사 UI로 전환하고, grant 실패 시 열린 빈 탭을 닫는다.

```ts
const popup = window.open("about:blank", "_blank");
if (popup) popup.opener = null;
const grant = await issueWebsitePreviewGrant(token, pageId, { locale, expectedDraftVersion: draftVersion });
const previewUrl = `${customerWebOrigin}${grant.previewPath}#preview=${grant.previewToken}`;
if (popup && !popup.closed) popup.location.replace(previewUrl);
setGrant({ ...grant, previewUrl, popupBlocked: !popup || popup.closed });
```

성공 Dialog에는 만료 시각, 10분 동안 링크 소지자가 볼 수 있다는 안내, `링크 복사`, `링크 폐기`, 닫기를 둔다. clipboard 실패는 role alert로 표시한다. 폐기 성공 시 원문 link state를 즉시 지운다. request generation ref로 닫기·page/locale 변경 뒤 늦게 도착한 응답을 무시한다. busy 동안 중복 발급·복사·폐기를 막고 닫힌 뒤 trigger focus를 복원한다.

- [x] **Step 5: 세 page type과 locale editor에 연결**

`ContentPageEditor`는 자체 `previewBusy`를 두고 기존 `busy`와 합쳐 `onBusyChange`에 전달한다. HOME_PAGE와 CONTENT_PAGE, 영어 translation 편집기에서 `document.id/draftVersion/draftMetadata.path`, locale, 내부 dirty를 action에 넘긴다.

`WebsiteContentEditor`의 legacy 한국어 HOTEL_LANDING header에는 현재 tree의 landing page id와 `draftVersion`, `draftPath`, `dirty`, `busy || previewBusy`를 넘긴다. page/locale 전환 guard에도 `previewBusy`를 포함한다. 기존 인메모리 `미리보기` action과 Dialog는 삭제하거나 이름을 바꾸지 않는다.

- [x] **Step 6: GREEN 및 관리자 접근성 회귀 확인**

```powershell
pnpm exec playwright test e2e/website-saved-draft-preview.spec.ts
pnpm exec tsc --noEmit
pnpm exec eslint src/lib/staff-api.ts src/components/hotel-admin/website-saved-draft-preview-action.tsx src/components/hotel-admin/content-page-editor.tsx src/components/hotel-admin/website-content-editor.tsx e2e/website-saved-draft-preview.spec.ts
```

Expected: preview E2E 전체 통과, TypeScript exit 0, ESLint error 0. 390px에서 `scrollWidth <= innerWidth`, Escape/닫기 후 trigger focus를 확인한다.

- [ ] **Step 7: 커밋**

```powershell
git add -- src/lib/staff-api.ts src/components/hotel-admin/website-saved-draft-preview-action.tsx src/components/hotel-admin/content-page-editor.tsx src/components/hotel-admin/website-content-editor.tsx e2e/website-saved-draft-preview.spec.ts
git commit -m "feat: issue saved draft preview links"
```

### Task 4: 고객 fragment와 preview API session primitive

**Files:**
- Create: `apps/web/src/lib/website-preview.ts`
- Create/Test: `apps/web/src/lib/website-preview.test.ts`
- Modify: `apps/web/src/lib/api.ts`

**Interfaces:**
- Consumes: Task 2 public preview endpoint
- Produces: `captureWebsitePreview(...)`, `storedWebsitePreviewForPath(...)`, `clearWebsitePreview(...)`
- Produces: `api.websitePreviewPage(path, locale, token): Promise<WebsitePreviewPageResponse>`

- [x] **Step 1: 실패하는 순수 session 테스트 작성**

DOM 없이 Map 기반 Storage fake와 history spy를 사용한다. 정확한 43자 URL-safe token 하나만 수용하고 fragment 제거, path 결합, reload 복원, malformed JSON 제거, 다른 path에서 session 제거를 literal assertion으로 검증한다.

```ts
const session = captureWebsitePreview(
  { pathname: '/brand/story', search: '?from=cms', hash: `#preview=${TOKEN}` },
  storage,
  nextUrl => replacedUrl = nextUrl,
)
expectEqual(session, { token: TOKEN, path: '/brand/story' }, 'fragment를 session으로 옮긴다')
expectEqual(replacedUrl, '/brand/story?from=cms', '주소에서 token fragment를 제거한다')
expectEqual(storedWebsitePreviewForPath('/brand/other', storage), null, '다른 경로에서는 session을 폐기한다')
```

- [x] **Step 2: RED 확인**

Run from `apps/web`:

```powershell
node --experimental-strip-types src/lib/website-preview.test.ts
```

Expected: `website-preview.ts` module을 찾지 못해 실패한다.

- [x] **Step 3: 최소 session helper 구현**

```ts
export type WebsitePreviewSession = { token: string; path: string }
export const WEBSITE_PREVIEW_STORAGE_KEY = 'website-preview'
const PREVIEW_FRAGMENT = /^#preview=([A-Za-z0-9_-]{43})$/

export function captureWebsitePreview(
  location: Pick<Location, 'pathname' | 'search' | 'hash'>,
  storage: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>,
  replaceUrl: (url: string) => void,
): WebsitePreviewSession | null

export function storedWebsitePreviewForPath(
  pathname: string,
  storage: Pick<Storage, 'getItem' | 'removeItem'>,
): WebsitePreviewSession | null

export function clearWebsitePreview(storage: Pick<Storage, 'removeItem'>): void
```

fragment가 정확히 일치할 때만 저장하고 `replaceUrl(pathname + search)`를 호출한다. 저장 JSON이 malformed이거나 token/path 형식이 틀리면 제거한다. 저장 path와 현재 normalized path가 다르면 제거한다. 함수는 token을 출력하거나 error message에 넣지 않는다.

- [x] **Step 4: preview API 응답·만료 header 읽기 구현**

```ts
export type WebsitePreviewPageResponse = {
  page: PublishedWebsitePage;
  expiresAt: string;
};

async function websitePreviewPage(path: string, locale: 'ko' | 'en', token: string) {
  const response = await fetch(`/api/website/pages/preview?${new URLSearchParams({ path, locale })}`, {
    headers: { 'X-Website-Preview': token },
    cache: 'no-store',
    credentials: 'omit',
  })
  if (!response.ok) throw await apiFailure(response)
  const expiresAt = response.headers.get('X-Website-Preview-Expires-At')
  if (!expiresAt) throw new ApiFailure('WEBSITE_PREVIEW_UNAVAILABLE', '미리보기 만료 정보를 확인할 수 없습니다.', 410)
  return { page: await response.json() as PublishedWebsitePage, expiresAt }
}
```

기존 `request`의 오류 body 처리를 `apiFailure(response)`로 한 번 추출해 공개 resolve와 preview가 같은 `ApiFailure` 계약을 사용하게 한다. preview 요청에 reservation token/cookie를 추가하지 않는다.

- [x] **Step 5: GREEN과 타입 검사**

```powershell
node --experimental-strip-types src/lib/website-preview.test.ts
pnpm exec tsc -b
```

Expected: 순수 test exit 0, TypeScript exit 0.

- [ ] **Step 6: 커밋**

```powershell
git add -- src/lib/website-preview.ts src/lib/website-preview.test.ts src/lib/api.ts
git commit -m "feat: capture website preview sessions safely"
```

### Task 5: 고객 실제 renderer와 안전한 preview UI

**Files:**
- Create: `apps/web/src/components/website-preview-banner.tsx`
- Modify: `apps/web/src/components/content-page.tsx`
- Modify: `apps/web/src/components/concierge-panel.tsx`
- Modify: `apps/web/src/App.tsx`
- Modify: `apps/web/src/styles.css`
- Create: `SDTPL_ADM/playwright.customer.config.ts`
- Create/Test: `SDTPL_ADM/e2e/customer-saved-draft-preview.spec.ts`

**Interfaces:**
- Consumes: Task 4 session과 API
- Produces: `WebsitePreviewBanner({ expiresAt, locale, onExpire, onExit })`
- Produces: `ContentPage*`의 `previewMode?: boolean`
- Produces: customer route의 `active | unavailable | none` preview 상태

- [x] **Step 1: 실패하는 고객 E2E 작성**

고객 전용 config는 `pnpm --dir ../apps/web dev`로 4000 Vite를 구동하고 새 spec만 대상으로 실행할 수 있게 한다. E2E는 모든 `/api/**`를 intercept하고 public preview 요청 header와 공개 resolve 호출 수를 기록한다.

```ts
await page.goto(`${customerUrl}/brand/story#preview=${TOKEN}`)
await expect(page).toHaveURL(`${customerUrl}/brand/story`)
await expect(page.getByRole('status', { name: '저장된 초안 미리보기' })).toBeVisible()
await expect(page.getByRole('heading', { name: '저장된 초안 제목' })).toBeVisible()
expect(previewHeader).toBe(TOKEN)
expect(publicResolveCount).toBe(0)
expect(await page.evaluate(() => sessionStorage.getItem('website-preview'))).toContain('/brand/story')
await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', 'noindex,nofollow')
```

추가 테스트는 reload 재사용, CTA·예약·결제·AI action 무호출, 다른 메뉴 이동 시 token 삭제와 공개 resolve, 410 전용 오류와 명시적인 `공개 페이지 보기`, 390px overflow를 검증한다. API mutation count는 availability/reservations/payment/cancel/concierge별로 0이어야 한다.

- [x] **Step 2: RED 확인**

Run from `SDTPL_ADM`:

```powershell
pnpm exec playwright test --config=playwright.customer.config.ts e2e/customer-saved-draft-preview.spec.ts
```

Expected: fragment가 남거나 preview endpoint를 호출하지 않아 실패한다.

- [x] **Step 3: App preview state와 비fallback load 구현**

최초 state initializer에서 `captureWebsitePreview(window.location, sessionStorage, url => history.replaceState({}, '', url))`를 호출한다. 현재 route와 session path가 일치하면 `api.websitePreviewPage`만 호출하고, 성공 body를 기존 HOME_PAGE/HOTEL_LANDING/CONTENT_PAGE 분기로 전달한다. preview API가 404/410이면 기존 `api.websitePage`를 호출하지 않고 `previewUnavailable` 상태로 전환한다.

다른 path로 `navigateToPath`를 실행하기 전에 `clearWebsitePreview`와 preview state 초기화를 수행한다. `미리보기 종료`와 `공개 페이지 보기`는 token을 지우고 현재 path에서 명시적으로 공개 load를 시작한다. 만료 callback은 draft content를 즉시 지우고 unavailable 상태를 표시한다.

preview session이 현재 path에 있는 동안 meta `robots=noindex,nofollow`를 만들거나 덮어쓰고, 종료 시 기존 값 또는 meta 부재 상태를 복원한다.

- [x] **Step 4: 배너·오류·만료 UI 구현**

```tsx
<WebsitePreviewBanner
  expiresAt={previewExpiresAt}
  locale={locale}
  onExpire={expirePreview}
  onExit={showPublishedPage}
/>
```

배너는 `role="status" aria-label="저장된 초안 미리보기"`, 만료 시각, `미리보기 종료`를 제공한다. `setTimeout(max(0, expiresAt-now))`으로 client 만료를 알리고 cleanup한다. 오류 화면은 `미리보기를 사용할 수 없음`과 `공개 페이지 보기`만 제공하며 draft/public body를 같이 렌더링하지 않는다.

- [x] **Step 5: 예약·결제·CTA를 UI와 handler 양쪽에서 차단**

`previewMode`일 때 `searchAvailability`, `applyConciergeCriteria`, `applyContentBookingIntent`, `chooseOffer`, `reserve`, `pay`, `cancel`은 API·state mutation 전에 즉시 반환한다. 기존 `latestReservation` 복원 effect도 preview session에서는 `api.getReservation`을 호출하지 않는다. preview 진입 시 화면의 offers·selected·reservation state는 비우되 기존 예약 복구용 sessionStorage 값은 삭제하지 않는다. booking fieldset, 객실 선택, 예약/결제/취소 버튼, 예약 조회 링크를 disabled 또는 `aria-disabled`로 표시한다. `ConciergePanel`에는 `disabled?: boolean`을 추가해 submit과 적용을 모두 막는다.

`ContentPage`, `ContentPageHero`, `ContentPageAfterHero`, 내부 block view에 `previewMode`를 전달한다. HERO/CTA anchor는 href navigation을 제거한 `aria-disabled` action으로, `BOOKING_CTA`는 disabled button으로 렌더링한다. 지점 landing과 홈의 `#booking` CTA도 click preventDefault와 `aria-disabled`를 함께 사용한다. 헤더·푸터의 일반 page navigation은 유지하고 클릭 시 Task 5 Step 3 종료 흐름을 탄다.

- [x] **Step 6: 스타일과 390px 접근성 마무리**

배너는 콘텐츠를 가리지 않는 sticky top 영역으로 두고 작은 화면에서 문구·만료·종료 버튼이 세로로 줄바꿈된다. `[aria-disabled="true"]`는 pointer cursor와 opacity만 바꾸지 말고 focus 대상 제거 또는 native disabled와 같이 사용한다. 오류 상태와 버튼은 기존 색·spacing token을 재사용한다.

- [x] **Step 7: 고객 GREEN과 build 확인**

```powershell
pnpm exec playwright test --config=playwright.customer.config.ts e2e/customer-saved-draft-preview.spec.ts
```

Run from `apps/web`:

```powershell
node --experimental-strip-types src/lib/website-preview.test.ts
pnpm build
```

Expected: customer preview E2E 전체 통과, pure test exit 0, Vite production build exit 0. 404/410 test의 `publicResolveCount`는 공개 전환 버튼을 누르기 전 0이다.

- [ ] **Step 8: 커밋**

```powershell
git add -- ../apps/web/src/components/website-preview-banner.tsx ../apps/web/src/components/content-page.tsx ../apps/web/src/components/concierge-panel.tsx ../apps/web/src/App.tsx ../apps/web/src/styles.css playwright.customer.config.ts e2e/customer-saved-draft-preview.spec.ts
git commit -m "feat: render safe saved draft previews"
```

### Task 6: 문서와 최종 직접 검증

**Files:**
- Modify: `docs/architecture/cms-functional-specification.md`
- Modify: `docs/overview/current-development-context.md`
- Create: `docs/changes/2026-09-13-authenticated-saved-draft-url-preview.md`

**Interfaces:**
- Consumes: Task 1~5 실제 API, UI, test 결과
- Produces: 한국어 구현·검증·미검증 기록

- [x] **Step 1: 한국어 문서 갱신**

변경 기록에 구현 범위, 보안 불변 조건, 직접 검증 결과, 미검증 항목을 작성한다.

```markdown
## 구현
- 저장된 최신 한국어·영어 초안에 페이지·locale·version·경로 결합 10분 grant를 발급한다.
- 원문 token은 fragment에서 즉시 제거하고 DB에는 SHA-256 hash만 저장한다.
- 고객은 기존 renderer를 사용하며 예약·결제·CTA를 실행하지 않는다.

## 검증
- 서버 대상 통합 테스트의 tests run·failures·errors·skipped 수를 기록한다.
- 관리자·고객 Playwright의 실제 통과 수와 viewport를 기록한다.
- 관리자 TypeScript/ESLint와 고객 pure test/build의 exit code를 기록한다.

## 미검증과 다음 작업
- 실제 사용자 CMS·예약·결제 mutation은 보내지 않았다.
- 전체 서버 suite와 전체 브라우저 회귀의 실행 여부를 사실대로 기록한다.
- 영구 공유, 댓글, PDF, draft navigation tree는 후속 범위다.
```

`cms-functional-specification.md`의 저장 초안 preview 후속 항목을 구현된 계약으로 바꾸고, `current-development-context.md`의 기존 인메모리 preview와 새 실제 URL preview의 역할을 구분한다. 검증 수치는 명령 출력에서만 가져온다.

- [x] **Step 2: 서버 대상 통합 재검증**

Run from `services/api`:

```powershell
.\mvnw.cmd '-Dtest=WebsitePreviewGrantIntegrationTest' test
```

Expected: failures 0, errors 0.

- [x] **Step 3: 관리자 대상 재검증**

Run from `SDTPL_ADM`:

```powershell
pnpm exec playwright test e2e/website-saved-draft-preview.spec.ts
pnpm exec tsc --noEmit
pnpm exec eslint src/lib/staff-api.ts src/components/hotel-admin/website-saved-draft-preview-action.tsx src/components/hotel-admin/content-page-editor.tsx src/components/hotel-admin/website-content-editor.tsx e2e/website-saved-draft-preview.spec.ts
```

Expected: Playwright failures 0, TypeScript exit 0, ESLint error 0. 기존 파일 warning은 새 오류와 분리해 기록한다.

- [x] **Step 4: 고객 대상 재검증**

Run from `SDTPL_ADM`:

```powershell
pnpm exec playwright test --config=playwright.customer.config.ts e2e/customer-saved-draft-preview.spec.ts
```

Run from `apps/web`:

```powershell
node --experimental-strip-types src/lib/website-preview.test.ts
pnpm build
```

Expected: 고객 Playwright failures 0, pure test와 build exit 0.

- [x] **Step 5: 변경 범위·보안 문자열·공백 검사**

```powershell
rg -n "X-Website-Preview|previewToken|website-preview" services/api/src/main SDTPL_ADM/src apps/web/src
git diff --check
git status --short
```

Expected: raw token을 log/analytics/localStorage에 보내는 코드 0건, 공백 오류 0. 기존 dirty 파일은 사용자 변경과 이번 hunk를 구분해 검토하며 unrelated 파일을 stage하지 않는다.

- [ ] **Step 6: 문서 커밋**

```powershell
git add -- docs/architecture/cms-functional-specification.md docs/overview/current-development-context.md docs/changes/2026-09-13-authenticated-saved-draft-url-preview.md
git commit -m "docs: record authenticated saved draft previews"
```

## 최종 중단 조건

### 2026-09-13 실행 결과

- Task 1~6의 기능 구현과 대상 직접 검증 완료. 서버 55개, 관리자 6개, 고객 14개, session 25개 assertion, preview API 계약 검사, 관리자 타입/ESLint(오류 0·경고 8), 고객 build를 통과했다.
- Task 1 독립 파일은 `d8b6517`에 커밋했다. Task 2~5 및 겹치는 기존 문서의 커밋 단계는 사용자 변경을 함께 stage하지 않기 위해 의도적으로 보류하고 현재 작업 트리에 보존한다. 새 변경 기록과 이 계획의 실행 결과만 별도 문서 커밋으로 기록한다.
- 별도 읽기 전용 검토에서 깊은 영어 경로, 호텔 의존성 실패, 지도 CTA, 예약 입력, HTTPS와 clipboard 응답 경쟁을 보완하고 재검토했다. 저장소 getter 차단도 실패 재현 후 수정했다. 추가한 HTTPS guard는 승인 설계의 운영 전송 제한을 구현한 것이며 로컬 `dev`만 예외다.
- 기존 계획 외 연결은 별도 지점 영어 editor에 같은 action을 적용한 범위와 위 안전장치뿐이다. 전체 suite·실제 사용자 저장/발행/예약/결제·운영 배포는 실행하지 않았다.
- 상세 결과와 운영 설정은 [변경 기록](../../changes/2026-09-13-authenticated-saved-draft-url-preview.md)을 따른다. 남은 것은 배포 HTTPS/proxy 확인과 사용자 변경을 분리한 통합 커밋이며, 기능 범위를 확대하지 않는다.

- grant 발급·폐기, 한국어·영어 세 page type 응답, 404/410 격리, GET 무변경, 관리자 UI, 고객 안전 렌더링이 모두 직접 검증되면 중단한다.
- 전체 suite, 영구 공유, 댓글, PDF, draft navigation tree, CDN 변경, 실제 사용자 CMS·예약·결제 mutation으로 범위를 넓히지 않는다.
- unrelated failure가 나오면 원인과 미검증 항목을 기록하고 범위 밖 코드를 수정하지 않는다.
