# CMS 미디어 초안 사용 위치 일괄 교체 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 본사 관리자가 영향 범위를 확인한 뒤 한 미디어 자산의 활성 한국어·영어 초안 사용 위치를 새 업로드 자산으로 원자적으로 교체한다.

**Architecture:** 새 `WebsiteMediaDraftReplacementService`가 읽기 전용 영향 조회와 확정 트랜잭션을 조정한다. 기존 페이지·번역 서비스와 `WebsiteMediaReferenceService`에 좁은 내부 연산을 추가해 문서 검증, usage 동기화, 영어 승인 무효화를 재사용한다. 관리자는 별도 교체 대화상자에서 새 자산을 업로드하고 영향 목록을 확인한 뒤 한 번만 확정한다.

**Tech Stack:** Java 21, Spring Boot 4.1.1, JDBC, PostgreSQL 16, JUnit 5, React 19, Next.js 16, TypeScript 5, Base UI AlertDialog/Dialog, Playwright 1.60

**Spec:** `docs/superpowers/specs/2026-09-13-media-draft-usage-bulk-replacement-design.md`

## Global Constraints

- `HQ_ADMIN`만 영향 조회와 확정을 실행한다.
- `HOME_PAGE`, `HOTEL_LANDING`, 활성 `CONTENT_PAGE`의 한국어·영어 `DRAFT` usage만 변경한다.
- 새 대상은 기존 업로드 API가 만든 `UPLOADED`·`ACTIVE` 자산이어야 한다.
- 자산 UUID·전달 URL만 바꾸고 alt·캡션·블록 순서는 유지한다.
- 공개본, `PUBLISHED` usage, 과거 snapshot, 보관 페이지, 기존 자산·파일은 변경하지 않는다.
- 대상 하나라도 stale이면 전체 트랜잭션을 `409 WEBSITE_MEDIA_REPLACEMENT_CONFLICT`로 롤백한다.
- 영어 초안 변경은 기존 저장과 같은 방식으로 검토·승인을 무효화한다.
- 새 DB migration과 파일 덮어쓰기를 추가하지 않는다.
- 실제 사용자 페이지·자산에는 검증용 mutation을 보내지 않는다.
- 기존 영문 번역·검수 역할 분리 동작과 사용자 변경을 보존한다.

## 파일 구조

- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaDraftReplacementUsage.java` — 영향 목록의 한 초안 위치
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaDraftReplacementImpact.java` — 원본·대상과 포함/제외 집계
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaDraftReplacementTarget.java` — 확정 시 기대하는 위치와 version
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaDraftReplacementRequest.java` — 확정 요청
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaDraftReplacementResult.java` — 변경한 위치·초안 수
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaDraftReplacementService.java` — 영향 조회와 원자적 확정 조정
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaManagementController.java` — GET impact, POST replacement endpoint
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaService.java` — package 내부 자산 조회·검증 재사용
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaReferenceService.java` — 문서 내 자산 쌍 교체와 실제 field path 반환
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageService.java` — 한국어 초안 교체·version·audit·usage
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteTranslationService.java` — 영어 초안 교체·승인 무효화·audit·usage
- Modify/Test: `services/api/src/test/java/team/hotelchain/webcontent/WebsiteMediaIntegrationTest.java` — 영향, 성공, 보존, 충돌, 권한 통합 회귀
- Modify: `SDTPL_ADM/src/lib/staff-api.ts` — impact/request/result 타입과 API 함수
- Create: `SDTPL_ADM/src/components/hotel-admin/media-draft-usage-replacement-dialog.tsx` — 업로드, 영향 조회, 확정 UI
- Modify: `SDTPL_ADM/src/components/hotel-admin/media-picker-dialog.tsx` — 활성 원본 자산에서 일괄 교체 진입과 완료 후 새로고침
- Modify/Test: `SDTPL_ADM/e2e/website-content-editor.spec.ts` — 관리자 교체 흐름, 취소, 충돌, 모바일
- Modify: `docs/architecture/cms-functional-specification.md` — 구현된 일괄 교체 계약과 후속 범위
- Modify: `docs/overview/current-development-context.md` — 변경·검증·미검증 기록
- Create: `docs/changes/2026-09-13-media-draft-usage-bulk-replacement.md` — 한국어 변경 기록

---

### Task 1: 서버 영향 조회 계약

**Files:**
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaDraftReplacementUsage.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaDraftReplacementImpact.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaDraftReplacementTarget.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaDraftReplacementRequest.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaDraftReplacementResult.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaDraftReplacementService.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaManagementController.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaService.java`
- Test: `services/api/src/test/java/team/hotelchain/webcontent/WebsiteMediaIntegrationTest.java`

