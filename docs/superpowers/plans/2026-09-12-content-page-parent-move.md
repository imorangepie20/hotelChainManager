# 콘텐츠 페이지 부모 이동·깊은 트리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 현재 공유 트리에서만 실행하며, branch·worktree·commit·reset·clean을 만들지 않는다.

**Goal:** 본사 관리자가 미발행 콘텐츠 트리를 최대 4단계 안에서 안전하게 이동하고, 발행 URL이 바뀔 가능성이 있는 이동은 차단한다.

**Architecture:** 페이지 이동을 기존 초안 저장과 분리한 command/impact API로 구현한다. 서버는 root와 하위 행을 잠근 뒤 부모 범위·깊이·경로 충돌·발행 여부를 검증하고, 성공 시 초안 경로·초안 version·audit만 원자적으로 갱신한다. 관리자 dialog는 impact read model을 먼저 보여 주며 고객 공개 API는 변경하지 않는다.

**Tech Stack:** Java 21, Spring Boot 4.1.1, PostgreSQL 16/Flyway, React 19·TypeScript·Next.js 16.2.7, Playwright Chromium.

**Spec:** `docs/superpowers/specs/2026-09-12-content-page-parent-move-design.md`

## Global Constraints

- `HOME_PAGE`, `HOTEL_LANDING`, 발행본 경로·메뉴·snapshot·published media usage는 이동으로 변경하지 않는다.
- `SECTION`과 `CONTENT_PAGE`의 ACTIVE 초안만 이동하며, 이동 root 또는 하위에 발행본이 있으면 `409 WEBSITE_PAGE_MOVE_PUBLISHED_DESCENDANT`로 거부한다.
- 새 초안 경로는 최대 4 segment이며, 자기/하위 이동·동일 위치·범위 불일치는 `400`, 경로 충돌은 `409`이다.
- 지점 소유 `ROOM`, `DINING`, `FACILITY`, `EXPERIENCE`, 지점 `GUIDE`는 같은 지점 범위의 부모만 사용한다. 체인 공통 kind는 체인 공통 부모만 사용한다.
- HQ 권한과 target의 expected draft/lifecycle version을 항상 요구한다. 실패 요청은 row·version·audit·media usage를 바꾸지 않는다.
- redirect, 발행 페이지 이동, 드래그 앤 드롭, 다국어, media replacement/deletion은 구현하지 않는다.
- 실제 사용자 페이지에는 move 요청을 보내지 않는다. runtime 확인은 공개 resolve/navigation 읽기만 사용한다.

### Task 1: 이동 command·impact 계약과 데이터 무결성

