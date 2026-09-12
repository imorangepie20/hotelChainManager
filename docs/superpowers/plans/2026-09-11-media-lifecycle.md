# CMS 미디어 자산 메타데이터·보관 구현 계획

**Goal:** 본사가 고객 페이지를 깨뜨리지 않고 미디어 자산 메타데이터를 수정하고 참조 없는 자산을 보관·복원하게 한다.

**Architecture:** 기존 `website_media_asset`의 `status`와 `version`을 수명주기 기준으로 사용한다. 페이지 저장은 활성 행에 공유 잠금을 잡고, 보관은 배타 잠금과 `website_media_usage`를 확인하므로 보관과 새 참조 저장이 교차하지 않는다. 관리자 선택기는 활성 자산만 선택하게 하고 보관 자산은 별도 관리 영역에서 복원한다.

**Tech Stack:** Java 21, Spring Boot, JdbcTemplate, Flyway, PostgreSQL, Next.js 16, React 19, Playwright, JUnit.

**Spec:** `docs/superpowers/specs/2026-09-11-media-lifecycle-design.md`

## Global Constraints

- `HQ_ADMIN`만 미디어 목록·수정·보관·복원을 수행한다.
- 자산명은 1~160자, 기본 대체 텍스트는 1~200자이며 기존 페이지별 alt를 수정하지 않는다.
- 초안 또는 발행 사용 위치가 하나라도 있으면 보관을 거부한다.
- 물리 파일 삭제, 파일 교체, 페이지 삭제, CDN·객체 저장소, 다국어 alt는 이 계획에 넣지 않는다.
- 가격·재고·예약·AI의 소유권은 변경하지 않는다.
- 변경 범위의 대상 테스트만 실행하고, 모든 사용자 데이터에 영향을 주는 실제 보관은 수행하지 않는다.

---

### Task 1: 상태 제약과 서버 수명주기 계약

**Files:**
- Create: `services/api/src/main/resources/db/migration/V13__website_media_archive.sql`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaMetadataRequest.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaVersionRequest.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaConflictException.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaAsset.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaService.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaManagementController.java`
- Reuse: `services/api/src/main/java/team/hotelchain/web/ApiExceptionHandler.java`
- Test: `services/api/src/test/java/team/hotelchain/webcontent/WebsiteMediaIntegrationTest.java`

**Interfaces:**
- Consumes: V12 `website_media_asset`, `website_media_usage`, `StaffAccessService`, `WebsiteMediaReferenceService`.
- Produces: `WebsiteMediaAsset.status(): String`, `version(): int`; `updateMetadata(token, id, request)`, `archive(token, id, request)`, `restore(token, id, request)`.

- [x] **Step 1: Write failing integration tests for the lifecycle contract**

```java
@Test
void updatesMetadataWithOptimisticVersionAndLeavesPageAltUntouched() {
    WebsiteMediaAsset updated = media.updateMetadata(hq.token(), BUNDLED_ASSET,
        new WebsiteMediaMetadataRequest("새 자산명", "새 기본 alt", 1));
    assertThat(updated.version()).isEqualTo(2);
    assertThat(media.usages(hq.token(), BUNDLED_ASSET)).allSatisfy(usage ->
        assertThat(usage.altText()).isNotEqualTo("새 기본 alt"));
}

@Test
void refusesToArchiveAnAssetReferencedByDraftOrPublishedPage() {
    assertThatThrownBy(() -> media.archive(hq.token(), BUNDLED_ASSET,
        new WebsiteMediaVersionRequest(1)))
        .isInstanceOf(WebsiteMediaConflictException.class)
        .hasMessageContaining("사용 중");
}
```

- [x] **Step 2: Run the focused test to verify the missing API fails**

Run: `mvn -Dtest=WebsiteMediaIntegrationTest test`

Expected: compilation failure because lifecycle methods and request types do not exist.

- [x] **Step 3: Extend the schema and implement the smallest server behavior**

```sql
ALTER TABLE website_media_asset DROP CONSTRAINT IF EXISTS website_media_asset_status_check;
ALTER TABLE website_media_asset
  ADD CONSTRAINT website_media_asset_status_check CHECK (status IN ('ACTIVE', 'ARCHIVED'));