**Interfaces:**
- Consumes: `StaffAccessService.requireHeadquarters(String)`, `WebsiteMediaService.MediaAssetRow`, `website_media_usage.locale/document_state/field_path`
- Produces: `WebsiteMediaDraftReplacementService.impact(String, UUID, UUID): WebsiteMediaDraftReplacementImpact`
- Produces: `GET /api/staff/website/media/{sourceMediaId}/draft-replacement-impact?targetMediaId={targetMediaId}`

- [ ] **Step 1: 실패하는 영향 조회 통합 테스트 작성**

`WebsiteMediaIntegrationTest`에 한국어·영어 활성 초안, 발행 usage, 보관 페이지 초안을 같은 원본 자산으로 구성한다. 새 업로드 자산을 대상으로 영향 조회하고 literal 기대값을 검증한다.

```java
WebsiteMediaDraftReplacementImpact impact = replacements.impact(token, source.id(), target.id());

assertThat(impact.sourceAsset().id()).isEqualTo(source.id());
assertThat(impact.targetAsset().id()).isEqualTo(target.id());
assertThat(impact.replaceableUsages())
        .extracting(WebsiteMediaDraftReplacementUsage::locale,
                WebsiteMediaDraftReplacementUsage::fieldPath)
        .containsExactly(
                tuple("en", "blocks[0].imageAssetId"),
                tuple("ko", "blocks[0].imageAssetId"));
assertThat(impact.publishedUsageCount()).isEqualTo(2);
assertThat(impact.archivedDraftUsageCount()).isEqualTo(1);
```

생산 코드에서 원본 자산 조회를 빈 결과로 바꾸면 실패하는 테스트다. 기대 목록은 helper로 계산하지 않는다.

- [ ] **Step 2: RED 확인**

Run:

```powershell
& 'C:\Users\jowoo\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd' '-Dtest=WebsiteMediaIntegrationTest#reportsReplaceableDraftMediaUsagesWithoutWriting' test
```

Expected: `WebsiteMediaDraftReplacementService` 또는 DTO가 없어 test compilation이 실패한다.

- [ ] **Step 3: DTO와 최소 조회 구현**

다음 record를 정확히 추가한다.

```java
public record WebsiteMediaDraftReplacementUsage(
        UUID pageId, String pageLabel, String pagePath, String pageType,
        String locale, String fieldPath, int expectedDraftVersion) {}

public record WebsiteMediaDraftReplacementImpact(
        WebsiteMediaAsset sourceAsset,
        WebsiteMediaAsset targetAsset,
        List<WebsiteMediaDraftReplacementUsage> replaceableUsages,
        int publishedUsageCount,
        int archivedDraftUsageCount) {}

public record WebsiteMediaDraftReplacementTarget(
        UUID pageId, String locale, String fieldPath, int expectedDraftVersion) {}

public record WebsiteMediaDraftReplacementRequest(
        UUID targetMediaId, int expectedSourceVersion, int expectedTargetVersion,
        List<WebsiteMediaDraftReplacementTarget> targets) {}

public record WebsiteMediaDraftReplacementResult(
        UUID sourceMediaId, UUID targetMediaId, int replacedUsageCount, int changedDraftCount) {}
```

`WebsiteMediaService`에 package-private 조회를 추가한다. `requireReplacementTarget`은 `origin='UPLOADED'`, `status='ACTIVE'`를 모두 검사한다.

```java
MediaAssetRow requireReplacementSource(UUID mediaId, String lockClause)
MediaAssetRow requireReplacementTarget(UUID mediaId, String lockClause)
WebsiteMediaAsset catalogAssetForReplacement(UUID mediaId)
```

`WebsiteMediaDraftReplacementService.impact`는 `@Transactional(readOnly = true)`를 사용한다. `website_page.lifecycle_status='ACTIVE'`만 `replaceableUsages`에 넣고 locale별 draft version을 조인한다. 정렬은 `page_path, locale, field_path`다.

