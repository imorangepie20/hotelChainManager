# CMS 일반 페이지 보관·복원 구현 계획

**Goal:** 본사가 일반 콘텐츠 페이지를 공개 URL과 메뉴에서 안전하게 내리고, 초안·발행 이력을 보존한 채 초안으로 복원하게 한다.

**Architecture:** `website_page`의 콘텐츠 버전과 독립된 수명주기 상태·버전을 둔다. 저장·발행은 활성 행의 공유 잠금을 확보하고 보관·복원은 배타 잠금을 사용한다. 보관은 현재 공개본과 공개 미디어 usage를 비우지만 초안·발행 이력·초안 미디어 usage는 보존한다.

**Tech Stack:** Java 21, Spring Boot, JdbcTemplate, Flyway, PostgreSQL, Next.js 16, React 19, Playwright, JUnit.

**Spec:** `docs/superpowers/specs/2026-09-11-content-page-lifecycle-design.md`

## Global Constraints

- 대상은 `CONTENT_PAGE`뿐이다. 홈·지점 랜딩·섹션의 수명주기는 바꾸지 않는다.
- `HQ_ADMIN`만 상태를 바꿀 수 있으며, 지점 직원 요청은 서버에서 거부한다.
- 보관은 물리 삭제·URL 해제가 아니며, 초안 미디어 사용 위치를 삭제하지 않는다.
- 가격·재고·예약·AI 소유권은 변경하지 않는다.
- 대상 테스트와 읽기 전용 실제 CMS 확인만 수행하고 사용자 페이지를 실제로 보관하지 않는다.

### Task 1: 서버 수명주기 계약

**Files:**
- Create: `services/api/src/main/resources/db/migration/V14__website_page_lifecycle.sql`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageLifecycleRequest.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageDocument.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageTreeItem.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageService.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageManagementController.java`
- Test: `services/api/src/test/java/team/hotelchain/webcontent/WebsitePageIntegrationTest.java`

- [x] **Step 1: 보관·복원과 공개 차단의 실패 통합 테스트를 먼저 작성한다.**

보관한 일반 페이지가 public resolve·navigation에서 빠지고, 초안·발행 이력·초안 미디어 사용 위치를 유지하며, 복원만으로는 비공개이고 다시 발행해야 같은 공개 경로로 돌아오는지 검증한다. 상태·초안·발행 버전 충돌과 지점 직원 403도 추가한다.

- [x] **Step 2: 실패하는 대상 서버 테스트를 실행한다.**

Run: `mvn -Dtest=WebsitePageIntegrationTest test`

Expected: 수명주기 타입·API가 없으므로 컴파일 또는 계약 assertion이 실패한다.

- [x] **Step 3: V14 스키마·락·API를 최소 범위로 구현한다.**

`lifecycle_status`, `lifecycle_version`, 보관 시각·행위자, `ARCHIVED`·`RESTORED` 감사 action을 추가한다. `CONTENT_PAGE`만 `FOR UPDATE` 상태 전환을 허용하고, 저장·발행·공개 resolve·navigation에 활성 상태 조건을 적용한다. 보관은 현재 공개본과 공개 usage만 비운다.

- [x] **Step 4: 대상 서버 테스트를 다시 실행한다.**

Run: `mvn -Dtest=WebsitePageIntegrationTest test`

Expected: 수명주기와 기존 페이지 회귀가 통과한다.

### Task 2: 관리자 보관·복원 UX

**Files:**
- Modify: `SDTPL_ADM/src/lib/staff-api.ts`
- Modify: `SDTPL_ADM/src/components/hotel-admin/website-page-tree.tsx`
- Modify: `SDTPL_ADM/src/components/hotel-admin/content-page-editor.tsx`
- Modify: `SDTPL_ADM/src/components/hotel-admin/website-content-editor.tsx`
- Test: `SDTPL_ADM/e2e/website-content-editor.spec.ts`

- [x] **Step 1: 보관 확인·읽기 전용·복원의 실패 Playwright 사례를 작성한다.**

활성 일반 페이지에서 `페이지 보관` 확인 요청이 현재 수명주기를 보내는지, 보관 응답 뒤 폼과 저장·발행이 비활성인지, `복원` 요청 후 다시 활성인지 검증한다.

- [x] **Step 2: 실패하는 신규 E2E 사례를 실행한다.**

Run: `pnpm.cmd exec playwright test e2e/website-content-editor.spec.ts --project=chromium --grep "페이지 보관|보관 페이지"

