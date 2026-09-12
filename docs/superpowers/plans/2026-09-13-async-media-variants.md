# 비동기 CMS 미디어 variant 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** CMS 업로드 원본을 유지하면서 640px·1280px WebP variant를 PostgreSQL 기반 백그라운드 작업으로 생성하고 관리자에게 상태와 재시도를 제공한다.

**Architecture:** `website_media_variant`가 작업 큐와 결과 메타데이터를 함께 소유한다. Spring scheduler는 `FOR UPDATE SKIP LOCKED`로 한 작업을 선점하고, WebP 파일을 임시 경로에 생성·검증한 뒤 원자 이동하여 READY로 확정한다. 기존 자산 응답에는 variant 목록만 additive하게 붙이며 고객 문서와 기존 원본 URL은 변경하지 않는다.

**Tech Stack:** Java 21, Spring Boot 4.1.1, PostgreSQL 16, Flyway V25, Java ImageIO, `com.github.usefulness:webp-imageio:0.11.0`, React 19, Next.js 16.2.7, TypeScript, Playwright

**Spec:** `docs/superpowers/specs/2026-09-13-async-media-variants-design.md`

## Global Constraints

- 생성 폭은 `640`, `1280`, 형식은 `WEBP`, lossy quality는 `0.82`로 고정한다.
- 원본보다 큰 variant는 생성하지 않는다.
- 자동 재시도는 최초 실패 후 30초, 두 번째 실패 후 2분이며 총 시도는 3회다.
- processing lease는 5분, scheduler 기본 간격은 2초다.
- 기존 `WebsiteMediaAsset.deliveryUrl`, 페이지 JSON, 공개 원본 endpoint는 변경하지 않는다.
- 공개 renderer의 `<picture>`/`srcset`, CDN, 객체 저장소, 외부 큐, AVIF, crop은 구현하지 않는다.
- 기존 자산·usage·페이지 데이터와 원본 파일을 보존하며 destructive contract 단계는 실행하지 않는다.
- 서버 권한은 본사 역할에서 확인하고 공개 variant는 ACTIVE·UPLOADED·READY·실제 파일 조건을 모두 통과해야 한다.
- 사용자 소유 `.tmp/` 파일은 수정·stage·삭제하지 않는다.

---

### Task 1: V25 큐 스키마와 관리자 자산 응답

