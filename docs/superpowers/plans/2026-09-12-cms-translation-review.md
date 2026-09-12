# CMS 영문 번역 검토·승인 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 영어 번역 초안을 version에 묶어 검토 요청·승인·반려하고, 승인된 현재 version만 발행할 수 있는 본사 CMS 워크플로를 구현한다.

**Architecture:** V21은 `website_page_translation`에 현재 상태를 추가하고 append-only `website_translation_review_event`에 감사 이력을 남긴다. 기존 `WebsiteTranslationService`가 영어 row 잠금과 publish validator를 소유하므로 상태 전이도 같은 트랜잭션 경계에 둔다. 관리자는 별도 공통 action bar를 사용하고 기존 영어 직접 발행만 제거한다.

**Tech Stack:** PostgreSQL 16/Flyway, Java 21/Spring Boot 4.1/JdbcTemplate/MockMvc, Next.js 16/React 19/TypeScript/shadcn-ui, Playwright

**Spec:** `docs/superpowers/specs/2026-09-12-cms-translation-review-design.md`

## Global Constraints

- 대상 locale은 `en` 하나이며 한국어 저장·발행 API와 공개 응답은 바꾸지 않는다.
- 첫 단계 쓰기 권한은 기존 `HQ_ADMIN`이며 새 역할을 만들지 않는다.
- 기존 공개 영어 snapshot과 URL은 V21 적용만으로 바뀌지 않는다.
- 초안 저장은 검토·승인을 무효화하지만 현재 공개본과 공개 media usage를 유지한다.
- 반려 코멘트는 trim 후 1~2,000자, 요청·승인 코멘트는 선택이며 최대 2,000자다.
- 공개 API에는 검토 상태, 코멘트, 담당자를 노출하지 않는다.
- dirty worktree의 다른 파일을 stage하거나 되돌리지 않는다. commit 전 task 파일만 staged됐는지 확인한다.

## File Map

- Create `services/api/src/main/resources/db/migration/V21__website_translation_review.sql`: 상태 컬럼, backfill, event 테이블.
- Create `services/api/src/main/java/team/hotelchain/webcontent/WebsiteTranslationReviewStatus.java`: 상태 enum.
- Create `services/api/src/main/java/team/hotelchain/webcontent/WebsiteTranslationReviewEvent.java`: event 응답.
- Create `services/api/src/main/java/team/hotelchain/webcontent/WebsiteTranslationReviewState.java`: 현재 상태 응답.
- Create `services/api/src/main/java/team/hotelchain/webcontent/WebsiteTranslationReviewActionRequest.java`: action 요청.
- Create `services/api/src/main/java/team/hotelchain/webcontent/WebsiteTranslationReviewValidationException.java`: 필수 반려 사유의 전용 400 오류 코드.
- Modify `services/api/src/main/java/team/hotelchain/webcontent/WebsiteTranslationService.java`: 조회·전이·저장 무효화·발행 gate.
- Modify `services/api/src/main/java/team/hotelchain/webcontent/WebsiteTranslationController.java`: review API.
- Modify `services/api/src/main/java/team/hotelchain/web/ApiExceptionHandler.java`: review validation 오류 응답.
- Modify `services/api/src/test/java/team/hotelchain/webcontent/WebsiteTranslationIntegrationTest.java`: PostgreSQL/MockMvc 회귀.
- Modify `SDTPL_ADM/src/lib/staff-api.ts`: review 타입과 HTTP 함수.
- Create `SDTPL_ADM/src/components/hotel-admin/website-translation-review-actions.tsx`: 상태와 action, 반려 dialog.
- Modify `SDTPL_ADM/src/components/hotel-admin/website-translation-editor.tsx`: action bar와 이력 연결.
- Modify `SDTPL_ADM/src/components/hotel-admin/content-page-editor.tsx`: 영어 직접 발행 숨김 prop.
- Modify `SDTPL_ADM/e2e/website-content-editor.spec.ts`: 검토·승인·반려 UI 회귀.
- Create `docs/changes/2026-09-12-cms-translation-review.md`: 구현·검증·롤백 기록.
- Modify `docs/architecture/cms-functional-specification.md`, `docs/overview/current-development-context.md`: 현재 계약과 상태.

