# 발행 콘텐츠 페이지 이동·Redirect Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 현재 공유 트리에서만 실행하며 branch·worktree·commit·reset·clean을 만들지 않는다.

**Goal:** 발행 콘텐츠 페이지를 이동할 때 기존 공개 경로를 301 redirect로 보존한다.

**Architecture:** `website_redirect`는 source path와 현재 target page를 정규화해 저장한다. move transaction은 발행 root·하위의 published path, immutable snapshot, redirect row, audit을 함께 갱신하고 공개 resolve는 page 조회 실패 시 redirect를 정확히 한 번 적용한다.

**Tech Stack:** Java 21, Spring Boot, PostgreSQL/Flyway, Next.js/TypeScript, Playwright.

**Spec:** `docs/superpowers/specs/2026-09-12-content-page-redirect-design.md`

## Global Constraints

- redirect는 로컬 canonical URL만 허용하며 한 번만 적용한다.
- `HOME_PAGE`, `HOTEL_LANDING`, 다국어, canonical/OG, 외부 URL, 만료 redirect는 제외한다.
- HQ·optimistic version·path/scope/depth 검증을 유지하며 실패 시 공개 상태를 바꾸지 않는다.
- 실제 사용자 CMS 페이지에는 move 요청을 보내지 않는다.

### Task 1: redirect schema와 published move 서버 계약

**Files:**
- Create: `services/api/src/main/resources/db/migration/V18__website_page_redirect.sql`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteRedirect.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/MoveWebsitePageRequest.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageService.java`
- Test: `services/api/src/test/java/team/hotelchain/webcontent/WebsitePageIntegrationTest.java`

- [ ] **Step 1: Write a failing integration test for a published root and child move.**

  Create/publish a fixture root and child, move it under another section, then assert the new published resolve works, old root/child paths produce 301 targets, navigation exposes only the new path, snapshots contain new paths, and two redirect rows exist.

- [ ] **Step 2: Run the targeted test and confirm it fails because redirect rows and published moves do not exist.**

  Run `services/api`: `& .\mvnw.cmd '-Dmaven.repo.local=C:\Users\jowoo\.m2\repository' '-Dtest=WebsitePageIntegrationTest#movesPublishedTreeWithPermanentRedirects' test`.

- [ ] **Step 3: Add V18 schema and minimal redirect record.**

  Create `website_redirect` with UUID id, unique source/target path checks, page foreign key, actor and timestamp; add `PAGE_MOVED_WITH_REDIRECT` audit action. Extend move request with expected published version.

- [ ] **Step 4: Implement atomic published move.**

  Lock the root and descendants, calculate paths, reject source/target collisions and redirect chains, update draft/published paths and versions, replace affected snapshots, insert redirects and one audit per moved row.

- [ ] **Step 5: Pass the targeted test and the full `WebsitePageIntegrationTest` class.**

### Task 2: public 301 resolution and conflict protection

**Files:**
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/PublicWebsitePageController.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageService.java`
- Test: `services/api/src/test/java/team/hotelchain/webcontent/WebsitePageIntegrationTest.java`

- [ ] **Step 1: Write failing tests for direct redirect, chain/cycle rejection, stale version, and no mutation on rejected moves.**
- [ ] **Step 2: Run the focused test and confirm current public resolve returns 404 for an old path.**
- [ ] **Step 3: Return `301 Location` for an active redirect source and reject redirect target/source chaining.**
- [ ] **Step 4: Run focused and complete server tests.**

### Task 3: administrator impact and verification record

**Files:**
- Modify: `SDTPL_ADM/src/lib/staff-api.ts`
- Modify: `SDTPL_ADM/src/components/hotel-admin/content-page-move-dialog.tsx`
- Modify: `SDTPL_ADM/e2e/website-content-editor.spec.ts`
- Create: `docs/changes/2026-09-12-content-page-redirect.md`
- Modify: `docs/overview/current-development-context.md`

- [ ] **Step 1: Add failing E2E for redirect impact list and explicit publish-version payload.**
- [ ] **Step 2: Run it and confirm the current dialog has no redirect information.**
- [ ] **Step 3: Render redirect rows and send expected published version only after impact confirmation.**
- [ ] **Step 4: Run E2E, TypeScript, server tests, API rebuild, health, old-path 301 and new-path resolve; record results in Korean.**