**Files:**
- Create: `services/api/src/main/resources/db/migration/V25__website_media_variants.sql`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaVariant.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaVariantService.java`
- Create: `services/api/src/test/java/team/hotelchain/webcontent/WebsiteMediaVariantIntegrationTest.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaAsset.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaService.java`

**Interfaces:**
- Consumes: `website_media_asset(id, origin, storage_key, width, status)`와 기존 `WebsiteMediaService.upload/restore/catalog`.
- Produces: `WebsiteMediaVariant`, `WebsiteMediaVariantService.enqueueEligible(UUID,int)`, `WebsiteMediaVariantService.findByAssetIds(List<UUID>)`, `WebsiteMediaAsset.variants()`.

- [ ] **Step 1: 신규 업로드와 작은 원본의 큐 생성 실패 테스트 작성**

`WebsiteMediaVariantIntegrationTest`에 실제 PostgreSQL과 기존 `MemoryMultipartFile` 패턴을 사용해 다음 테스트를 추가한다.

```java
@Test
void queuesOnlyWebpWidthsThatDoNotUpscaleTheUploadedOriginal() throws IOException {
    String token = headquartersToken();
    WebsiteMediaAsset large = media.upload(token, imageFile(1600, 900), "큰 이미지", "큰 이미지 설명");
    WebsiteMediaAsset small = media.upload(token, imageFile(500, 281), "작은 이미지", "작은 이미지 설명");

    assertThat(large.variants()).extracting(WebsiteMediaVariant::targetWidth, WebsiteMediaVariant::status)
            .containsExactly(tuple(640, "PENDING"), tuple(1280, "PENDING"));
    assertThat(small.variants()).isEmpty();
    assertThat(jdbc.queryForObject("select count(*) from website_media_variant where asset_id = ?", Integer.class, large.id()))
            .isEqualTo(2);
}
```

- [ ] **Step 2: 대상 테스트가 스키마/타입 부재로 실패하는지 확인**

Run:

```powershell
cd services/api
.\mvnw.cmd -Dtest=WebsiteMediaVariantIntegrationTest#queuesOnlyWebpWidthsThatDoNotUpscaleTheUploadedOriginal test
```

Expected: `WebsiteMediaVariant` 또는 `website_media_variant`가 없어 FAIL.

- [ ] **Step 3: V25 expand migration 작성**

`V25__website_media_variants.sql`에 아래 구조와 제약을 구현하고 활성 업로드 자산을 `VALUES (640), (1280)`과 조인해 `asset.width >= target_width`인 row만 backfill한다.

```sql
CREATE TABLE website_media_variant (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL REFERENCES website_media_asset(id) ON DELETE CASCADE,
    format VARCHAR(10) NOT NULL CHECK (format = 'WEBP'),
    target_width INTEGER NOT NULL CHECK (target_width IN (640, 1280)),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED')),
    storage_key VARCHAR(255),
    mime_type VARCHAR(64),
    byte_size BIGINT,
    width INTEGER,
    height INTEGER,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count BETWEEN 0 AND 3),
    next_attempt_at TIMESTAMPTZ,
    lease_expires_at TIMESTAMPTZ,
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (asset_id, format, target_width),
    CHECK ((status = 'READY') = (storage_key IS NOT NULL AND mime_type = 'image/webp'
        AND byte_size > 0 AND width = target_width AND height > 0))
);

CREATE INDEX website_media_variant_work_idx
    ON website_media_variant (status, next_attempt_at, lease_expires_at, created_at)
    WHERE status IN ('PENDING', 'PROCESSING', 'FAILED');
```

- [ ] **Step 4: variant record와 큐 조회·멱등 enqueue 구현**

```java
public record WebsiteMediaVariant(
        UUID id, String format, int targetWidth, String status, String deliveryUrl,
        String mimeType, Long byteSize, Integer width, Integer height,
        int attemptCount, String lastError, OffsetDateTime updatedAt) {
}