- [ ] **Step 4: controller GET 연결**

```java
@GetMapping("/{mediaId}/draft-replacement-impact")
public WebsiteMediaDraftReplacementImpact replacementImpact(
        @PathVariable UUID mediaId,
        @RequestParam UUID targetMediaId,
        @RequestHeader("X-Staff-Session") String token) {
    return replacements.impact(token, mediaId, targetMediaId);
}
```

`WebsiteMediaManagementController` 생성자에 `WebsiteMediaDraftReplacementService replacements`를 주입한다.

- [ ] **Step 5: GREEN 확인**

Step 2 명령을 다시 실행한다. Expected: `Tests run: 1, Failures: 0, Errors: 0`.

- [ ] **Step 6: 조회 거부 회귀 추가·실행**

같은 자산, 번들 대상, 보관 대상, `HQ_EDITOR`, `HQ_PUBLISHER`, `BRANCH_STAFF`를 별도 test method에서 검증한다.

```java
assertThatThrownBy(() -> replacements.impact(editor.token(), source.id(), target.id()))
        .isInstanceOf(StaffAccessDeniedException.class);
assertThatThrownBy(() -> replacements.impact(admin.token(), source.id(), source.id()))
        .isInstanceOf(IllegalArgumentException.class);
```

Run:

```powershell
& 'C:\Users\jowoo\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd' '-Dtest=WebsiteMediaIntegrationTest#reportsReplaceableDraftMediaUsagesWithoutWriting+rejectsInvalidDraftReplacementImpactRequests' test
```

Expected: 2 tests pass.

- [ ] **Step 7: 커밋**

```powershell
git add -- services/api/src/main/java/team/hotelchain/webcontent services/api/src/test/java/team/hotelchain/webcontent/WebsiteMediaIntegrationTest.java
git commit -m "feat: report bulk media replacement impact"
```

### Task 2: 원자적 한국어·영어 초안 교체

**Files:**
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaDraftReplacementService.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaReferenceService.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageService.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteTranslationService.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaManagementController.java`
- Test: `services/api/src/test/java/team/hotelchain/webcontent/WebsiteMediaIntegrationTest.java`

**Interfaces:**
- Consumes: Task 1 DTO와 `impact`
- Produces: `WebsiteMediaReferenceService.replaceAssetReferences(...)`
- Produces: `WebsitePageService.replaceDraftMediaReferences(...)`
- Produces: `WebsiteTranslationService.replaceDraftMediaReferences(...)`
- Produces: `WebsiteMediaDraftReplacementService.replace(String, UUID, WebsiteMediaDraftReplacementRequest): WebsiteMediaDraftReplacementResult`
- Produces: `POST /api/staff/website/media/{sourceMediaId}/draft-replacements`

- [ ] **Step 1: 성공·보존 실패 테스트 작성**

활성 홈, 지점, 일반 페이지의 한국어 초안과 승인된 영어 초안에 같은 source를 둔다. 확정 후 다음을 검증한다.

```java
WebsiteMediaDraftReplacementResult result = replacements.replace(token, source.id(), requestFrom(impact));

assertThat(result.replacedUsageCount()).isEqualTo(4);
assertThat(result.changedDraftCount()).isEqualTo(4);
assertThat(media.usages(token, target.id()))
        .filteredOn(usage -> usage.documentState().equals("DRAFT"))
        .hasSize(4)
        .allSatisfy(usage -> assertThat(usage.altText()).isIn("홈 alt", "지점 alt", "본문 alt", "영어 alt"));
assertThat(media.usages(token, source.id()))
        .filteredOn(usage -> usage.documentState().equals("PUBLISHED"))
        .hasSize(4);
assertThat(translations.review(token, englishPageId).status())
        .isEqualTo(WebsiteTranslationReviewStatus.DRAFT);
```

발행 resolve, 기존 snapshot JSON, source·target 파일 bytes, 관련 없는 페이지 문서를 교체 전 literal fixture와 비교한다.

- [ ] **Step 2: RED 확인**

```powershell
& 'C:\Users\jowoo\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd' '-Dtest=WebsiteMediaIntegrationTest#replacesAllActiveDraftUsagesAtomicallyAndPreservesPublishedState' test
```

Expected: `replace`가 없어 test compilation이 실패한다.

- [ ] **Step 3: 문서 참조 교체 primitive 구현**

`WebsiteMediaReferenceService`에 다음 record와 method를 추가한다.

```java
record MediaReferenceReplacement(Map<String, Object> content, List<String> fieldPaths) {}

