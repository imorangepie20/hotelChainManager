# 일반 콘텐츠 페이지 발행본 초안 복원 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 본사 관리자가 활성 일반 콘텐츠 페이지의 이전 발행본 콘텐츠를 현재 초안으로 복원하고 검토·재발행할 수 있게 한다.

**Architecture:** 기존 `website_page_version`의 불변 스냅샷을 source of truth로 읽고, 현재 `CONTENT_PAGE`의 초안 콘텐츠만 조건부 UPDATE 한다. 공개 필드와 페이지 메타데이터는 보존하며, 정상 저장과 같은 미디어 정규화·초안 usage 동기화를 적용한다.

**Tech Stack:** Java 21, Spring Boot, PostgreSQL 16/Flyway, Next.js/React/TypeScript, Playwright.

**Spec:** `docs/superpowers/specs/2026-09-11-content-page-version-restore-design.md`

## Global Constraints

- 대상은 `ACTIVE` `CONTENT_PAGE`이며 `HOME_PAGE`, `HOTEL_LANDING`, `SECTION`은 허용하지 않는다.
- 복원은 과거 발행본의 콘텐츠만 현재 초안으로 복사하고 현재 URL·메뉴·부모·공개본은 변경하지 않는다.
- 요청은 lifecycle, draft, published의 현재 버전을 모두 보내며 서버는 한 트랜잭션에서 검증한다.
- 과거 콘텐츠도 활성 미디어와 현재 구조화 콘텐츠 계약을 다시 통과해야 한다.
- 고객 공개는 명시적 재발행 뒤에만 바뀐다.
- 현재 사용자가 요청한 작업 트리에서 진행하고, 기존 변경을 되돌리거나 별도 브랜치·worktree·커밋을 만들지 않는다.

---

### Task 1: 감사 로그 제약과 서버 복원 계약

**Files:**
- Create: `services/api/src/main/resources/db/migration/V15__website_page_version_restore.sql`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageService.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageManagementController.java`
- Test: `services/api/src/test/java/team/hotelchain/webcontent/WebsitePageIntegrationTest.java`

**Interfaces:**
- Consumes: `WebsitePageLifecycleRequest(int expectedLifecycleVersion, int expectedDraftVersion, int expectedPublishedVersion)` and `website_page_version(page_id, version, page_snapshot)`.
- Produces: `WebsitePageService.restoreContentPageVersionDraft(String token, UUID pageId, int sourceVersion, WebsitePageLifecycleRequest request): WebsitePageDocument`.
- Produces: `POST /api/staff/website/pages/{pageId}/versions/{sourceVersion}/restore-draft`.

- [x] **Step 1: 발행본 콘텐츠 복원 통합 테스트를 작성한다.**

`WebsitePageIntegrationTest`에서 다음을 한 테스트 흐름으로 만든다.

```java
WebsitePageDocument first = pages.publishPage(hq.token(), page.id(), page.draftVersion(), page.publishedVersion());
WebsitePageDocument changed = pages.saveContentPageDraft(hq.token(), page.id(), first.draftVersion(), metadata, validContentPage("변경 발행본"));
WebsitePageDocument currentPublic = pages.publishPage(hq.token(), page.id(), changed.draftVersion(), changed.publishedVersion());
WebsitePageDocument restored = pages.restoreContentPageVersionDraft(
    hq.token(), page.id(), first.publishedVersion(), lifecycleRequest(currentPublic));