@Transactional
public void enqueueEligible(UUID assetId, int sourceWidth) {
    for (int targetWidth : List.of(640, 1280)) {
        if (targetWidth <= sourceWidth) {
            jdbc.update("""
                insert into website_media_variant (id, asset_id, format, target_width, status)
                values (?, ?, 'WEBP', ?, 'PENDING')
                on conflict (asset_id, format, target_width) do nothing
                """, UUID.randomUUID(), assetId, targetWidth);
        }
    }
}
```

`findByAssetIds`는 한 번의 `WHERE asset_id IN (...) ORDER BY target_width` 조회로 `Map<UUID,List<WebsiteMediaVariant>>`를 반환해 catalog N+1을 만들지 않는다. READY일 때만 `/api/website/media/{assetId}/variants/{targetWidth}.webp`를 `deliveryUrl`로 만든다.

- [ ] **Step 5: 업로드·복원·catalog에 variant를 연결**

`WebsiteMediaService`에 `WebsiteMediaVariantService variants`를 주입한다. 업로드 insert 직후와 ARCHIVED→ACTIVE 복원 직후 같은 transaction에서 `enqueueEligible`을 호출한다. `catalog`와 단건 응답은 먼저 asset row들을 읽고 한 번의 variant 조회 결과를 각 `WebsiteMediaAsset`의 마지막 필드에 넣는다.

```java
public record WebsiteMediaAsset(
        UUID id, String displayName, String deliveryUrl, String mimeType, long byteSize,
        int width, int height, String defaultAltText, int usageCount, String status,
        int version, OffsetDateTime archivedAt, OffsetDateTime permanentDeleteAvailableAt,
        List<WebsiteMediaVariant> variants) {
}
```

- [ ] **Step 6: migration 보존·멱등성과 대상 테스트 통과 확인**

테스트에 기존 자산·usage·페이지 checksum을 저장한 뒤 V25 row 생성 이후에도 같음을 확인하고, 동일 asset에 `enqueueEligible`을 두 번 호출해 row 수가 2인지 확인한다.

Run:

```powershell
cd services/api
.\mvnw.cmd -Dtest=WebsiteMediaVariantIntegrationTest,WebsiteMediaIntegrationTest test
```

Expected: 두 테스트 클래스 PASS, 기존 원본 전달 테스트도 PASS.

- [ ] **Step 7: Task 1 커밋**

```powershell
git add services/api/src/main/resources/db/migration/V25__website_media_variants.sql services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaVariant.java services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaVariantService.java services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaAsset.java services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaService.java services/api/src/test/java/team/hotelchain/webcontent/WebsiteMediaVariantIntegrationTest.java
git commit -m "feat(cms): queue responsive media variants"
```

---

### Task 2: lease 기반 WebP worker와 재시도

**Files:**
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaVariantEncoder.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaVariantJob.java`
- Create: `services/api/src/test/java/team/hotelchain/webcontent/WebsiteMediaVariantEncoderTest.java`
- Modify: `services/api/pom.xml`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaVariantService.java`
- Modify: `services/api/src/test/java/team/hotelchain/webcontent/WebsiteMediaVariantIntegrationTest.java`
- Modify if Alpine native loading fails: `services/api/Dockerfile`

**Interfaces:**
- Consumes: Task 1의 PENDING rows와 `website.media.storage-dir` 원본 파일.
- Produces: `WebsiteMediaVariantEncoder.encode(Path,Path,int)`, `WebsiteMediaVariantService.claimNext()`, `completeReady(UUID,int,String,Result)`, `completeFailed(UUID,int,String)`, `WebsiteMediaVariantJob.processNext()`.

- [ ] **Step 1: encoder RED 테스트 작성**

```java
@Test
void writesAReadableWebpAtTheRequestedWidthWithoutChangingTheOriginal() throws IOException {
    Path source = writePng(tempDir.resolve("source.png"), 1600, 900);
    byte[] checksumBefore = sha256(source);
    Path target = tempDir.resolve("result.webp.tmp");

    WebsiteMediaVariantEncoder.Result result = encoder.encode(source, target, 640);

    assertThat(result).extracting(Result::mimeType, Result::width, Result::height)
            .containsExactly("image/webp", 640, 360);
    assertThat(Files.size(target)).isEqualTo(result.byteSize());
    assertThat(ImageIO.read(target.toFile())).isNotNull();
    assertThat(sha256(source)).isEqualTo(checksumBefore);
}
```

- [ ] **Step 2: encoder 테스트가 WebP writer 부재로 실패하는지 확인**

Run:

```powershell
cd services/api
.\mvnw.cmd -Dtest=WebsiteMediaVariantEncoderTest test
```

Expected: WebP writer/encoder가 없어 FAIL.

- [ ] **Step 3: WebP dependency와 최소 encoder 구현**

`pom.xml`에 아래 runtime을 고정하되 production code에서 `WebPWriteParam`을 사용하므로 compile scope로 둔다.

```xml
<dependency>
    <groupId>com.github.usefulness</groupId>
    <artifactId>webp-imageio</artifactId>
    <version>0.11.0</version>