MediaReferenceReplacement replaceAssetReferences(
        String pageType, Map<String, Object> content,
        UUID sourceId, String sourcePath,
        UUID targetId, String targetPath)
```

landing은 `heroAssetId`와 `heroImage`, structured page는 HERO와 IMAGE_GALLERY의 `imageAssetId`와 `imageSrc`를 함께 바꾼다. source UUID와 source 전달 URL이 쌍으로 일치할 때만 바꾼다. alt·caption·block ID는 복사본에서 그대로 둔다. 반환 `fieldPaths`는 실제 변경된 `imageAssetId` 경로를 정렬한다. 변경 뒤 `normalizeLandingContent` 또는 `normalizeStructuredContent`를 호출한다.

- [ ] **Step 4: 한국어·영어 단일 초안 내부 연산 구현**

`WebsitePageService`:

```java
int replaceDraftMediaReferences(
        StaffPrincipal actor, UUID pageId, int expectedDraftVersion,
        WebsiteMediaService.MediaAssetRow source,
        WebsiteMediaService.MediaAssetRow target,
        List<String> expectedFieldPaths)
```

활성 page row를 잠그고 version과 lifecycle을 다시 확인한다. 실제 변경 경로가 기대 경로와 다르면 conflict를 던진다. 기존 page type validator를 통과한 뒤 `draft_content`, `draft_version + 1`, `updated_by`를 갱신하고 한국어 `DRAFT` usage를 동기화한다. `MEDIA_DRAFT_USAGES_REPLACED` audit에는 `locale: ko`, source/target ID, count를 기록한다.

`WebsiteTranslationService`에도 같은 signature를 추가한다. locale은 `en`으로 고정한다. 기존 `normalize`, draft row update, `media.synchronize`, `audit`, `reviewEvent`를 재사용한다. 이전 상태가 `IN_REVIEW`, `APPROVED`, `PUBLISHED`면 새 draft version으로 `APPROVAL_INVALIDATED`를 한 번 기록한다.

- [ ] **Step 5: 조정 transaction과 POST endpoint 구현**

`replace`는 `@Transactional`이다. 요청 null, version 1 미만, 빈 targets, 중복 target, `ko|en` 외 locale을 `400`으로 거부한다. source·target 자산을 UUID 순서로 `for update` 잠근다. impact를 잠금 상태에서 다시 계산하고 요청 target 집합과 정확히 비교한다. pageId·locale 순서로 Task 2 Step 4 연산을 호출한다.

```java
@PostMapping("/{mediaId}/draft-replacements")
public WebsiteMediaDraftReplacementResult replaceDraftUsages(
        @PathVariable UUID mediaId,
        @RequestBody WebsiteMediaDraftReplacementRequest request,
        @RequestHeader("X-Staff-Session") String token) {
    return replacements.replace(token, mediaId, request);
}
```

불일치는 다음 예외를 사용한다.

```java
throw new WebsiteMediaConflictException(
        "WEBSITE_MEDIA_REPLACEMENT_CONFLICT",
        "미디어 사용 위치가 변경되었습니다. 영향 범위를 다시 확인해 주세요.");
```

교체 대상이 0개면 `WEBSITE_MEDIA_REPLACEMENT_EMPTY`를 사용한다.

- [ ] **Step 6: GREEN 확인**

Step 2 명령을 다시 실행한다. Expected: 1 test pass.

- [ ] **Step 7: 충돌·전체 롤백 테스트 작성**

source version, target version, 한 페이지 draft version, usage field path, lifecycle을 각각 stale로 만든 매개변수 테스트를 추가한다. 각 실행 뒤 모든 대상 draft JSON/version/usage/audit/review event가 fixture와 같은지 검증한다.

```java
assertThatThrownBy(() -> replacements.replace(token, source.id(), staleRequest))
        .isInstanceOf(WebsiteMediaConflictException.class)
        .extracting(error -> ((WebsiteMediaConflictException) error).code())
        .isEqualTo("WEBSITE_MEDIA_REPLACEMENT_CONFLICT");