---

### Task 1: V21 상태 저장과 읽기 모델

**Files:**
- Create: `services/api/src/main/resources/db/migration/V21__website_translation_review.sql`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteTranslationReviewStatus.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteTranslationReviewEvent.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteTranslationReviewState.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteTranslationService.java`
- Test: `services/api/src/test/java/team/hotelchain/webcontent/WebsiteTranslationIntegrationTest.java`

**Interfaces:**
- Produces `WebsiteTranslationReviewState review(String token, UUID pageId)`.
- `WebsiteTranslationReviewState(WebsiteTranslationReviewStatus status, Integer reviewedDraftVersion, List<WebsiteTranslationReviewEvent> events)`.
- `WebsiteTranslationReviewEvent(long id, String action, int draftVersion, UUID actorId, String actorDisplayName, Instant createdAt, String comment)`.

- [ ] **Step 1: Write the failing read-model test**

```java
@Test
void startsNewEnglishTranslationsAsDraftWithNoReviewEvents() {
    String token = headquarters();
    var ko = story(token);
    assertThat(translations.review(token, ko.id()))
        .extracting(WebsiteTranslationReviewState::status,
            WebsiteTranslationReviewState::reviewedDraftVersion)
        .containsExactly(WebsiteTranslationReviewStatus.DRAFT, null);
    translations.initialize(token, ko.id(), ko.draftVersion(), ko.lifecycleVersion());
    assertThat(translations.review(token, ko.id()).events()).isEmpty();
}
```

- [ ] **Step 2: Run RED**

Run `./mvnw.cmd -Dtest=WebsiteTranslationIntegrationTest#startsNewEnglishTranslationsAsDraftWithNoReviewEvents test` in `services/api`.

Expected: compile failure because the review types/method do not exist.

- [ ] **Step 3: Add V21 migration**

```sql
ALTER TABLE website_page_translation
  ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  ADD COLUMN reviewed_draft_version INTEGER,
  ADD CONSTRAINT website_page_translation_review_status_check
    CHECK (review_status IN ('DRAFT','IN_REVIEW','APPROVED','PUBLISHED')),
  ADD CONSTRAINT website_page_translation_review_version_check CHECK (
    (review_status = 'DRAFT' AND reviewed_draft_version IS NULL) OR
    (review_status <> 'DRAFT' AND reviewed_draft_version > 0 AND reviewed_draft_version <= draft_version)
  );

UPDATE website_page_translation
SET review_status='PUBLISHED', reviewed_draft_version=published_from_draft_version
WHERE published_content <> '{}'::jsonb
  AND published_from_draft_version = draft_version;

CREATE TABLE website_translation_review_event (
  id BIGSERIAL PRIMARY KEY,
  page_id UUID NOT NULL,
  locale VARCHAR(2) NOT NULL DEFAULT 'en' CHECK (locale='en'),
  action VARCHAR(30) NOT NULL CHECK (action IN
    ('REVIEW_REQUESTED','APPROVED','REJECTED','APPROVAL_INVALIDATED','PUBLISHED')),
  draft_version INTEGER NOT NULL CHECK (draft_version > 0),
  actor_id UUID REFERENCES staff_member(id) ON DELETE SET NULL,
  comment VARCHAR(2000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
  FOREIGN KEY (page_id, locale)
    REFERENCES website_page_translation(page_id, locale) ON DELETE CASCADE
);
CREATE INDEX website_translation_review_event_page_created_idx
  ON website_translation_review_event(page_id, locale,created_at DESC,id DESC);
```

- [ ] **Step 4: Implement records and `review`**

Call `pages.pageDraft(token,pageId)` and `requireEditableType` first. A missing translation returns `DRAFT/null/[]`. Otherwise query status and the latest 50 events ordered by `created_at DESC,id DESC`, left-joining `staff_member.display_name`.

- [ ] **Step 5: Run GREEN and nearest suite**

Run `./mvnw.cmd -Dtest=WebsiteTranslationIntegrationTest test`. Expected: all existing translation tests plus the new read-model test pass.

- [ ] **Step 6: Commit Task 1**