</dependency>
```

`WebsiteMediaVariantEncoder`는 `Graphics2D`의 bicubic interpolation로 비율을 유지해 축소하고, WebP writer의 `CompressionType.Lossy`와 quality `0.82f`를 지정한다. writer가 없거나 encode 뒤 decode 크기가 다르면 `IOException`을 던진다.

```java
public Result encode(Path source, Path temporaryTarget, int targetWidth) throws IOException;
public record Result(String mimeType, long byteSize, int width, int height) {}
```

- [ ] **Step 4: claim·완료·실패 상태 RED 테스트 작성**

`WebsiteMediaVariantIntegrationTest`에 worker를 직접 호출하는 테스트를 추가한다.

```java
@Test
void claimsOnceAndCompletesAQueuedVariantAsReady() throws Exception {
    WebsiteMediaAsset asset = uploadImage(1600, 900);
    assertThat(variants.claimNext()).isPresent();
    assertThat(variants.claimNext()).isPresent(); // 두 번째 폭
    assertThat(variants.claimNext()).isEmpty();

    resetBothRowsToPending(asset.id());
    assertThat(job.processNext()).isTrue();
    WebsiteMediaAsset refreshed = media.catalogAssetForManagement(asset.id());
    assertThat(refreshed.variants()).filteredOn(v -> v.targetWidth() == 640)
            .extracting(WebsiteMediaVariant::status).containsExactly("READY");
}
```

별도 테스트에서 원본 파일을 제거해 30초·2분 backoff와 3회 상한을 확인하고, PROCESSING의 `lease_expires_at`을 과거로 바꾼 뒤 다시 claim되는지 확인한다. 두 thread의 동시 claim 결과 variant ID가 중복되지 않는지도 확인한다.

- [ ] **Step 5: DB claim과 결과 전이 구현**

`claimNext`는 `Clock` 기준 now를 인자로 쓰지 않고 주입된 `Clock`에서 얻는다. 짧은 transaction 안에서 아래 후보를 잠그고 PROCESSING·시도 수·5분 lease를 갱신해 `VariantClaim`을 반환한다.

```sql
select variant.id, variant.asset_id, variant.target_width, asset.storage_key
  from website_media_variant variant
  join website_media_asset asset on asset.id = variant.asset_id
 where asset.status = 'ACTIVE' and asset.origin = 'UPLOADED'
   and (
       variant.status = 'PENDING'
       or (variant.status = 'FAILED' and variant.attempt_count < 3 and variant.next_attempt_at <= ?)
       or (variant.status = 'PROCESSING' and variant.lease_expires_at <= ?)
   )
 order by variant.created_at, variant.target_width
 for update of variant skip locked
 limit 1
```

`completeReady`는 claim과 현재 PROCESSING 상태가 일치할 때만 결과를 기록한다. `completeFailed`는 attempt 1이면 +30초, attempt 2면 +2분, attempt 3이면 `next_attempt_at = null`로 저장하고 `last_error`는 허용된 한국어 요약 500자 이하로 제한한다.

- [ ] **Step 6: 파일 생성과 scheduler 연결**

`WebsiteMediaVariantJob.processNext()`는 source와 final path가 정규화된 media root 아래인지 확인하고 `{assetId}-{targetWidth}.webp.{uuid}.tmp`에 encode한다. 검증 성공 후 `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`를 우선 사용하고 파일시스템이 atomic move를 지원하지 않으면 같은 디렉터리의 `REPLACE_EXISTING`으로 이동한다. 실패 시 temp를 제거하고 `completeFailed`를 호출한다.

```java
@Component
@ConditionalOnProperty(name = "website.media.variant-job-enabled", havingValue = "true", matchIfMissing = true)
public final class WebsiteMediaVariantJob {
    @Scheduled(fixedDelayString = "${website.media.variant-scan-delay:2s}")
    public void generateNextVariant() { processNext(); }
    boolean processNext();
}
```

- [ ] **Step 7: worker 전체 대상 테스트 통과 확인**

Run:

```powershell
cd services/api
.\mvnw.cmd -Dtest=WebsiteMediaVariantEncoderTest,WebsiteMediaVariantIntegrationTest test
```

Expected: WebP decode, claim 경쟁, lease 복구, 재시도 상한 테스트 모두 PASS.

- [ ] **Step 8: Windows와 Alpine codec smoke 검증**

Run:

```powershell
cd services/api
.\mvnw.cmd -Dtest=WebsiteMediaVariantEncoderTest test
cd ../..
docker build --target build -t hotel-api-webp-test services/api
docker run --rm hotel-api-webp-test ./mvnw -Dtest=WebsiteMediaVariantEncoderTest test
docker compose build api
```

Expected: Windows와 Alpine build stage에서 같은 encoder 테스트가 PASS하고 runtime image build가 성공한다. native loader가 Alpine에서 실패하면 Task 3으로 진행하지 않는다. 실패한 플랫폼·예외와 `Dockerfile`의 현재 base를 기록하고, Debian 계열 base의 정확한 digest를 이 계획에 반영한 뒤 구현을 재개한다. PNG/JPEG fallback은 추가하지 않는다.

- [ ] **Step 9: Task 2 커밋**

```powershell
git add services/api/pom.xml services/api/Dockerfile services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaVariantEncoder.java services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaVariantJob.java services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaVariantService.java services/api/src/test/java/team/hotelchain/webcontent/WebsiteMediaVariantEncoderTest.java services/api/src/test/java/team/hotelchain/webcontent/WebsiteMediaVariantIntegrationTest.java
git commit -m "feat(cms): generate WebP variants in background"
```

---

### Task 3: 공개 전달, 수동 재시도와 자산 수명주기

**Files:**
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/PublicWebsiteMediaController.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaManagementController.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaVariantService.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaService.java`
- Modify: `services/api/src/test/java/team/hotelchain/webcontent/WebsiteMediaVariantIntegrationTest.java`
- Modify: `services/api/src/test/java/team/hotelchain/webcontent/WebsiteMediaIntegrationTest.java`