assertThat(jdbc.queryForObject("select count(*) from website_page_audit where action = 'MEDIA_DRAFT_USAGES_REPLACED'", Integer.class))
        .isZero();
```

- [ ] **Step 8: 대상 서버 테스트 실행**

```powershell
& 'C:\Users\jowoo\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd' '-Dtest=WebsiteMediaIntegrationTest' test
```

Expected: 해당 클래스 전체 통과. 실패·오류 0.

- [ ] **Step 9: 커밋**

```powershell
git add -- services/api/src/main/java/team/hotelchain/webcontent services/api/src/test/java/team/hotelchain/webcontent/WebsiteMediaIntegrationTest.java
git commit -m "feat: replace active draft media usages atomically"
```

### Task 3: 관리자 영향 확인·확정 UI

**Files:**
- Modify: `SDTPL_ADM/src/lib/staff-api.ts`
- Create: `SDTPL_ADM/src/components/hotel-admin/media-draft-usage-replacement-dialog.tsx`
- Modify: `SDTPL_ADM/src/components/hotel-admin/media-picker-dialog.tsx`
- Test: `SDTPL_ADM/e2e/website-content-editor.spec.ts`

**Interfaces:**
- Consumes: Task 1·2 REST API
- Produces: `getWebsiteMediaDraftReplacementImpact(...)`
- Produces: `replaceWebsiteMediaDraftUsages(...)`
- Produces: `MediaDraftUsageReplacementDialog`
- Produces: E2E 파일의 `openBulkReplacementImpact(page: Page): Promise<void>` — 실제 UI에서 원본 선택, 대화상자 열기, 업로드, 영향 표시를 수행

- [ ] **Step 1: 실패하는 Playwright 핵심 흐름 작성**

기존 media route fixture에 impact GET과 replacement POST를 추가한다. UI test는 원본 자산 선택, 시작, 업로드, 영향 확인, 확정 순서를 실행한다.

```ts
await page.getByRole("button", { name: "초안 사용 위치 일괄 교체" }).click();
const dialog = page.getByRole("dialog", { name: "초안 사용 위치 일괄 교체" });
await dialog.getByLabel("새 이미지 파일").setInputFiles(replacementFile);
await dialog.getByRole("button", { name: "새 자산 업로드" }).click();
await expect(dialog.getByText("한국어 초안 2곳 · 영어 초안 1곳")).toBeVisible();
await dialog.getByRole("button", { name: "초안 위치 교체하기" }).click();
expect(replacementRequest).toEqual({
  targetMediaId: NEW_ASSET,
  expectedSourceVersion: 1,
  expectedTargetVersion: 1,
  targets: expectedTargets,
});
```

확정 전 POST가 없고 성공 뒤 source·target usage GET이 다시 호출되는지 실제 route count로 검증한다.

- [ ] **Step 2: RED 확인**

```powershell
pnpm exec playwright test e2e/website-content-editor.spec.ts --grep "bulk replaces active draft media usages after impact confirmation"
```

Expected: `초안 사용 위치 일괄 교체` 버튼이 없어 실패한다.

- [ ] **Step 3: staff API 타입과 함수 구현**

```ts
export type WebsiteMediaDraftReplacementUsage = {
  pageId: string; pageLabel: string; pagePath: string; pageType: string;
  locale: "ko" | "en"; fieldPath: string; expectedDraftVersion: number;
};
export type WebsiteMediaDraftReplacementImpact = {
  sourceAsset: WebsiteMediaAsset;
  targetAsset: WebsiteMediaAsset;
  replaceableUsages: WebsiteMediaDraftReplacementUsage[];
  publishedUsageCount: number;
  archivedDraftUsageCount: number;
};
export type WebsiteMediaDraftReplacementRequest = {
  targetMediaId: string;
  expectedSourceVersion: number;
  expectedTargetVersion: number;
  targets: Array<{ pageId: string; locale: "ko" | "en"; fieldPath: string; expectedDraftVersion: number }>;
};
export type WebsiteMediaDraftReplacementResult = {
  sourceMediaId: string; targetMediaId: string; replacedUsageCount: number; changedDraftCount: number;
};
```

```ts
export function getWebsiteMediaDraftReplacementImpact(token: string, sourceMediaId: string, targetMediaId: string) {
  return mediaRequest<WebsiteMediaDraftReplacementImpact>(
    `/api/staff/website/media/${sourceMediaId}/draft-replacement-impact?targetMediaId=${encodeURIComponent(targetMediaId)}`,
    token,
  );
}