assertThat(restored.draftContent()).isEqualTo(first.publishedContent());
assertThat(restored.publishedContent()).isEqualTo(currentPublic.publishedContent());
assertThat(pages.resolvePublished("/brand/history").content()).isEqualTo(currentPublic.publishedContent());
```

이 테스트에는 source version 0, 현재 발행본 version, 다른 페이지의 version, 보관 페이지, 오래된 lifecycle request, 지점 직원 요청도 포함한다. 초안 usage가 새 콘텐츠 자산과 일치하고 `VERSION_RESTORED` audit details에 `sourceVersion`이 있는지도 검증한다.

- [x] **Step 2: 복원 계약의 실패 기준을 고정한다.**

Run:

```powershell
Set-Location services\api
& .\mvnw.cmd '-Dmaven.repo.local=C:\Users\jowoo\.m2\repository' '-Dtest=WebsitePageIntegrationTest' test
```

기준: 서비스 또는 route가 없으면 이 계약 테스트가 컴파일 또는 테스트 단계에서 실패한다. 최종 통과 결과는 Step 4에 기록한다.

- [x] **Step 3: V15 migration과 최소 서버 구현을 작성한다.**

V15 migration은 `website_page_audit_action_check`을 다시 만들고 `VERSION_RESTORED`를 허용한다. 서비스는 다음 순서로 구현한다.

```java
PageRow current = lockedContentPageForUpdate(pageId);
requireLifecycleRequest(request);
if (!"ACTIVE".equals(current.lifecycleStatus()) || !matchesLifecycleRequest(current, request)) {
    throw lifecycleConflict();
}
if (sourceVersion >= current.publishedVersion()) {
    throw new IllegalArgumentException("이전 발행본만 초안으로 복원할 수 있습니다.");
}
Map<String, Object> source = versionSnapshot(current.id(), sourceVersion);
Map<String, Object> content = mediaReferences.normalizeStructuredContent(snapshotContent(source, current));
contentPages.validate(content);
```

그 다음 lifecycle·draft·published version을 모두 비교하는 UPDATE로 `draft_content`와 `draft_version`만 바꾼다. 성공 시 `mediaReferences.synchronizeDraft(...)`와 `VERSION_RESTORED` audit insert를 수행한다. controller는 위 POST route를 `WebsitePageLifecycleRequest`로 연결한다.

- [x] **Step 4: 서버 테스트를 통과시킨다.**

Run:

```powershell
Set-Location services\api
& .\mvnw.cmd '-Dmaven.repo.local=C:\Users\jowoo\.m2\repository' '-Dtest=WebsitePageIntegrationTest' test
```

Expected: `WebsitePageIntegrationTest`가 모두 통과한다.

### Task 2: 발행 이력의 안전한 복원 제어

**Files:**
- Modify: `SDTPL_ADM/src/lib/staff-api.ts`
- Modify: `SDTPL_ADM/src/components/hotel-admin/website-content-editor.tsx`
- Test: `SDTPL_ADM/e2e/website-content-editor.spec.ts`

**Interfaces:**
- Consumes: `POST /api/staff/website/pages/{pageId}/versions/{sourceVersion}/restore-draft`.
- Produces: `restoreWebsitePageVersionDraft(token, pageId, sourceVersion, input): Promise<WebsitePageDocument>`.
- Produces: 이력 카드의 확인 대화상자와 복원 뒤 새 문서·이력 갱신.

- [x] **Step 1: 관리자 복원 E2E를 작성한다.**

E2E fixture에 발행 이력 v3, v2를 제공하고 다음 행동을 검증한다.

```ts
await page.getByRole("button", { name: "발행본 v2 콘텐츠를 초안으로 복원" }).click();
await expect(page.getByRole("alertdialog", { name: "발행본을 초안으로 복원할까요?" })).toContainText("고객 웹");
await page.getByRole("button", { name: "초안으로 복원" }).click();
expect((await request).postDataJSON()).toEqual({
  expectedLifecycleVersion: 1,
  expectedDraftVersion: 4,
  expectedPublishedVersion: 3,
});
```

미저장 편집에서는 복원 버튼이 비활성이고, 성공 뒤에는 새 초안 제목·성공 안내·활성 발행 버튼을 확인한다.

- [x] **Step 2: 복원 UI의 실패 기준을 고정한다.**

Run:

```powershell
pnpm.cmd exec playwright test e2e/website-content-editor.spec.ts --project=chromium --grep "발행본.*초안"
```

Working directory: `SDTPL_ADM`

기준: 복원 제어 또는 요청 route가 없으면 이 E2E가 실패한다. 최종 통과 결과는 Step 4에 기록한다.

- [x] **Step 3: API 클라이언트와 관리자 UI를 구현한다.**

`staff-api.ts`에 lifecycle request를 그대로 받는 복원 함수를 추가한다. `website-content-editor.tsx`의 발행 이력 카드가 이전 버전마다 복원 버튼을 렌더링한다. 버튼은 활성 일반 페이지이고 dirty가 아닐 때만 가능하며, 확인 action은 현재 세 버전을 전송한다. 성공 시 반환 문서·이력·트리를 갱신한다.

- [x] **Step 4: 대상 E2E와 TypeScript 검사를 통과시킨다.**

Run:

```powershell
pnpm.cmd exec playwright test e2e/website-content-editor.spec.ts --project=chromium --grep "발행본.*초안"
pnpm.cmd exec tsc --noEmit
```

Working directory: `SDTPL_ADM`

Expected: 새 E2E와 TypeScript 검사가 통과한다.

### Task 3: 변경 기록과 대상 회귀 확인

**Files:**
- Modify: `docs/changes/2026-09-10-web-content-management.md`
- Modify: `docs/overview/current-development-context.md`
- Modify: `docs/architecture/full-site-implementation-design.md`
- Modify: `docs/overview/project-brief.md`
- Modify: `docs/decisions/decision-log.md`

- [x] **Step 1: CMS 문서에 V15의 동작·제한·검증 결과를 기록한다.**

발행본 콘텐츠만 복원하고 URL·메뉴·부모·공개본은 유지한다는 점, 보관/미디어 호환성 거부 규칙, 수행한 테스트 수와 Docker·실제 화면 검증 여부를 실제 결과만 기록한다.

- [x] **Step 2: 관련 서버·관리자 회귀를 실행한다.**

Run:

```powershell
# services/api
& .\mvnw.cmd '-Dmaven.repo.local=C:\Users\jowoo\.m2\repository' '-Dtest=WebsitePageIntegrationTest' test