**Interfaces:**
- Consumes: Task 2의 READY/FAILED row와 variant storage key.
- Produces: `GET /api/website/media/{mediaId}/variants/{targetWidth}.webp`, `POST /api/staff/website/media/{mediaId}/variants/{targetWidth}/retry`, variant-aware permanent delete.

- [ ] **Step 1: endpoint와 권한 RED 테스트 작성**

```java
@Test
void deliversOnlyReadyVariantsOfActiveUploadedAssets() throws Exception {
    WebsiteMediaAsset asset = uploadAndProcess640();
    ResponseEntity<byte[]> response = publicMedia.variant(asset.id(), 640);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("image/webp"));
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("public, max-age=31536000, immutable");

    media.archive(headquartersToken(), asset.id(), new WebsiteMediaVersionRequest(asset.version()));
    assertThatThrownBy(() -> publicMedia.variant(asset.id(), 640))
            .isInstanceOf(WebsiteMediaNotFoundException.class);
}
```

FAILED가 아닌 수동 재시도, 640/1280 외 폭, 보관/번들 자산, 지점 직원 요청이 각각 409/400/409/403이 되는 서비스·MockMvc 테스트도 추가한다.

- [ ] **Step 2: endpoint 테스트가 메서드 부재로 실패하는지 확인**

Run:

```powershell
cd services/api
.\mvnw.cmd -Dtest=WebsiteMediaVariantIntegrationTest test
```

Expected: `variant`/`retryVariant` 메서드가 없어 FAIL.

- [ ] **Step 3: READY 전달과 FAILED 재시도 구현**

```java
@GetMapping("/{mediaId}/variants/{targetWidth}.webp")
public ResponseEntity<byte[]> variant(@PathVariable UUID mediaId, @PathVariable int targetWidth) {
    WebsiteMediaContent content = variants.publicContent(mediaId, targetWidth);
    return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("image/webp"))
            .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
            .header("X-Content-Type-Options", "nosniff")
            .body(content.bytes());
}

@PostMapping("/{mediaId}/variants/{targetWidth}/retry")
public WebsiteMediaAsset retryVariant(@PathVariable UUID mediaId, @PathVariable int targetWidth,
        @RequestHeader("X-Staff-Session") String token) {
    variants.retry(token, mediaId, targetWidth);
    return media.catalogAssetForManagement(mediaId);
}
```