Stage only Task 1 files, run `git diff --cached --name-only`, then commit `feat: add translation review state`.

### Task 2: 검토 요청·승인·반려 API

**Files:**
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteTranslationReviewActionRequest.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteTranslationReviewValidationException.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteTranslationService.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteTranslationController.java`
- Modify: `services/api/src/main/java/team/hotelchain/web/ApiExceptionHandler.java`
- Test: `services/api/src/test/java/team/hotelchain/webcontent/WebsiteTranslationIntegrationTest.java`

**Interfaces:**
- Produces `requestReview`, `approveReview`, `rejectReview`, each taking `(String token, UUID pageId, WebsiteTranslationReviewActionRequest request)` and returning `WebsiteTranslationReviewState`.
- Request record is `WebsiteTranslationReviewActionRequest(int expectedDraftVersion, String comment)`.
- Produces `GET /review`, `POST /review/request`, `/review/approve`, `/review/reject`.

- [ ] **Step 1: Write failing MockMvc transition tests**

```java
@Test
void requestsAndApprovesTheCurrentEnglishDraft() throws Exception {
    String token = headquarters();
    var ko = story(token);
    translations.initialize(token, ko.id(), ko.draftVersion(), ko.lifecycleVersion());
    var mvc = MockMvcBuilders.webAppContextSetup(context).build();
    String path = "/api/staff/website/pages/" + ko.id() + "/translations/en/review";
    mvc.perform(post(path + "/request").header("X-Staff-Session", token)
        .contentType("application/json")
        .content("{\"expectedDraftVersion\":1,\"comment\":\"Please review\"}"))
        .andExpect(status().isOk());
    mvc.perform(post(path + "/approve").header("X-Staff-Session", token)
        .contentType("application/json")
        .content("{\"expectedDraftVersion\":1,\"comment\":\"Approved\"}"))
        .andExpect(status().isOk());
    assertThat(translations.review(token, ko.id()).status()).isEqualTo(WebsiteTranslationReviewStatus.APPROVED);
}
```

Add a second test: request a fresh draft, assert blank rejection returns `400`, reject with `Needs a complete alt text`, then assert `DRAFT/null` and the latest event’s version, actor, timestamp, and exact comment.

- [ ] **Step 2: Run RED**

Run `./mvnw.cmd -Dtest=WebsiteTranslationIntegrationTest#requestsAndApprovesTheCurrentEnglishDraft+requiresAReasonAndAuditsRejectedReviews test`. Expected: 404 or compilation failure for missing routes/methods.

- [ ] **Step 3: Implement transactional transitions**

- Reject non-positive expected version and optional comments over 2,000 with `IllegalArgumentException`.
- Trim comments; store blank optional comments as `NULL`; require nonblank reject comment.
- Throw `WebsiteTranslationReviewValidationException("WEBSITE_TRANSLATION_REJECTION_REASON_REQUIRED", "반려 사유를 1~2,000자로 입력해 주세요.")` for a blank rejection comment. Add a specific `400` handler before the generic `IllegalArgumentException` response.
- Lock page then translation in existing order.
- Return `409 WEBSITE_TRANSLATION_REVIEW_STALE` for version mismatch.
- Return `409 WEBSITE_TRANSLATION_REVIEW_STATE_CONFLICT` for invalid source state.
- On request, run `normalize(source, current.draftContent(), current.draftConnections(), true)` and `rejectCollision(pageId, current.draftMetadata().path())` before setting `IN_REVIEW` and the current version.
- Insert one event in the same transaction after each successful state update.

- [ ] **Step 4: Add controller routes**

```java
@GetMapping("/review")
public WebsiteTranslationReviewState review(@PathVariable UUID pageId, @RequestHeader("X-Staff-Session") String token) {
    return translations.review(token, pageId);
}
@PostMapping("/review/request")
public WebsiteTranslationReviewState requestReview(@PathVariable UUID pageId, @RequestHeader("X-Staff-Session") String token,
        @RequestBody WebsiteTranslationReviewActionRequest request) {
    return translations.requestReview(token, pageId, request);
}
@PostMapping("/review/approve")
public WebsiteTranslationReviewState approveReview(@PathVariable UUID pageId, @RequestHeader("X-Staff-Session") String token,
        @RequestBody WebsiteTranslationReviewActionRequest request) {
    return translations.approveReview(token, pageId, request);
}
@PostMapping("/review/reject")
public WebsiteTranslationReviewState rejectReview(@PathVariable UUID pageId, @RequestHeader("X-Staff-Session") String token,
        @RequestBody WebsiteTranslationReviewActionRequest request) {
    return translations.rejectReview(token, pageId, request);
}
```