**Files:**
- Create: `services/api/src/main/resources/db/migration/V17__website_page_parent_move.sql`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/MoveWebsitePageRequest.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageMoveImpact.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageMoveImpactItem.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageManagementController.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageService.java`
- Test: `services/api/src/test/java/team/hotelchain/webcontent/WebsitePageIntegrationTest.java`

**Consumes:** `website_page.parent_id`, `draft_path`, `draft_version`, `lifecycle_status`, `published_from_draft_version` and existing `WebsitePageDraftMetadata` path validation.

**Produces:** `GET /api/staff/website/pages/{pageId}/move-impact?parentId={uuid}&slug={slug}` and `POST /api/staff/website/pages/{pageId}/move`, each requiring a headquarters session. The command body is `MoveWebsitePageRequest(UUID parentId, String slug, int expectedDraftVersion, int expectedLifecycleVersion)`.

- [ ] **Step 1: Write focused failing server tests for a permitted move and a read-only impact.**

  In `WebsitePageIntegrationTest`, create a fixture-only `SECTION /brand/seasonal` and an unpubished `CONTENT_PAGE /brand/seasonal/autumn`, then assert an impact to `/brand/collections/autumn` contains old/new paths and zero published descendants. Call move and assert the moved row parent, draft path, and draft version changed; its `published_path`, `published_version`, media usage count, and pre-existing audit count remain unchanged except for one `PAGE_MOVED` audit.

  ```java
  WebsitePageMoveImpact impact = pages.moveImpact(hqToken, autumn.id(), collections.id(), "autumn");
  assertThat(impact.items()).extracting(WebsitePageMoveImpactItem::nextDraftPath)
      .containsExactly("/brand/collections/autumn");
  WebsitePageDocument moved = pages.moveContentPage(hqToken, autumn.id(),
      new MoveWebsitePageRequest(collections.id(), "autumn", autumn.draftVersion(), autumn.lifecycleVersion()));
  assertThat(moved.draftMetadata().path()).isEqualTo("/brand/collections/autumn");
  ```

- [ ] **Step 2: Run the test and confirm the contract is absent.**

  Run:

  ```powershell
  Set-Location services/api
  & .\mvnw.cmd '-Dmaven.repo.local=C:\Users\jowoo\.m2\repository' '-Dtest=WebsitePageIntegrationTest#movesUnpublishedPageAfterReadOnlyImpact' test
  ```

  Expected: compilation fails because the request, impact records, and service methods do not exist.

- [ ] **Step 3: Add the audit migration and immutable request/read records.**

  `V17__website_page_parent_move.sql` drops and recreates `website_page_audit_action_check` with all existing actions plus `PAGE_MOVED`; it adds `website_page_parent_draft_path_idx (parent_id, lifecycle_status, draft_path, id)` for descendant discovery. `WebsitePageMoveImpact` contains `UUID pageId`, `UUID newParentId`, `String newRootDraftPath`, and immutable `List<WebsitePageMoveImpactItem>`. Each item contains id, current/next path, depth, and `boolean published`.

- [ ] **Step 4: Implement a shared locked move planner.**

  Add `planMove(PageRow root, PageRow newParent, String slug)` in `WebsitePageService`. It locks the root and all descendants with `FOR UPDATE`, ordered by `draft_path`; rejects non-ACTIVE rows, `HOME_PAGE`/`HOTEL_LANDING`, root==parent, a descendant parent, same parent/slug, any `published_from_draft_version != null`, depth over four, a new path collision outside the moved set, or `validateContentParent`/hotel-scope failure. It creates `nextDraftPath` by replacing only the root path prefix and does not update the database.

- [ ] **Step 5: Implement impact and atomic move methods.**

  `moveImpact` requires HQ, locks no persistent state (`@Transactional(readOnly = true)`), reads root/new parent, and returns the planner result. `moveContentPage` requires HQ and a non-null positive version request; inside one transaction it uses the planner, updates root `parent_id` and every moved row `draft_path`, increments each moved row `draft_version`, and inserts one `PAGE_MOVED` audit per row with `oldParentId`, `newParentId`, `oldDraftPath`, `newDraftPath`, and `root` details. It must never update `published_*`, connections, media usage, or page-version rows.

- [ ] **Step 6: Expose the controller endpoints and pass the focused test.**

  Add controller methods above the existing publish route, bind `parentId` and `slug` as request parameters for impact, and return the service values. Re-run Step 2; expected one passing test.

### Task 2: rejection and recursive-path regression coverage

**Files:**
- Modify: `services/api/src/test/java/team/hotelchain/webcontent/WebsitePageIntegrationTest.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageService.java`

**Consumes:** Task 1 move planner and existing `BusinessConflictException` error mapping.

**Produces:** complete server proof for depth, scope, lifecycle, conflict, optimistic lock, published-descendant and no-mutation rules.

- [ ] **Step 1: Add failing integration tests for the rejection matrix.**

  Build a three-level unpubished fixture and assert a root move recalculates every descendant path. Add separate assertions for fifth segment, self/descendant parent, duplicate destination path, different hotel section, archived root, branch-staff token, stale draft/lifecycle version, and a published child. For each failure capture the root/child rows and audit count before the request and assert they are unchanged after the expected exception.

  ```java
  assertThatThrownBy(() -> pages.moveContentPage(hqToken, root.id(), staleRequest))
      .isInstanceOf(BusinessConflictException.class)
      .hasMessageContaining("최신 페이지");
  assertThat(row(root.id()).draftPath()).isEqualTo(beforeRootPath);
  assertThat(auditCount(root.id())).isEqualTo(beforeAudits);
  ```

- [ ] **Step 2: Run the new test and confirm it fails for an uncovered rule.**

  Run:

  ```powershell
  Set-Location services/api
  & .\mvnw.cmd '-Dmaven.repo.local=C:\Users\jowoo\.m2\repository' '-Dtest=WebsitePageIntegrationTest#rejectsUnsafeContentPageMovesWithoutMutatingTree' test
  ```

  Expected: failure until the planner distinguishes published descendants and all descendant paths are checked.

- [ ] **Step 3: Implement only the missing rejection handling.**

  Add explicit helpers `movePublishedDescendantConflict()` and `moveInvalidRequest(String message)`. Map published root or descendant to error code `WEBSITE_PAGE_MOVE_PUBLISHED_DESCENDANT`, and preserve the existing `WEBSITE_PAGE_PATH_CONFLICT` for collisions. Validate the whole planned set before executing any update or audit insert.

- [ ] **Step 4: Pass the new test and the complete page integration class.**

  Run both:

  ```powershell
  Set-Location services/api
  & .\mvnw.cmd '-Dmaven.repo.local=C:\Users\jowoo\.m2\repository' '-Dtest=WebsitePageIntegrationTest#rejectsUnsafeContentPageMovesWithoutMutatingTree' test
  & .\mvnw.cmd '-Dmaven.repo.local=C:\Users\jowoo\.m2\repository' '-Dtest=WebsitePageIntegrationTest' test
  ```

  Expected: both commands finish with zero failures and errors.

### Task 3: 관리자 impact dialog와 tree refresh

**Files:**
- Create: `SDTPL_ADM/src/components/hotel-admin/content-page-move-dialog.tsx`
- Modify: `SDTPL_ADM/src/app/(dashboard)/dashboard/website/page.tsx`
- Modify: `SDTPL_ADM/src/components/hotel-admin/website-page-tree.tsx`
- Test: `SDTPL_ADM/e2e/website-content-editor.spec.ts`

**Consumes:** Task 1 APIs and existing dashboard session header/API error helpers, `Dialog`, `Select`, `Input`, `Button` components.

**Produces:** only ACTIVE `SECTION`/`CONTENT_PAGE` targets get a keyboard-accessible move action; dialog fetches impact before POST and refreshes tree/document on success.

- [ ] **Step 1: Add a failing Chromium E2E for impact-first move UX.**

  Extend the page fixture with one unpubished content page and a destination section. Route the impact GET to return old/new path data and route move POST to assert `parentId`, `slug`, expected versions. Assert the dialog renders destination and affected path, sends no POST before the confirm button, closes after success, and tree text reflects the new draft path. Add a second case that returns `409 WEBSITE_PAGE_MOVE_PUBLISHED_DESCENDANT`, expects its Korean error text, and confirms the dialog remains open.

- [ ] **Step 2: Run the E2E and verify the missing move control failure.**

  Run:

  ```powershell
  Set-Location SDTPL_ADM
  pnpm.cmd exec playwright test e2e/website-content-editor.spec.ts --grep "페이지 이동"
  ```

  Expected: failure because no move dialog/action exists.

- [ ] **Step 3: Implement typed API state and `ContentPageMoveDialog`.**

  Add local `MoveImpact`/`MoveImpactItem` types in the dashboard page, fetch impact only after a valid candidate parent and slug are selected, abort/ignore stale requests, and pass the result to the dialog. The dialog uses explicit labels, displays current→new paths, disables confirm until impact succeeds, handles Escape and focus return through the shared `Dialog`, and surfaces the server response message without translating error codes into client-side policy.

- [ ] **Step 4: Wire the tree action and success refresh.**

  `WebsitePageTree` receives optional `onMove(page)` and renders the action only for active `SECTION`/`CONTENT_PAGE`; it does not offer `HOME_PAGE`, `HOTEL_LANDING`, archived items, or a page’s descendants as parent choices. After POST success, refetch tree and selected page before closing; retain dialog inputs and error after a failed POST.

- [ ] **Step 5: Pass E2E and TypeScript validation.**

  Run:

  ```powershell
  Set-Location SDTPL_ADM
  pnpm.cmd exec playwright test e2e/website-content-editor.spec.ts --grep "페이지 이동"
  pnpm.cmd exec tsc --noEmit
  ```

  Expected: move E2E passes and TypeScript reports no errors.

### Task 4: focused runtime proof and Korean records

**Files:**
- Modify: `docs/changes/2026-09-12-content-page-parent-move.md`
- Modify: `docs/overview/current-development-context.md`

**Consumes:** completed server and admin verification.

**Produces:** Korean change record with migration reason, exact verification output, no-user-data-mutation statement, and remaining redirect/media scope.

- [ ] **Step 1: Write the change record before runtime mutation.**

  Record V17’s parent-move scope, the prohibition on published descendant moves, APIs, and planned focused commands. State that no real CMS page will be moved for verification.

- [ ] **Step 2: Rebuild only the API and verify read-only public endpoints.**

  After Task 2 succeeds, run:

  ```powershell
  docker compose up --build -d api
  Invoke-RestMethod http://127.0.0.1:4080/actuator/health
  Invoke-RestMethod 'http://127.0.0.1:4080/api/website/pages/resolve?path=/brand/story'
  ```

  Expected: Flyway V17 applies, health is `UP`, and the existing public brand resolve remains available without sending a move request.

- [ ] **Step 3: Verify the admin dialog against fixtures and customer build.**

  Run the Task 3 E2E, then:

  ```powershell
  Set-Location apps/web
  pnpm.cmd exec tsc -b
  pnpm.cmd build
  ```

  Expected: admin fixture interaction and customer production build complete successfully.

- [ ] **Step 4: Update development context and validate docs.**

  Add exact pass counts, runtime results, unverified user-data operations, and the next scope (redirect then archived-media deletion/replacement) to the two Korean documents. Run `git diff --check`; expected no whitespace errors.

## Plan self-review

### Spec coverage

- max depth, parent kinds, scope validation, path collision, lifecycle, optimistic versions, audit, and failure immutability: Tasks 1–2.
- impact-before-move UI, keyboard behavior, response refresh: Task 3.
- migration, focused server/admin/customer/runtime verification and Korean record: Task 4.
- redirect, published moves, media lifecycle, localization and drag/drop remain excluded by Global Constraints.

### Placeholder scan

The plan has no `TBD`, `TODO`, deferred implementation placeholder, or generic test instruction. Each behavior supplies a targeted command and expected result.

### Type consistency

`MoveWebsitePageRequest`, `WebsitePageMoveImpact`, and `WebsitePageMoveImpactItem` use the same names in server, controller, and UI tasks. The command always carries target draft/lifecycle versions; impact has no mutation request body.