`retry`는 `access.requireHeadquarters(token)` 이후 asset과 variant를 `FOR UPDATE`로 읽고 FAILED만 PENDING으로 초기화한다. 잘못된 폭은 `IllegalArgumentException`, 나머지 상태 충돌은 `WEBSITE_MEDIA_VARIANT_RETRY_CONFLICT`를 사용한다.

- [ ] **Step 4: 영구 삭제 commit/rollback RED 테스트 작성**

30일 지난 보관 자산의 원본과 READY variant를 준비한다. 정상 삭제 뒤 둘 다 없고 DB row도 cascade 삭제되는지 확인한다. DB version conflict를 강제로 일으킨 rollback 테스트에서는 원본과 variant가 원래 위치로 복구되는지 확인한다.

```java
assertThat(Files.exists(originalPath)).isFalse();
assertThat(Files.exists(variantPath)).isFalse();
assertThat(jdbc.queryForObject("select count(*) from website_media_variant where asset_id = ?", Integer.class, asset.id()))
        .isZero();
```

- [ ] **Step 5: 원본과 variant의 일괄 격리·복구 구현**

`WebsiteMediaService.permanentlyDelete`가 자산 잠금 뒤 `variants.storageKeys(mediaId)`를 함께 읽는다. 원본과 실제 존재하는 variant의 정규화 경로를 각각 고유 `.trash` 경로로 이동하고 `TransactionSynchronization.afterCompletion`에서 전부 삭제 또는 전부 복구한다. READY DB row에 파일이 없으면 기존 원본 누락과 동일하게 삭제를 중단한다.

```java
record QuarantinedFile(Path source, Path quarantine) {}

private void registerFileCompletion(List<QuarantinedFile> files) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCompletion(int status) {
            for (QuarantinedFile file : files) restoreOrDelete(status, file);
        }
    });
}
```

- [ ] **Step 6: endpoint·수명주기 대상 테스트 통과 확인**

Run:

```powershell
cd services/api
.\mvnw.cmd -Dtest=WebsiteMediaVariantIntegrationTest,WebsiteMediaIntegrationTest test
```

Expected: 실패·오류 0, 기존 보관/복원/삭제 테스트 포함 PASS.

- [ ] **Step 7: Task 3 커밋**

```powershell
git add services/api/src/main/java/team/hotelchain/webcontent/PublicWebsiteMediaController.java services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaManagementController.java services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaVariantService.java services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaService.java services/api/src/test/java/team/hotelchain/webcontent/WebsiteMediaVariantIntegrationTest.java services/api/src/test/java/team/hotelchain/webcontent/WebsiteMediaIntegrationTest.java
git commit -m "feat(cms): expose and recover media variants"
```

---

### Task 4: 관리자 상태·polling·수동 재시도 UI

**Files:**
- Modify: `SDTPL_ADM/src/lib/staff-api.ts`
- Modify: `SDTPL_ADM/src/components/hotel-admin/media-picker-dialog.tsx`
- Modify: `SDTPL_ADM/e2e/website-content-editor.spec.ts`

**Interfaces:**
- Consumes: `WebsiteMediaAsset.variants`와 Task 3의 retry endpoint.
- Produces: `retryWebsiteMediaVariant`, 선택 자산의 접근 가능한 반응형 이미지 상태 영역, active 작업에 한정된 2초 polling.

- [ ] **Step 1: 네 상태와 재시도·polling RED Playwright 테스트 작성**

`mediaAsset` fixture 기본값에 `variants: []`를 추가하고 전용 테스트에서는 첫 catalog 응답을 PENDING, 다음 응답을 READY와 FAILED로 반환한다.

