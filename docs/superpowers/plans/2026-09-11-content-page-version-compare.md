# 일반 콘텐츠 페이지 발행 이력 비교 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 본사 관리자가 같은 일반 콘텐츠 페이지의 두 발행본 차이를 초안·공개본을 바꾸지 않고 확인하게 한다.

**Architecture:** Spring은 `website_page_version`의 두 immutable snapshot을 한 번에 읽어 반환하고, Next 관리자만 정해진 SEO/HERO/TEXT/CTA 필드를 비교해 읽기 전용 dialog로 표시한다. 비교 조회는 미디어 현재 상태와 수명주기를 바꾸거나 요구하지 않는다.

**Tech Stack:** Java 21, Spring Boot, PostgreSQL 16, Next.js/React/TypeScript, Playwright.

**Spec:** `docs/superpowers/specs/2026-09-11-content-page-version-compare-design.md`

## Global Constraints

- 대상은 `CONTENT_PAGE`만이며 `HOME_PAGE`, `HOTEL_LANDING`, `SECTION`은 거부한다.
- `ACTIVE`와 `ARCHIVED` 모두 같은 페이지의 발행 이력을 읽을 수 있다.
- `baseVersion`과 `compareVersion`은 양수이고 `baseVersion < compareVersion`이어야 한다.
- 비교는 DB row·draft·public·lifecycle·audit·media usage를 변경하지 않는다.
- 현재 활성 미디어 검증이나 이미지 URL fetch를 하지 않는다.
- 기존 사용자 작업 트리에서 진행하며 별도 branch, worktree, commit을 만들지 않는다.

---

### Task 1: 읽기 전용 비교 API와 무결성 보호

**Files:**
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageVersionSnapshot.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageVersionComparison.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageService.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageManagementController.java`
- Test: `services/api/src/test/java/team/hotelchain/webcontent/WebsitePageIntegrationTest.java`

**Interfaces:**
- Consumes: `GET /api/staff/website/pages/{pageId}/versions/compare?baseVersion={int}&compareVersion={int}`.
- Produces: `WebsitePageService.compareContentPageVersions(String token, UUID pageId, int baseVersion, int compareVersion): WebsitePageVersionComparison`.
- Produces: `WebsitePageVersionComparison(UUID pageId, WebsitePageVersionSnapshot base, WebsitePageVersionSnapshot compare)`.

- [x] **Step 1: 비교가 쓰지 않는다는 실패 통합 테스트를 작성한다.**

`WebsitePageIntegrationTest`에서 하나의 일반 페이지를 서로 다른 metadata·SEO·HERO·TEXT/CTA로 두 번 발행한다. 비교 전 draft/public 문서, lifecycle version, `website_page_audit` count, `website_media_usage` count를 읽고 `compareContentPageVersions`를 호출한 뒤 모두 같음을 검증한다. `base`와 `compare`의 version·publishedAt·metadata·publishedFromDraftVersion·content가 각 historical publish 시점과 일치하는지도 검증한다.

- [x] **Step 2: 비교 실패 기준을 고정한다.**

같은 테스트에 다음 assertions를 추가한다.

```java
assertThatThrownBy(() -> pages.compareContentPageVersions(hq.token(), page.id(), 0, 3))
    .isInstanceOf(IllegalArgumentException.class);
assertThatThrownBy(() -> pages.compareContentPageVersions(hq.token(), page.id(), 3, 3))
    .isInstanceOf(IllegalArgumentException.class);
assertThatThrownBy(() -> pages.compareContentPageVersions(hq.token(), page.id(), 3, 2))
    .isInstanceOf(IllegalArgumentException.class);
assertThatThrownBy(() -> pages.compareContentPageVersions(branch.token(), page.id(), 2, 3))
    .isInstanceOf(StaffAccessDeniedException.class);