```

```java
WebsiteMediaAsset archive(String token, UUID id, WebsiteMediaVersionRequest request) {
    StaffPrincipal actor = access.requireHeadquarters(token);
    MediaAssetRow asset = requireAssetForUpdate(id);
    requireVersion(asset, request.expectedVersion());
    if (usageCount(id) > 0) throw inUse();
    updateStatus(id, "ARCHIVED", actor.id());
    return catalogAsset(id);
}
```

`requireActiveAsset` must read an active row with `FOR KEY SHARE`; archive and restore must lock the row with `FOR UPDATE`. The public uploaded-file lookup must require `ACTIVE`.

- [x] **Step 4: Run the focused test to verify server behavior passes**

Run: `mvn -Dtest=WebsiteMediaIntegrationTest test`

Expected: metadata, conflict, archive, restore, authorization, and public-delivery assertions pass.

- [x] **Step 5: Add the HTTP controller mapping and error status mapping**

```java
@PatchMapping("/{mediaId}")
WebsiteMediaAsset update(@PathVariable UUID mediaId, @RequestBody WebsiteMediaMetadataRequest request,
        @RequestHeader("X-Staff-Session") String token) {
    return media.updateMetadata(token, mediaId, request);
}
```

`WebsiteMediaConflictException`은 기존 `BusinessConflictException`을 상속하므로, 기존 예외 처리기가 안정 코드와 함께 HTTP 409으로 변환한다. 별도 예외 처리기 변경은 필요 없었고, 존재하지 않는 자산은 `WEBSITE_MEDIA_NOT_FOUND` 404를 유지했다.

### Task 2: 관리자 카탈로그 관리 UX

**Files:**
- Modify: `SDTPL_ADM/src/lib/staff-api.ts`
- Modify: `SDTPL_ADM/src/components/hotel-admin/media-picker-dialog.tsx`
- Test: `SDTPL_ADM/e2e/website-content-editor.spec.ts`

**Interfaces:**
- Consumes: updated `WebsiteMediaAsset`, `PATCH /api/staff/website/media/{id}`, archive and restore endpoints.
- Produces: active-only selection cards, metadata save, protected archive, archived-asset restore controls.

- [x] **Step 1: Write failing Playwright cases for management actions**

```ts
test("updates catalog metadata with the selected asset version", async ({ page }) => {
  await page.getByRole("button", { name: "미디어 선택" }).click();
  await page.getByRole("button", { name: "속초 해안 대표 이미지 선택" }).click();
  await page.getByLabel("자산명").fill("수정한 속초 해안 이미지");
  const request = page.waitForRequest((item) => item.method() === "PATCH");
  await page.getByRole("button", { name: "자산 정보 저장" }).click();
  expect((await request).postDataJSON()).toEqual({
    displayName: "수정한 속초 해안 이미지", defaultAltText: expect.any(String), expectedVersion: 1,
  });
});
```

- [x] **Step 2: Run the one E2E file and verify it fails at the missing controls**

Run: `pnpm.cmd exec playwright test e2e/website-content-editor.spec.ts --project=chromium`

Expected: the new locator cannot find `자산 정보 저장`.

- [x] **Step 3: Add typed client calls and selected-asset management state**

```ts
export function updateWebsiteMedia(token: string, mediaId: string, input: WebsiteMediaMetadataInput) {
  return mediaRequest<WebsiteMediaAsset>(`/api/staff/website/media/${mediaId}`, token, {
    method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input),
  });
}
```

Fetch catalog data with `includeArchived=true`, filter `status === "ACTIVE"` for the selection cards, and update the local asset array from each successful response.

- [x] **Step 4: Implement accessible archive and restore controls**

Use the existing `AlertDialog` for archive confirmation. Disable `보관` when `usageCount > 0`, show why it is disabled, and leave the ordinary bottom `선택` button disabled for `ARCHIVED` assets. Show archived assets in a separate section with `복원`.

- [x] **Step 5: Run TypeScript and the targeted E2E file**

Run:

```powershell
pnpm.cmd exec tsc --noEmit
pnpm.cmd exec playwright test e2e/website-content-editor.spec.ts --project=chromium
```

Expected: type check passes and existing selection/upload tests plus new lifecycle tests pass.

### Task 3: Targeted integration, browser check, and documentation

**Files:**
- Modify: `docs/superpowers/plans/2026-09-11-media-lifecycle.md`
- Modify: `docs/changes/2026-09-10-web-content-management.md`
- Modify: `docs/overview/current-development-context.md`
- Modify: `docs/architecture/full-site-implementation-design.md`
- Modify: `docs/decisions/decision-log.md`

**Interfaces:**
- Consumes: completed API and admin UI.
- Produces: recorded validation evidence and remaining physical-deletion limitation.

- [x] **Step 1: Build the API image once and apply V13**

Run: `docker compose up -d --build api`

Expected: Flyway applies V13 and `/actuator/health/readiness` returns `UP`.

- [x] **Step 2: Use the real CMS browser without mutating the bundled asset**

Open the existing media picker, select the bundled asset, verify metadata fields and that the in-use archive control is disabled with a usage explanation, then close the dialog without saving.

- [x] **Step 3: Record only completed evidence and known limits**

Mark plan checkboxes, record targeted test counts and browser result in Korean, and state that no user asset was archived or deleted in the live development catalog.

- [x] **Step 4: Check whitespace errors before reporting**

Run: `git diff --check`

Expected: exit code 0; line-ending warnings alone are not diff errors.

## 2026-09-11 구현·검증 결과

- 먼저 `WebsiteMediaIntegrationTest`와 새 Playwright 두 건을 작성했다. 서버 API·요청 타입과 관리자 제어가 없던 상태에서 각각 컴파일 또는 locator 실패를 확인한 뒤 구현을 시작했다.
- 서버는 V13 migration, 메타데이터 수정, 활성/보관 상태 전환, 낙관적 버전, 초안·발행 사용 위치 보호를 구현했다. 기본 alt 변경은 기존 페이지별 alt와 사용 위치의 alt를 바꾸지 않는다.
- 관리자 선택기는 활성 자산만 선택 카드로 표시하고, 사용 중 자산은 보관 버튼을 비활성화한다. 보관 확인 대화상자와 보관 목록의 복원 제어도 연결했다.
- 대상 서버 통합 테스트 `WebsiteMediaIntegrationTest` 7건이 통과했다. 이어 `WebsiteMediaIntegrationTest,WebContentIntegrationTest,WebsitePageIntegrationTest` 20건이 통과했다.
- `pnpm.cmd exec tsc --noEmit`와 `pnpm.cmd exec playwright test e2e/website-content-editor.spec.ts --project=chromium`을 실행해 관리자 타입 검사와 CMS Playwright 9건 통과를 확인했다.
- `docker compose up -d --build api` 뒤 Flyway V13 적용 로그와 `http://127.0.0.1:4080/actuator/health/readiness`의 `UP`을 확인했다.
- 실제 본사 CMS에서 이미 사용 중인 내장 자산을 열어 메타데이터 필드와 `사용 위치가 있어 보관할 수 없습니다.` 안내, 비활성 `보관` 제어를 확인하고 저장·보관 없이 대화상자를 닫았다. 실제 개발 카탈로그의 사용자 자산을 보관하거나 삭제하지 않았다.
- `git diff --check`은 공백 오류 없이 통과했다. 기존 작업 트리의 LF→CRLF 변환 경고만 출력됐다.

### 후속 경합·캐시 보완

- 선택기를 닫을 때 저장하지 않은 메타데이터 입력을 폐기하고, 다시 열 때 서버 카탈로그로 입력을 다시 채웠다. 현재 페이지에서 아직 저장하지 않은 HERO 자산도 보관 제어에서 보호한다.
- 상태 전환 성공 뒤 선택기 상태를 반환 자산으로 먼저 갱신하고, 최신 카탈로그·사용 위치 재조회를 요청한다. 409 버전/사용 위치 충돌은 최신 값을 다시 표시하고 중복 상태 전환을 비활성화한다. 보관된 업로드 카드는 차단될 공개 이미지 URL을 읽지 않고 안내 placeholder를 표시한다.
- 공개 업로드 전달은 `public, max-age=0, must-revalidate`로 변경했다. 이전 `immutable` 응답을 이미 저장한 클라이언트 캐시는 서버가 회수할 수 없다는 한계는 남는다.
- 이 보완과 V14 페이지 수명주기까지 포함한 최종 대상 서버 회귀는 22건, CMS Playwright는 15건을 통과했다.