```ts
test("미디어 variant 상태를 갱신하고 실패 항목을 다시 시도한다", async ({ page }) => {
  await mockWebsite(page, { mediaVariants: "pending-then-terminal" });
  await page.goto("/dashboard/website");
  await page.getByRole("button", { name: "홈", exact: true }).click();
  await page.getByRole("button", { name: "미디어 선택" }).click();

  const picker = page.getByRole("dialog", { name: "미디어 선택" });
  await expect(picker.getByText("640px · 변환 대기 중")).toBeVisible();
  await expect(picker.getByText("640 × 360 · WebP")).toBeVisible({ timeout: 5_000 });
  await expect(picker.getByText("1280px · 변환 실패 · 3/3회")).toBeVisible();
  await picker.getByRole("button", { name: "1280px 다시 시도" }).click();
  expect(retryRequests).toEqual([`${UPLOADED_ASSET}:1280`]);
});
```

dialog를 닫은 뒤 2.5초 동안 catalog 요청 수가 늘지 않는 assertion을 넣어 polling 정리를 확인한다.

- [ ] **Step 2: Playwright가 variant UI 부재로 실패하는지 확인**

Run:

```powershell
cd SDTPL_ADM
pnpm exec playwright test e2e/website-content-editor.spec.ts --grep "미디어 variant 상태"
```

Expected: `반응형 이미지`/상태 문구가 없어 FAIL.

- [ ] **Step 3: API 타입과 retry 함수 추가**

```ts
export type WebsiteMediaVariant = {
  id: string;
  format: "WEBP";
  targetWidth: 640 | 1280;
  status: "PENDING" | "PROCESSING" | "READY" | "FAILED";
  deliveryUrl: string | null;
  mimeType: "image/webp" | null;
  byteSize: number | null;
  width: number | null;
  height: number | null;
  attemptCount: number;
  lastError: string | null;
  updatedAt: string;
};

export function retryWebsiteMediaVariant(token: string, mediaId: string, targetWidth: 640 | 1280) {
  return mediaRequest<WebsiteMediaAsset>(`/api/staff/website/media/${mediaId}/variants/${targetWidth}/retry`, token, { method: "POST" });
}
```

`WebsiteMediaAsset`에 `variants: WebsiteMediaVariant[]`를 필수로 추가하고 모든 test fixture를 갱신한다.

- [ ] **Step 4: 상태 영역과 제한된 polling 구현**

선택 자산 정보 아래 `<section aria-label="반응형 이미지">`를 추가한다. READY 링크는 `target="_blank" rel="noreferrer"`를 사용한다. FAILED 버튼은 해당 폭 하나만 disable하고 성공 응답으로 `replaceAsset`을 호출한다.

```tsx
useEffect(() => {
  if (!open || !selectedAsset?.variants.some((item) =>
      item.status === "PENDING" || item.status === "PROCESSING")) return;
  const timer = window.setTimeout(() => {
    void refreshCatalog(selectedAsset.id, false);
  }, 2_000);
  return () => window.clearTimeout(timer);
}, [open, refreshCatalog, selectedAsset]);
```

`selectedAsset` 객체 전체 대신 `selectedAsset.id`와 active 여부를 primitive로 계산해 effect dependency가 불필요하게 흔들리지 않게 한다. polling refresh에서는 기존 메타데이터 입력을 덮어쓰지 않는다.

- [ ] **Step 5: 대상 E2E와 TypeScript 통과 확인**

Run:

```powershell
cd SDTPL_ADM
pnpm exec playwright test e2e/website-content-editor.spec.ts --grep "미디어 variant 상태"
pnpm exec tsc --noEmit
```

Expected: 대상 Chromium PASS, TypeScript 오류 0.

- [ ] **Step 6: 기존 미디어 E2E 회귀 확인**

Run:

```powershell
cd SDTPL_ADM
pnpm exec playwright test e2e/website-content-editor.spec.ts --grep "미디어|이미지 파일 교체|초안 사용 위치 일괄 교체"
```

Expected: 기존 업로드·선택·보관·복원·교체 관련 테스트 PASS.

- [ ] **Step 7: Task 4 커밋**

```powershell
git add SDTPL_ADM/src/lib/staff-api.ts SDTPL_ADM/src/components/hotel-admin/media-picker-dialog.tsx SDTPL_ADM/e2e/website-content-editor.spec.ts
git commit -m "feat(cms): show media variant processing"
```