export function replaceWebsiteMediaDraftUsages(token: string, sourceMediaId: string, input: WebsiteMediaDraftReplacementRequest) {
  return mediaRequest<WebsiteMediaDraftReplacementResult>(`/api/staff/website/media/${sourceMediaId}/draft-replacements`, token, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input),
  });
}
```

- [ ] **Step 4: 교체 대화상자 구현**

`MediaDraftUsageReplacementDialog` props:

```ts
{
  token: string;
  sourceAsset: WebsiteMediaAsset;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCompleted: (result: WebsiteMediaDraftReplacementResult) => void;
}
```

새 자산 업로드 성공 뒤에만 impact를 읽는다. impact target을 request targets로 그대로 옮기되 source·target version은 impact 응답을 사용한다. `requestGeneration` ref로 닫힌 뒤 도착한 upload/impact/replace 응답을 무시한다. 영향 목록은 page label/path, locale, field path를 표시한다. 공개 usage와 보관 초안 제외 수를 별도로 표시한다.

확정 버튼은 upload, impact, replace 중이거나 대상이 0개면 disabled다. `StaffApiError.code === "WEBSITE_MEDIA_REPLACEMENT_CONFLICT"`이면 `영향 범위가 변경되었습니다. 다시 확인해 주세요.`를 표시하고 impact를 다시 읽지 않는다.

- [ ] **Step 5: picker 진입과 완료 새로고침 연결**

일반 선택 모드의 활성 자산 정보에만 버튼을 추가한다. 현재 페이지 한 필드 교체 모드에서는 표시하지 않는다.

```tsx
<Button
  type="button"
  variant="outline"
  disabled={savingMetadata || changingStatus || !usages.some((usage) => usage.documentState === "DRAFT")}
  onClick={() => setBulkReplacementOpen(true)}
>
  초안 사용 위치 일괄 교체
</Button>
```

완료 시 `refreshCatalog(sourceAsset.id, true)`와 `refreshUsages(sourceAsset.id)`를 실행하고 성공 문구에 변경 위치 수를 표시한다. 새 target 자산은 새 catalog 응답으로 들어온다.

- [ ] **Step 6: GREEN 확인**

Step 2 명령을 다시 실행한다. Expected: 1 test pass.

- [ ] **Step 7: 취소·충돌·모바일 회귀 작성**

다음 세 test를 추가한다.

```ts
test("cancels bulk draft media replacement before mutation and restores focus", async ({ page }) => {
  const trigger = page.getByRole("button", { name: "초안 사용 위치 일괄 교체" });
  await trigger.click();
  const dialog = page.getByRole("dialog", { name: "초안 사용 위치 일괄 교체" });
  await dialog.getByRole("button", { name: "취소" }).click();
  expect(replacementPostCount).toBe(0);
  await expect(trigger).toBeFocused();
});

test("keeps impact visible when bulk draft media replacement conflicts", async ({ page }) => {
  await openBulkReplacementImpact(page);
  await page.getByRole("button", { name: "초안 위치 교체하기" }).click();
  await expect(page.getByRole("alert")).toContainText("영향 범위가 변경되었습니다. 다시 확인해 주세요.");
  await expect(page.getByText("한국어 초안 2곳 · 영어 초안 1곳")).toBeVisible();
  expect(replacementPostCount).toBe(1);
});

