# 일반 콘텐츠 페이지 영구 삭제 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 본사가 보관된 일반 콘텐츠 페이지를 이력·사용 위치와 함께 안전하게 영구 삭제하게 한다.

**Architecture:** Spring이 `CONTENT_PAGE`를 행 잠금과 lifecycle request 검증 뒤 child history·usage 행부터 지우고 부모 page를 삭제한다. Next 관리자는 보관된 페이지에서만 확인 dialog와 DELETE를 노출하며 성공 시 홈과 새 트리로 전환한다.

**Tech Stack:** Java 21, Spring Boot, PostgreSQL 16, Next.js 16, React 19, TypeScript, Playwright.

**Spec:** `docs/superpowers/specs/2026-09-11-content-page-permanent-delete-design.md`

## Global Constraints

- `ARCHIVED CONTENT_PAGE`와 현재 lifecycle/draft/published version이 모두 일치할 때만 삭제한다.
- 미디어 자산 자체는 삭제하지 않는다. page usage, immutable version, page audit, parent page 행만 한 트랜잭션에서 삭제한다.
- 기존 공유 작업 트리에서 진행하며 branch, worktree, commit을 만들지 않는다.
- 실제 사용자 페이지에는 archive 또는 delete 요청을 보내지 않는다.

### Task 1: 서버 영구 삭제 계약

**Files:**
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageService.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageManagementController.java`
- Test: `services/api/src/test/java/team/hotelchain/webcontent/WebsitePageIntegrationTest.java`

**Interfaces:**
- Consumes: `WebsitePageService.deleteArchivedContentPage(String token, UUID pageId, WebsitePageLifecycleRequest request)`.
- Produces: `DELETE /api/staff/website/pages/{pageId}` with `204 No Content`.

- [x] **Step 1: 삭제 전후 데이터와 거부 조건을 검증하는 실패 통합 테스트를 작성한다.**

```java
WebsitePageDocument archived = pages.archiveContentPage(hq.token(), page.id(), lifecycleRequest(published));
pages.deleteArchivedContentPage(hq.token(), page.id(), lifecycleRequest(archived));
assertThat(jdbc.queryForObject("select count(*) from website_page where id = ?", Integer.class, page.id())).isZero();
assertThat(jdbc.queryForObject("select count(*) from website_page_version where page_id = ?", Integer.class, page.id())).isZero();
assertThat(jdbc.queryForObject("select count(*) from website_page_audit where page_id = ?", Integer.class, page.id())).isZero();
assertThat(jdbc.queryForObject("select count(*) from website_media_usage where page_id = ?", Integer.class, page.id())).isZero();
```

활성 페이지, 이전 lifecycle request, SECTION, 지점 직원도 같은 테스트에서 거부한다.

- [x] **Step 2: 실패를 확인한다.**

Run:

```powershell
# services/api
& .\mvnw.cmd '-Dmaven.repo.local=C:\Users\jowoo\.m2\repository' '-Dtest=WebsitePageIntegrationTest' test
```

Expected: `deleteArchivedContentPage`이 없어서 컴파일 또는 테스트가 실패한다.

- [x] **Step 3: 잠금·순서 정리·204 controller를 구현한다.**

`lockedContentPageForUpdate`와 `requireLifecycleRequest`를 재사용한다. page가 `ARCHIVED`이고 version이 일치하는지 확인한 뒤 자식 page 유무를 검사한다. 순서대로 `website_media_usage`, `website_page_version`, `website_page_audit`를 page id로 지우고, 마지막 `website_page` 삭제에는 type·status·세 version 조건을 모두 포함한다. 0건이면 `WEBSITE_PAGE_DELETE_CONFLICT`를 던진다. controller는 `@DeleteMapping`과 `@ResponseStatus(HttpStatus.NO_CONTENT)`로 연결한다.

- [x] **Step 4: 대상 서버 테스트를 통과시킨다.**

Run:

```powershell
# services/api
& .\mvnw.cmd '-Dmaven.repo.local=C:\Users\jowoo\.m2\repository' '-Dtest=WebsitePageIntegrationTest' test
```

Expected: 삭제·거부 조건을 포함한 `WebsitePageIntegrationTest`가 통과한다.

### Task 2: 관리자 확인 dialog와 성공 전환

**Files:**
- Modify: `SDTPL_ADM/src/lib/staff-api.ts`
- Modify: `SDTPL_ADM/src/components/hotel-admin/content-page-editor.tsx`
- Modify: `SDTPL_ADM/src/components/hotel-admin/website-content-editor.tsx`
- Test: `SDTPL_ADM/e2e/website-content-editor.spec.ts`

**Interfaces:**
- Consumes: `deleteWebsitePage(token, pageId, WebsitePageLifecycleInput): Promise<void>`.
- Produces: `ContentPageEditor.onDeleted(): void` after successful delete only.

- [x] **Step 1: 보관 후 삭제 confirmation E2E를 작성한다.**

fixture의 DELETE route가 `204`을 반환하고 다음 tree GET에서 대상 leaf를 제외하게 한다. 활성 일반 페이지에는 `영구 삭제` 버튼이 없고, 보관 뒤에만 버튼·경고가 보이는지 확인한다. 확정 DELETE body의 세 version과 성공 뒤 홈 이동·트리 제거를 assert한다.

- [x] **Step 2: 빈 응답 DELETE API client를 추가한다.**

`staff-api.ts`에 `contentRequest`의 오류 형식을 재사용하는 `deleteWebsitePage`를 추가한다. `204` 성공 본문을 JSON으로 읽지 않는다.

- [x] **Step 3: editor와 상위 CMS 전환을 구현한다.**

`ContentPageEditor`는 `ARCHIVED CONTENT_PAGE`에서만 destructive `영구 삭제`와 AlertDialog를 표시한다. 대화상자는 history/audit/media usage 영구 삭제 및 복구 불가를 설명하고 요청 중 중복 실행을 막는다. 상위 CMS는 성공 시 selected content page를 비우고 고정 home을 불러오며 tree를 새로 읽는다.

- [x] **Step 4: 관리자 focused 검증을 통과시킨다.**

Run:

```powershell
# SDTPL_ADM
pnpm.cmd exec playwright test e2e/website-content-editor.spec.ts --project=chromium --grep "permanently deletes"
pnpm.cmd exec tsc --noEmit
```

Expected: confirmation, DELETE request, home transition, tree update가 확인되고 TypeScript 검사가 통과한다.

### Task 3: 문서과 runtime 확인

**Files:**
- Modify: `docs/changes/2026-09-10-web-content-management.md`
- Modify: `docs/overview/current-development-context.md`
- Modify: `docs/architecture/full-site-implementation-design.md`
- Modify: `docs/overview/project-brief.md`
- Modify: `docs/decisions/decision-log.md`

- [x] **Step 1: 영구 삭제의 상태 전제와 정리 대상·자산 보존 한계를 한국어 문서에 기록한다.**
- [x] **Step 2: Docker API를 재빌드하고 4080 health와 기존 공개 page resolve를 읽기 전용으로 확인한다.**
- [x] **Step 3: `git diff --check`를 실행한다.**