- [ ] **Step 5: Run GREEN and full translation suite**

Run `./mvnw.cmd -Dtest=WebsiteTranslationIntegrationTest test`.

- [ ] **Step 6: Commit Task 2**

Stage only Task 2 files, inspect staged names, commit `feat: add translation review actions`.

### Task 3: 저장 무효화와 승인 발행 gate

**Files:**
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteTranslationService.java`
- Test: `services/api/src/test/java/team/hotelchain/webcontent/WebsiteTranslationIntegrationTest.java`

**Interfaces:**
- Changes existing `save` to reset review state.
- Changes existing `publish` to require `APPROVED` for the exact current draft.
- Produces test-only helper `approveEnglish(String token, UUID pageId, int draftVersion)`.

- [ ] **Step 1: Write failing preservation and gate test**

Create/approve/publish v1, save v2, and assert:

```java
assertThat(translations.review(token, pageId).status()).isEqualTo(WebsiteTranslationReviewStatus.DRAFT);
assertThat(translations.review(token, pageId).reviewedDraftVersion()).isNull();
assertThat(translations.resolve("/en/brand/locale-story").content())
    .containsEntry("title", "Published title");
assertThat(jdbc.queryForObject(
    "select count(*) from website_media_usage where page_id=? and locale='en' and document_state='PUBLISHED'",
    Integer.class, pageId)).isPositive();
assertThat(translations.review(token, pageId).events())
    .anySatisfy(event -> assertThat(event.action()).isEqualTo("APPROVAL_INVALIDATED"));