test("shows bulk draft media replacement impact at 390px", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await openBulkReplacementImpact(page);
  const button = page.getByRole("button", { name: "초안 위치 교체하기" });
  const box = await button.boundingBox();
  expect(box).not.toBeNull();
  expect(box!.x).toBeGreaterThanOrEqual(0);
  expect(box!.x + box!.width).toBeLessThanOrEqual(390);
});
```

`openBulkReplacementImpact(page)`는 같은 test 파일의 fixture helper로 작성한다. 원본 자산 선택, dialog 열기, 새 파일 입력, 업로드 완료, impact 응답 표시까지 실제 UI를 조작한다. 각 test는 mock DOM을 확인하지 않는다. 취소·Escape 뒤 trigger focus, 늦은 upload 응답 후 page mutation 0건도 별도 assertion으로 검증한다.

각 test의 route fixture는 `let replacementPostCount = 0`을 선언하고 replacement POST handler에서만 증가시킨다. helper는 이 counter를 변경하지 않는다.

- [ ] **Step 8: 관리자 직접 검사**

```powershell
pnpm exec playwright test e2e/website-content-editor.spec.ts --grep "bulk draft media replacement|bulk replaces active draft media usages"
pnpm exec tsc --noEmit
```

Expected: 새 일괄 교체 4건 통과, TypeScript exit 0.

- [ ] **Step 9: 커밋**

```powershell
git add -- SDTPL_ADM/src/lib/staff-api.ts SDTPL_ADM/src/components/hotel-admin/media-draft-usage-replacement-dialog.tsx SDTPL_ADM/src/components/hotel-admin/media-picker-dialog.tsx SDTPL_ADM/e2e/website-content-editor.spec.ts
git commit -m "feat: confirm bulk draft media replacement"
```

### Task 4: 문서와 최종 직접 검증

**Files:**
- Modify: `docs/architecture/cms-functional-specification.md`
- Modify: `docs/overview/current-development-context.md`
- Create: `docs/changes/2026-09-13-media-draft-usage-bulk-replacement.md`

**Interfaces:**
- Consumes: Task 1~3 실제 API, UI, test 결과
- Produces: 한국어 구현·검증·미검증 기록

- [ ] **Step 1: 한국어 문서 갱신**

변경 기록에 다음을 실제 결과로 작성한다.

```markdown
## 구현
- 활성 한국어·영어 초안 usage만 영향 조회 후 원자적으로 교체한다.
- 공개본·발행 usage·과거 snapshot·보관 페이지·기존 파일을 유지한다.
- 영어 검토·승인 상태는 기존 초안 저장 규칙대로 무효화한다.

## 검증
- 서버 대상 통합 명령의 tests run·failures·errors·skipped 수를 기록한다.
- 관리자 Chromium 직접 E2E 명령의 통과 수를 기록한다.
- 관리자 TypeScript 명령의 exit code를 기록한다.

## 미검증과 다음 작업
- 실제 사용자 CMS mutation은 보내지 않았다.
- 전체 suite와 고객 예약 회귀는 실행하지 않았다.
- variant·변환·CDN·객체 저장소는 후속 범위다.
```

검증 절에는 실행 출력의 정확한 수치만 쓴다. `cms-functional-specification.md`의 “기존 자산의 사용 위치를 일괄 이동하지 않는다”를 새 draft-only 계약으로 교체한다. 현재 개발 상태의 오래된 “파일 교체 미구현” 문구도 최신 구현과 충돌하지 않게 정리한다.

- [ ] **Step 2: 서버 직접 테스트 재실행**

```powershell
& 'C:\Users\jowoo\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd' '-Dtest=WebsiteMediaIntegrationTest' test
```

Expected: failures 0, errors 0.

- [ ] **Step 3: 관리자 직접 검사 재실행**

```powershell
pnpm exec playwright test e2e/website-content-editor.spec.ts --grep "bulk draft media replacement|bulk replaces active draft media usages"
pnpm exec tsc --noEmit
```

Expected: 대상 E2E failures 0, TypeScript exit 0.

- [ ] **Step 4: 변경 범위·공백 검사**

```powershell
git diff --check
git status --short
```

Expected: 공백 오류 0. 기존 사용자 변경과 이번 파일을 구분해 보고한다.

- [ ] **Step 5: 문서 커밋**

```powershell
git add -- docs/architecture/cms-functional-specification.md docs/overview/current-development-context.md docs/changes/2026-09-13-media-draft-usage-bulk-replacement.md
git commit -m "docs: record bulk draft media replacement"
```

## 최종 중단 조건

- 영향 조회, 원자적 교체, 관리자 확인 UI, 영어 승인 무효화, 보존 회귀가 모두 직접 검증되면 중단한다.
- 전체 suite, 전체 브라우저 회귀, 고객 예약·결제 회귀, 실제 사용자 CMS mutation으로 범위를 넓히지 않는다.
- unrelated failure가 나오면 원인과 미검증 항목을 기록하고 해당 범위 밖 코드는 수정하지 않는다.