---

### Task 5: 문서와 통합 검증

**Files:**
- Create: `docs/changes/2026-09-13-async-media-variants.md`
- Modify: `docs/architecture/cms-functional-specification.md`
- Modify: `docs/overview/current-development-context.md`

**Interfaces:**
- Consumes: Tasks 1~4의 실제 migration/API/UI/검증 결과.
- Produces: 운영·롤백·미검증 범위를 포함한 한국어 완료 기록.

- [ ] **Step 1: 변경 기록과 현재 상태 문서 작성**

변경 기록에는 다음 내용을 실제 결과 값으로 기록한다.

```markdown
## 구현 결과
- V25 스키마와 backfill 범위
- worker 선점·lease·재시도 결과
- 공개 variant와 관리자 retry 계약
- 기존 원본 URL·페이지 문서 보존

## 검증
- 실행한 서버 테스트 수와 failures/errors/skipped
- 관리자 E2E와 TypeScript 결과
- 고객 parser/build 결과
- Docker Flyway/health/WebP HTTP 확인

## 미검증과 다음 작업
- 실제 운영 다중 인스턴스 장기 부하
- 고객 `<picture>`/`srcset` 전환
- CDN·객체 저장소
```

- [ ] **Step 2: 서버 변경 범위 전체 검증**

Run:

```powershell
cd services/api
.\mvnw.cmd -Dtest=WebsiteMediaVariantEncoderTest,WebsiteMediaVariantIntegrationTest,WebsiteMediaIntegrationTest,WebContentIntegrationTest,WebsitePageIntegrationTest,WebsiteTranslationIntegrationTest test
```

Expected: failures 0, errors 0. 전체 backend suite는 변경 범위가 요구하지 않으므로 실행하지 않는다.

- [ ] **Step 3: 관리자와 고객 회귀 검증**

Run:

```powershell
cd SDTPL_ADM
pnpm exec playwright test e2e/website-content-editor.spec.ts --grep "미디어|이미지 파일 교체|초안 사용 위치 일괄 교체"
pnpm exec tsc --noEmit
cd ../apps/web
node --experimental-strip-types src/lib/content-page.test.ts
node --experimental-strip-types src/lib/content-collection.test.ts
pnpm build
```

Expected: 관리자 대상 E2E PASS, TypeScript 오류 0, 고객 parser assertions와 production build PASS.

- [ ] **Step 4: Docker와 실제 HTTP 검증**

개발 자산을 변경하지 않도록 테스트 전용 업로드와 DB row는 검증 직후 정리 가능한 별도 test DB/profile을 사용한다.

```powershell
docker compose build api
docker compose up -d api
Invoke-RestMethod http://127.0.0.1:4080/actuator/health
```

Expected: Flyway V25 적용, health `UP`. 테스트 자산 처리 후 variant endpoint가 HTTP 200, `Content-Type: image/webp`, `Cache-Control: public, max-age=31536000, immutable`, RIFF/WEBP signature를 반환하고 원본 endpoint checksum이 이전과 동일해야 한다.

- [ ] **Step 5: diff와 비밀값·임시 파일 점검**

Run:

```powershell
git diff --check
git status --short
rg -n "BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|password\s*=|api[_-]?key\s*=" services/api SDTPL_ADM docs -g '!**/target/**' -g '!**/node_modules/**'
```

Expected: diff 오류와 새 비밀값 없음. `.tmp/`는 untracked 상태로 보존.

- [ ] **Step 6: 완료 기록 커밋**

```powershell
git add docs/changes/2026-09-13-async-media-variants.md docs/architecture/cms-functional-specification.md docs/overview/current-development-context.md
git commit -m "docs: record async media variants"
```

- [ ] **Step 7: 최종 상태 확인**

```powershell
git status --short --branch
git log -5 --oneline
```

Expected: 기능 파일은 모두 커밋됨. 사용자 소유 `.tmp/`만 untracked로 남음.