```

Also assert direct publish from `DRAFT` returns `409 WEBSITE_TRANSLATION_NOT_APPROVED`.

- [ ] **Step 2: Run RED**

Run the new test only. Expected: direct publish succeeds or review status remains `PUBLISHED`.

- [ ] **Step 3: Implement save invalidation**

Extend the save update with `review_status='DRAFT', reviewed_draft_version=NULL`. Read the pre-save status and add `APPROVAL_INVALIDATED` with the new draft version only for `IN_REVIEW`, `APPROVED`, or `PUBLISHED`. Do not update published columns or published usage.

- [ ] **Step 4: Implement publish gate**

```java
if (current.reviewStatus() != WebsiteTranslationReviewStatus.APPROVED) {
    throw new BusinessConflictException("WEBSITE_TRANSLATION_NOT_APPROVED",
        "현재 영어 초안을 승인한 뒤 발행해 주세요.");
}
if (!Integer.valueOf(current.draftVersion()).equals(current.reviewedDraftVersion())) {
    throw new BusinessConflictException("WEBSITE_TRANSLATION_REVIEW_STALE",
        "승인된 초안이 변경되었습니다. 다시 검토를 요청해 주세요.");
}
```

Publishing sets `review_status='PUBLISHED'`, retains the current reviewed version, inserts the review event, and keeps the existing page audit.

- [ ] **Step 5: Migrate existing happy-path tests**

```java
private void approveEnglish(String token, UUID pageId, int draftVersion) {
    translations.requestReview(token, pageId,
        new WebsiteTranslationReviewActionRequest(draftVersion, null));
    translations.approveReview(token, pageId,
        new WebsiteTranslationReviewActionRequest(draftVersion, null));
}
```

Call it immediately before every intended successful English publish. Do not add it to forbidden/stale assertions. Preserve one MVC happy path through the real review routes.

- [ ] **Step 6: Run GREEN**

Run `./mvnw.cmd -Dtest=WebsiteTranslationIntegrationTest test`. Expected: archive/restore, redirects, home, landing, collections, usage, stale cases, and new workflow all pass.

- [ ] **Step 7: Commit Task 3**

Stage the service and integration test only, inspect staged names, commit `feat: require approval for translation publishing`.

### Task 4: 관리자 공통 action bar

**Files:**
- Modify: `SDTPL_ADM/src/lib/staff-api.ts`
- Create: `SDTPL_ADM/src/components/hotel-admin/website-translation-review-actions.tsx`
- Modify: `SDTPL_ADM/src/components/hotel-admin/website-translation-editor.tsx`
- Modify: `SDTPL_ADM/src/components/hotel-admin/content-page-editor.tsx`
- Test: `SDTPL_ADM/e2e/website-content-editor.spec.ts`

**Interfaces:**
- Produces TS status/event/state types matching Java JSON.
- Produces `getWebsiteTranslationReview`, `requestWebsiteTranslationReview`, `approveWebsiteTranslationReview`, `rejectWebsiteTranslationReview`.
- Adds `showPublishAction?: boolean` to `ContentPageEditor`, default `true`.

- [ ] **Step 1: Rewrite the existing English E2E happy path first**

Mock `GET /review` as `DRAFT`, transition mock state on request/approve/publish, and require this sequence at 1280 and 390:

```ts
await page.getByRole("button", { name: "초안 저장", exact: true }).click();
await page.getByRole("button", { name: "검토 요청", exact: true }).click();
await expect(page.getByText("검토 중", { exact: true })).toBeVisible();
await page.getByRole("button", { name: "승인", exact: true }).click();
await expect(page.getByText("승인됨", { exact: true })).toBeVisible();
await page.getByRole("button", { name: "발행", exact: true }).click();
await expect(page.getByText("발행됨", { exact: true })).toBeVisible();
```

Assert request/approve use `{ expectedDraftVersion: 2, comment: null }` and publish occurs after approval.

- [ ] **Step 2: Add failing rejection-dialog E2E**

Start in `IN_REVIEW`, open `반려`, assert title `영어 번역을 반려할까요?`, disabled blank submit, submit `이미지 설명을 영어로 보완해 주세요.`, assert request body and `초안` badge. Reopen and press Escape; focus must return to `반려`.

- [ ] **Step 3: Run RED**

Run the two cases with `npm.cmd run test:e2e -- e2e/website-content-editor.spec.ts --grep "English translation|rejects an English translation" --workers=1 --max-failures=1`. Expected: missing buttons/routes/badges.

- [ ] **Step 4: Add API types/functions**

```ts
export type WebsiteTranslationReviewStatus = "DRAFT" | "IN_REVIEW" | "APPROVED" | "PUBLISHED";
export type WebsiteTranslationReviewEvent = {
  id: number;
  action: "REVIEW_REQUESTED" | "APPROVED" | "REJECTED" | "APPROVAL_INVALIDATED" | "PUBLISHED";
  draftVersion: number;
  actorId: string | null;
  actorDisplayName: string | null;
  createdAt: string;
  comment: string | null;
};
export type WebsiteTranslationReviewState = {
  status: WebsiteTranslationReviewStatus;
  reviewedDraftVersion: number | null;
  events: WebsiteTranslationReviewEvent[];
};
```

Each mutation posts `{ expectedDraftVersion, comment }` through existing `contentRequest`.

- [ ] **Step 5: Implement action bar and dialog**

Render Korean state labels and reviewed version. Disable transitions while dirty, busy, or archived. Apply mutation response immediately. Keep rejection dialog open on API error with `role=alert`, restore focus on cancel/success, and announce success with `role=status`.

- [ ] **Step 6: Integrate without changing Korean behavior**

`WebsiteTranslationEditor` owns review state and editor dirty state. Use `DRAFT/null/[]` before initialization. Remove landing editor’s local publish branch. Pass `showPublishAction={false}` for English `ContentPageEditor`; default `true` preserves Korean publishing.

- [ ] **Step 7: Run GREEN and type check**

Run `npm.cmd run test:e2e -- e2e/website-content-editor.spec.ts --grep "English translation|rejects an English translation" --workers=1 --max-failures=1`, then run `npx.cmd tsc --noEmit` from `SDTPL_ADM`; the package has no separate typecheck script.

- [ ] **Step 8: Commit Task 4**

Stage only the five Task 4 files, inspect staged names, commit `feat: add translation review controls`.

### Task 5: 랜딩·이력·오류 복구 회귀

**Files:**
- Modify: `SDTPL_ADM/src/components/hotel-admin/website-translation-editor.tsx`
- Modify: `SDTPL_ADM/src/components/hotel-admin/website-translation-review-actions.tsx`
- Test: `SDTPL_ADM/e2e/website-content-editor.spec.ts`

**Interfaces:**
- Consumes Task 4 action bar.
- Produces common workflow for HOME_PAGE, CONTENT_PAGE, HOTEL_LANDING and latest 50-event timeline.

- [ ] **Step 1: Add failing landing and refresh-recovery E2E**

Change the existing landing test to `저장 → 검토 요청 → 승인 → 발행`, asserting the same draft version in every action. Add a case where approval succeeds but a later `GET /review` returns 500; `승인됨` must remain visible, history warning must appear, and retry must not repeat approval.

- [ ] **Step 2: Run RED**

Run `npm.cmd run test:e2e -- e2e/website-content-editor.spec.ts --grep "landing content|keeps the approved state" --workers=1 --max-failures=1`. Expected: old direct publish or successful mutation incorrectly shown as failed.

- [ ] **Step 3: Add timeline and isolated refresh handling**

Render action, `초안 vN`, actor name fallback `알 수 없는 담당자`, localized time, and comment. Apply mutation state before optional history/version refresh; catch those reads separately and expose a retry action.

- [ ] **Step 4: Verify responsive and keyboard UI**

Run the four workflow cases with `CMS_LOCALE_SCREENSHOTS=1`. Inspect `.tmp/cms-locale-admin-1280.png` and `390.png` for action order, dialog sizing, focus return, and horizontal overflow.

- [ ] **Step 5: Run the whole editor E2E file and production build**

Run `npm.cmd run test:e2e -- e2e/website-content-editor.spec.ts --workers=1 --max-failures=1` and `npm.cmd run build` in `SDTPL_ADM`.

- [ ] **Step 6: Commit Task 5**

Stage the three Task 5 files only, inspect staged names, commit `test: cover translation review workflow`.

### Task 6: 문서·실제 migration·최종 회귀

**Files:**
- Create: `docs/changes/2026-09-12-cms-translation-review.md`
- Modify: `docs/architecture/cms-functional-specification.md`
- Modify: `docs/overview/current-development-context.md`

**Interfaces:**
- Consumes Tasks 1–5 implementation and verification evidence.
- Produces current contract, rollback record, verified and unverified scope.

- [ ] **Step 1: Record the implemented contract**

Document V21, four review endpoints, state rules, existing-row backfill, UI, rollback constraint, and the commands below. Mark physical-device checks, role split, scheduling, alerts, and Korean workflow out of scope.

- [ ] **Step 2: Run focused backend/frontend gates**

```powershell
Set-Location services/api
./mvnw.cmd -Dtest=WebsiteTranslationIntegrationTest test
Set-Location ../../SDTPL_ADM
npm.cmd run test:e2e -- e2e/website-content-editor.spec.ts --workers=1 --max-failures=1
npm.cmd run build
Set-Location ../apps/web
pnpm.cmd run build
```

Record observed test counts; do not copy old totals.

- [ ] **Step 3: Rebuild API and verify live state read-only**

Run `docker compose up -d --build api`, inspect the last 200 API log lines for V21 and readiness, then read-only verify all seven existing `/en` routes remain public and rows whose draft equals published source are `PUBLISHED`. Do not save, approve, reject, republish, archive, or delete user CMS content.

- [ ] **Step 4: Run hygiene checks**

Run `git -c core.safecrlf=false diff --check` and `git status --short`. Inspect task files for secrets, dumps, caches, broad formatting, and unrelated edits.

- [ ] **Step 5: Update evidence and commit docs**

Replace planned counts with observed results. Stage only the three documentation files, inspect staged names, commit `docs: record translation review workflow`.

- [ ] **Step 6: Final requirement audit**

Match every spec requirement to a passing test or explicit read-only/manual check. Do not claim role separation, scheduled publishing, notifications, Korean approval, or physical-device validation.