```

페이지에 없는 version과 `SECTION`은 `WebsitePageNotFoundException`이어야 한다. 보관한 `CONTENT_PAGE`의 historical versions는 여전히 비교되어야 한다.

- [x] **Step 3: snapshot DTO와 서버 읽기 구현을 작성한다.**

서비스에 `@Transactional(readOnly = true)` 비교 메서드를 추가한다. 대상 row가 `CONTENT_PAGE`인지 확인하고, 양수·오름차순 version을 확인한다. snapshot reader는 아래처럼 page scope를 고정한다.

```java
select page_snapshot::text, published_at
from website_page_version
where page_id = ? and version = ?
```

reader는 snapshot envelope의 `pageType`, `hotelId`, `parentId`, `slug`, `path`, `menu`, `publishedFromDraftVersion`, `content`를 검증해 `WebsitePageVersionSnapshot`을 만든다. `contentPages.validate(content)`만 사용하며 `mediaReferences.normalizeStructuredContent`는 호출하지 않는다. controller는 GET route와 두 `@RequestParam int`를 서비스에 연결한다.

- [x] **Step 4: 대상 서버 테스트를 통과시킨다.**

Run:

```powershell
# services/api
& .\mvnw.cmd '-Dmaven.repo.local=C:\Users\jowoo\.m2\repository' '-Dtest=WebsitePageIntegrationTest' test
```

Expected: 비교 결과·불변성·보관·권한·version validation을 포함한 `WebsitePageIntegrationTest`가 통과한다.

### Task 2: 관리자 발행본 비교 dialog

**Files:**
- Create: `SDTPL_ADM/src/components/hotel-admin/content-page-version-compare-dialog.tsx`
- Modify: `SDTPL_ADM/src/lib/staff-api.ts`
- Modify: `SDTPL_ADM/src/components/hotel-admin/website-content-editor.tsx`
- Test: `SDTPL_ADM/e2e/website-content-editor.spec.ts`

**Interfaces:**
- Consumes: `getWebsitePageVersionComparison(token, pageId, baseVersion, compareVersion): Promise<WebsitePageVersionComparison>`.
- Produces: `ContentPageVersionCompareDialog` with `open`, `versions`, `initialBaseVersion`, `initialCompareVersion`, `onOpenChange` props.
- Produces: only GET comparison requests; it does not call save, publish, archive, restore, or media endpoints.

- [x] **Step 1: dialog interaction E2E를 작성한다.**

CMS fixture에 v2, v3, v4 history와 comparison response를 route한다. `발행본 v2와 현재 발행본 비교` 버튼을 클릭해 dialog 제목·read-only 설명·v2/v4 metadata·SEO/HERO 값·`변경` 문구를 확인한다. Select로 v2/v3을 선택하고 두 번째 GET URL을 확인한다. `닫기`와 Escape 뒤 compare button focus를 확인하고, 해당 흐름에 POST/PUT request가 없음을 assert한다.

- [x] **Step 2: API client 타입과 GET 함수를 추가한다.**

`staff-api.ts`에 서버 DTO와 같은 `WebsitePageVersionSnapshot`, `WebsitePageVersionComparison` type을 추가한다. `URLSearchParams`로 `baseVersion`, `compareVersion`을 encode하여 다음 함수로 GET 한다.

```ts
getWebsitePageVersionComparison(token, pageId, baseVersion, compareVersion)
```

- [x] **Step 3: 읽기 전용 비교 component를 구현한다.**

새 dialog는 `Dialog`를 사용하고 `sm:max-w-6xl max-h-[90vh] overflow-y-auto`를 적용한다. 좁은 화면에서는 한 열, 넓은 화면에서는 두 열이다. metadata, SEO, HERO, TEXT, CTA를 읽기 전용으로 표시하고 값마다 `동일`, `변경`, `추가`, `제거` 텍스트 badge를 붙인다. 이미지 URL은 `<img>`로 요청하지 않고 asset ID·전달 경로·대체 텍스트를 문자열로만 보여 준다. 실패 시 `role="alert"`을 표시하고 dialog와 선택값은 유지한다.

- [x] **Step 4: 이력 카드에서 비교를 연다.**

`website-content-editor.tsx`는 `CONTENT_PAGE`의 이전 version 카드에 `발행본 vN과 현재 발행본 비교` 버튼을 추가한다. 미저장 초안과 lifecycle status는 이 읽기 전용 제어를 막지 않는다. 대화상자 안에서 현재 page의 version 목록만 선택할 수 있고 base가 compare보다 작도록 options를 제한한다. 기존 `restore-draft` 제어와 confirmation behavior는 변경하지 않는다.

- [x] **Step 5: 대상 관리자 검증을 통과시킨다.**

Run:

```powershell
# SDTPL_ADM
pnpm.cmd exec playwright test e2e/website-content-editor.spec.ts --project=chromium --grep "발행본.*비교"
pnpm.cmd exec tsc --noEmit
```

Expected: comparison E2E와 TypeScript 검사가 통과한다.

### Task 3: 문서와 읽기 전용 런타임 확인

**Files:**
- Modify: `docs/changes/2026-09-10-web-content-management.md`
- Modify: `docs/overview/current-development-context.md`
- Modify: `docs/architecture/full-site-implementation-design.md`
- Modify: `docs/overview/project-brief.md`
- Modify: `docs/decisions/decision-log.md`

- [x] **Step 1: 문서에 비교의 읽기 전용 계약을 기록한다.**

V16이 `CONTENT_PAGE` 두 발행본의 immutable snapshot을 읽고, archive도 허용하며, 초안·공개본·audit·media usage를 바꾸지 않는다는 점과 블록 이동 판정·현재 미디어 preview는 제외한다는 점을 기록한다.

- [x] **Step 2: Docker API와 공개 경로를 읽기 전용으로 확인한다.**

Run:

```powershell
docker compose up -d --build api
$ErrorActionPreference = 'Stop'
Invoke-RestMethod http://127.0.0.1:4080/actuator/health
Invoke-RestMethod 'http://127.0.0.1:4080/api/website/pages/resolve?path=%2Fbrand%2Fstory'
```

Expected: migration 없이 새 API 이미지가 기동하고 health가 `UP`이며 기존 공개 페이지 resolve가 유지된다. 실제 사용자 페이지에는 저장·발행·복원 요청을 보내지 않는다.

- [x] **Step 3: 공백 오류를 확인한다.**

Run:

```powershell
git diff --check
```

Expected: 공백 오류가 없고, 기존 수정 파일의 LF→CRLF 변환 경고만 출력될 수 있다.