Expected: 보관 제어와 lifecycle API가 없어서 locator 또는 요청 assertion이 실패한다.

- [x] **Step 3: 타입 API와 접근 가능한 상태 제어를 구현한다.**

관리자 트리에 보관 배지를 표시한다. 일반 페이지의 명시적 보관 확인 대화상자와 복원 버튼을 추가하고, 보관 상태에서는 모든 편집·저장·발행 제어를 비활성화한다.

- [x] **Step 4: TypeScript와 CMS 대상 E2E를 실행한다.**

Run:

```powershell
pnpm.cmd exec tsc --noEmit
pnpm.cmd exec playwright test e2e/website-content-editor.spec.ts --project=chromium
```

Expected: 타입 검사와 기존 CMS 회귀·새 수명주기 테스트가 통과한다.

### Task 3: 실제 확인과 기록

**Files:**
- Modify: `docs/changes/2026-09-10-web-content-management.md`
- Modify: `docs/overview/current-development-context.md`
- Modify: `docs/architecture/full-site-implementation-design.md`
- Modify: `docs/decisions/decision-log.md`
- Modify: `docs/superpowers/plans/2026-09-11-content-page-lifecycle.md`

- [x] **Step 1: API 이미지를 한 번만 재빌드해 V14 migration과 readiness를 확인한다.**
- [ ] **Step 2: 실제 CMS에서 기존 페이지를 저장·보관하지 않고 보관 제어와 기존 공개 경로를 확인한다.**
- [x] **Step 3: 완료된 검증 결과와 영구 삭제·이동·깊은 트리의 미구현 상태를 기록한다.**
- [x] **Step 4: `git diff --check`으로 공백 오류를 확인한다.**

## 2026-09-11 구현·검증 결과

- 실패 단계에서 새 `WebsitePageIntegrationTest`는 수명주기 타입·서비스 API가 없어 컴파일되지 않았다. 구현 뒤 일반 페이지 생성·발행·보관·복원·명시적 재발행, 공개 resolve·메뉴 제외, 초안 미디어 usage 보존, 오래된 상태 전환, 보관 중 저장·발행 거부, 지점 권한과 비일반 페이지 404를 확인했다.
- V14 migration은 `lifecycle_status`, `lifecycle_version`, 보관 시각·행위자와 `ARCHIVED`·`RESTORED` 감사 action을 추가했다. 보관은 현재 `published_content`와 공개 메뉴 노출, `PUBLISHED` 미디어 usage만 비우며 `DRAFT` usage·경로·발행 이력은 남긴다. 복원은 `ACTIVE` 초안으로만 되돌리고 공개본을 복구하지 않는다.
- 관리자에는 페이지 보관 확인, 보관 배지, 전체 편집 영역의 읽기 전용 상태, `초안으로 복원`, 재발행 안내를 추가했다. 저장되지 않은 편집이 있으면 보관 버튼을 비활성화해 로컬 변경을 버리지 않는다.
- 대상 서버 `WebsitePageIntegrationTest` 10건과 최종 CMS 서버 회귀 `WebsiteMediaIntegrationTest,WebContentIntegrationTest,WebsitePageIntegrationTest` 22건이 실패·오류 없이 통과했다. `pnpm.cmd exec tsc --noEmit`과 CMS Playwright 15건도 통과했다.
- `docker compose up -d --build api` 뒤 Flyway V14 적용 로그와 `http://127.0.0.1:4080/actuator/health/readiness`의 `UP`을 확인했다. 공개 `/brand/story`는 `CONTENT_PAGE`, `hotelId: null`으로 resolve됐고 공개 navigation에도 남아 있는 활성 페이지임을 읽기 전용으로 확인했다.
- 실제 사용자 페이지를 보관하거나 저장하지 않았다. 실제 CMS 브라우저에서는 기존 속초 랜딩을 읽기 전용으로 열어 기존 CMS 렌더를 확인했지만, 사용자 콘텐츠 보호를 위해 일반 페이지 보관 제어를 직접 실행하지 않았다. 이 부분은 향후 전용 검증 데이터에서만 확인한다.
- `git diff --check`은 종료 코드 0으로 통과했다. 기존 작업 트리의 LF→CRLF 변환 경고만 출력됐고 공백 오류는 없었다.