# SDTPL_ADM
pnpm.cmd exec playwright test e2e/website-content-editor.spec.ts --project=chromium
pnpm.cmd exec tsc --noEmit

git diff --check
```

Working directories: Maven은 `services/api`, Playwright와 TypeScript는 `SDTPL_ADM`이다.

결과: `WebsitePageIntegrationTest` 12건, CMS Playwright 전체 16건과 최종 복원 대상 1건, TypeScript 검사가 통과했다. `git diff --check`는 공백 오류 없이 통과했으며, 기존 수정 파일의 LF→CRLF 변환 경고만 출력했다.

- [x] **Step 3: Docker migration과 read-only 공개 API를 확인한다.**

Run:

```powershell
docker compose up -d --build api
Invoke-RestMethod http://127.0.0.1:4080/actuator/health
Invoke-RestMethod "http://127.0.0.1:4080/api/website/pages/resolve?path=/brand/story"
```

Expected: Flyway V15 적용, readiness `UP`, 기존 활성 공개 페이지 resolve가 유지된다. 사용자가 운영 중인 페이지에는 복원·저장·발행 요청을 보내지 않는다.


## 실행 결과

- 서버: `WebsitePageIntegrationTest` 12건이 실패·오류 없이 통과했다. 이전 발행본 콘텐츠의 초안 복원, 현재 공개본 보존, 재발행 후 공개 반영, 초안 미디어 usage·`VERSION_RESTORED` audit, 0·현재 발행본·없는 source version, 권한·보관·오래된 버전 거부를 확인했다.
- 관리자: CMS Playwright 전체 16건이 통과했고, 현재 저장 초안 대체 경고를 추가한 뒤 복원 대상 E2E 1건과 `pnpm.cmd exec tsc --noEmit`을 다시 통과했다.
- 런타임: `docker compose up -d --build api` 뒤 Flyway V15 적용과 `/actuator/health`의 `UP`, 기존 `/api/website/pages/resolve?path=/brand/story`의 읽기 전용 응답을 확인했다. 실제 개발 콘텐츠에는 저장·복원·발행 요청을 보내지 않았다.
- 작업 트리: `git diff --check`는 공백 오류 없이 통과했다. 기존 수정 파일에는 LF→CRLF 변환 경고가 출력되며, 경고를 숨기지 않았다.
