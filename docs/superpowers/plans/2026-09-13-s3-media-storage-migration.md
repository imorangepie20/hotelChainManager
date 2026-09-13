# S3 호환 미디어 저장소 이관 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 로컬 미디어와 공개 URL을 유지하면서 신규 파일 이중 기록, 기존 파일 S3 backfill, S3 우선 읽기·로컬 fallback과 양쪽 저장소 영구 삭제 보상을 구현한다.

**Architecture:** provider별 `WebsiteMediaObjectStore`와 모드별 `WebsiteMediaStorageGateway`를 분리한다. `local → mirror → s3-primary` 순서로 전환하며 모든 지원 모드에서 로컬 사본을 유지하고, 기존 DB storage key와 API URL은 변경하지 않는다.

**Tech Stack:** Java 21, Spring Boot 4.1.1, PostgreSQL 16.15, AWS SDK for Java 2.x `2.54.17`, Adobe S3Mock `5.1.0`, React/Next.js, TypeScript, Playwright

**Spec:** `docs/superpowers/specs/2026-09-13-s3-media-storage-migration-design.md`

## Global Constraints

- 기본 모드는 `local`이며 S3 설정 없이 현재 동작과 테스트가 그대로 통과해야 한다.
- 지원 모드는 `local`, `mirror`, `s3-primary`뿐이다. `s3-only`와 로컬 파일 삭제는 구현하지 않는다.
- 같은 provider 중립 storage key를 로컬과 S3에서 사용하고 기존 DB 행·페이지 JSON·공개 URL을 변경하지 않는다.
- `mirror`와 `s3-primary`의 신규 원본·READY variant는 양쪽 저장이 모두 성공해야 DB를 확정한다.
- `s3-primary`만 S3 우선 읽기와 로컬 fallback을 사용한다. `mirror`는 혼합 버전 안전을 위해 로컬을 읽는다.
- bucket은 비공개이며 endpoint, 자격 증명, 절대 로컬 경로와 객체 byte를 API·로그에 노출하지 않는다.
- backfill은 DB 참조 key만 한 번에 최대 100개 복사하고 기존 S3 객체를 덮어쓰거나 로컬 파일을 삭제하지 않는다.
- 자동 scheduler, 자동 삭제·복구, 특정 CDN 계정·DNS·purge 연동은 범위 밖이다.
- `.tmp/`, 비밀값, DB dump와 cache를 읽거나 수정하거나 커밋하지 않는다.
- 변경 경로의 직접 테스트와 실제 PostgreSQL·S3Mock 검증을 우선하며 전체 suite는 범위가 요구할 때만 실행한다.

## File Structure

- `services/api/src/main/java/team/hotelchain/webcontent/storage/WebsiteMediaStorageKey.java`: 상대 key 검증과 `.trash` key 생성.
- `services/api/src/main/java/team/hotelchain/webcontent/storage/WebsiteMediaDigest.java`: byte·file SHA-256 계산과 hex 정규화.
- `services/api/src/main/java/team/hotelchain/webcontent/storage/WebsiteMediaObjectStore.java`: 단일 물리 저장소 계약.
- `services/api/src/main/java/team/hotelchain/webcontent/storage/WebsiteMediaObjectMetadata.java`: key, size, modifiedAt, SHA-256 metadata.
- `services/api/src/main/java/team/hotelchain/webcontent/storage/WebsiteMediaQuarantinedObject.java`: provider별 원본·격리 key.
- `services/api/src/main/java/team/hotelchain/webcontent/storage/WebsiteMediaStorageException.java`: provider 원문을 외부로 노출하지 않는 저장소 오류.
- `services/api/src/main/java/team/hotelchain/webcontent/storage/WebsiteMediaObjectNotFoundException.java`: 물리 객체 없음과 provider 장애를 구분하는 오류.
- `services/api/src/main/java/team/hotelchain/webcontent/storage/LocalWebsiteMediaObjectStore.java`: 현재 로컬 `Files` 동작의 소유자.
- `services/api/src/main/java/team/hotelchain/webcontent/storage/S3WebsiteMediaObjectStore.java`: AWS SDK 기반 private bucket 구현.
- `services/api/src/main/java/team/hotelchain/webcontent/storage/WebsiteMediaStorageMode.java`: `LOCAL`, `MIRROR`, `S3_PRIMARY` parsing.
- `services/api/src/main/java/team/hotelchain/webcontent/storage/WebsiteMediaStorageProperties.java`: local·S3 설정 검증.
- `services/api/src/main/java/team/hotelchain/webcontent/storage/WebsiteMediaStorageConfiguration.java`: physical store와 gateway bean 구성.
- `services/api/src/main/java/team/hotelchain/webcontent/storage/WebsiteMediaStorageGateway.java`: 모드별 쓰기·읽기·fallback·격리 보상.
- `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaStorageMigrationService.java`: DB 참조 집계와 100개 backfill.
- `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaStorageMigrationStatus.java`: 저장소별 migration count 응답.
- `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaStorageBackfillResult.java`: batch 복사 결과.
- `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaStoreAudit.java`: provider별 audit 응답.
- `SDTPL_ADM/src/components/hotel-admin/media-storage-status.tsx`: 점검·migration 상태와 backfill UI.

---

### Task 1: 로컬 저장소 경계로 업로드·읽기·audit 이동

**Files:**
- Create: `services/api/src/main/java/team/hotelchain/webcontent/storage/WebsiteMediaStorageKey.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/storage/WebsiteMediaDigest.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/storage/WebsiteMediaObjectStore.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/storage/WebsiteMediaObjectMetadata.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/storage/WebsiteMediaQuarantinedObject.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/storage/WebsiteMediaStorageException.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/storage/WebsiteMediaObjectNotFoundException.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/storage/LocalWebsiteMediaObjectStore.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/storage/WebsiteMediaStorageGateway.java`
- Test: `services/api/src/test/java/team/hotelchain/webcontent/storage/LocalWebsiteMediaObjectStoreTest.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaService.java:42-49,85-127,210-224,435-440`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaStorageAuditService.java:30-125`

**Interfaces:**
- Produces: `WebsiteMediaObjectStore`, `LocalWebsiteMediaObjectStore`, local-only `WebsiteMediaStorageGateway`.
- Consumes: current `website.media.storage-dir` and `WebsiteMediaContent` behavior.

- [ ] **Step 1: Write the failing local store contract test**

```java
@TempDir Path root;

@Test void writesReadsListsAndDeletesOnlyRelativeKeys() {
    var store = new LocalWebsiteMediaObjectStore(root);
    byte[] bytes = "image".getBytes(StandardCharsets.UTF_8);
    store.put("asset.png", bytes, "image/png", WebsiteMediaDigest.sha256(bytes));

    assertThat(store.get("asset.png")).containsExactly(bytes);
    assertThat(store.head("asset.png")).get().satisfies(metadata -> {
        assertThat(metadata.key()).isEqualTo("asset.png");
        assertThat(metadata.byteSize()).isEqualTo(bytes.length);
    });
    assertThat(store.list()).extracting(WebsiteMediaObjectMetadata::key).containsExactly("asset.png");
    store.deleteIfExists("asset.png");
    assertThat(store.head("asset.png")).isEmpty();
}

@ParameterizedTest
@ValueSource(strings = {"", "../secret", "/absolute", ".trash/hidden"})
void rejectsUnsafePublicKeys(String key) {
    assertThatThrownBy(() -> WebsiteMediaStorageKey.publicKey(key))
            .isInstanceOf(IllegalArgumentException.class);
}
```

- [ ] **Step 2: Run the test and verify the missing types fail compilation**

Run:

```powershell
cd services/api
& .\mvnw.cmd '-Dtest=LocalWebsiteMediaObjectStoreTest' test
```

Expected: test compilation fails because `LocalWebsiteMediaObjectStore` and the storage contract do not exist.

- [ ] **Step 3: Implement the physical store contract and local adapter**

Use these exact public signatures:

```java
public interface WebsiteMediaObjectStore {
    String name();
    void put(String key, byte[] bytes, String contentType, String sha256);
    byte[] get(String key);
    Optional<WebsiteMediaObjectMetadata> head(String key);
    List<WebsiteMediaObjectMetadata> list();
    WebsiteMediaQuarantinedObject quarantine(String key, UUID transactionId);
    void restore(WebsiteMediaQuarantinedObject object);
    void purge(WebsiteMediaQuarantinedObject object);
    void deleteIfExists(String key);
}

public record WebsiteMediaObjectMetadata(
        String key, long byteSize, Instant lastModified, String sha256) {}

public record WebsiteMediaQuarantinedObject(
        String storeName, String sourceKey, String quarantineKey) {}
```

`WebsiteMediaStorageKey.publicKey`는 slash로 정규화하고 absolute, drive-letter absolute, 빈 값, `..`, `.trash` prefix를 거부한다. `quarantineKey(transactionId, sourceKey)`만 `.trash/{transactionId}/{sourceKey}`를 만들 수 있게 한다. `LocalWebsiteMediaObjectStore`는 root 밖 경로를 거부하고 symlink를 일반 파일로 따라가지 않는다.

`get`은 key가 없으면 `WebsiteMediaObjectNotFoundException`을 던지고 다른 I/O 실패는 `WebsiteMediaStorageException`으로 감싼다. `WebsiteMediaService.publicContent`는 전자만 현재의 `WebsiteMediaNotFoundException(mediaId)`로 변환해 기존 HTTP 404를 유지한다.

`WebsiteMediaStorageGateway`는 이 단계에서 local store 하나를 감싸며 다음 signature를 제공한다.

```java
public void put(String key, byte[] bytes, String contentType);
public byte[] get(String key);
public Optional<WebsiteMediaObjectMetadata> head(String storeName, String key);
public List<WebsiteMediaObjectMetadata> list(String storeName);
public Set<String> storeNames();
public void deleteEverywhere(String key);
```

로컬 전용 생성자는 `public WebsiteMediaStorageGateway(WebsiteMediaObjectStore local)`로 고정하고 `WebsiteMediaDigest.sha256(byte[])`는 lowercase hex 문자열을 반환한다. Task 4에서 mode와 metric 의존성을 받는 생성자로 교체한다.

- [ ] **Step 4: Replace upload, original public read and audit direct file access**

`WebsiteMediaService.upload`은 검증한 byte를 `storage.put(storageKey, bytes, mimeType)`로 기록하고 DB rollback 시 `storage.deleteEverywhere(storageKey)`를 호출한다. `publicContent`는 DB 상태 확인 후 `storage.get(storageKey)`를 사용한다.

`WebsiteMediaStorageAuditService`는 `storage.storeNames()`, `list(storeName)`, `head(storeName, key)`를 사용한다. 현재 local-only 응답 값과 10분 `.tmp`, `.trash` 제외 계약은 바꾸지 않는다.

- [ ] **Step 5: Run local contract and existing media regression tests**

Run:

```powershell
cd services/api
& .\mvnw.cmd '-Dtest=LocalWebsiteMediaObjectStoreTest,WebsiteMediaIntegrationTest' test
```

Expected: all tests pass; `WebsiteMediaIntegrationTest` remains 24 tests and storage audit output is unchanged.

- [ ] **Step 6: Prove direct file ownership shrank and commit**

Run:

```powershell
rg -n "storageDirectory|Files\.readAllBytes|Files\.write" src/main/java/team/hotelchain/webcontent/WebsiteMediaService.java src/main/java/team/hotelchain/webcontent/WebsiteMediaStorageAuditService.java
git diff --check
git add src/main/java/team/hotelchain/webcontent/storage src/main/java/team/hotelchain/webcontent/WebsiteMediaService.java src/main/java/team/hotelchain/webcontent/WebsiteMediaStorageAuditService.java src/test/java/team/hotelchain/webcontent/storage/LocalWebsiteMediaObjectStoreTest.java
git commit -m "refactor(media): isolate local object storage"
```

Expected: no direct `storageDirectory`, read or write remains in the two migrated services.

---

### Task 2: Variant 발행과 영구 삭제를 저장소 경계로 이동

**Files:**
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/storage/WebsiteMediaStorageGateway.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaVariantJob.java:20-85`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaVariantService.java:45-55,127-147,354-404`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaService.java:160-208,356-403`
- Test: `services/api/src/test/java/team/hotelchain/webcontent/storage/LocalWebsiteMediaObjectStoreTest.java`
- Test: `services/api/src/test/java/team/hotelchain/webcontent/WebsiteMediaVariantIntegrationTest.java`
- Test: `services/api/src/test/java/team/hotelchain/webcontent/WebsiteMediaIntegrationTest.java`

**Interfaces:**
- Consumes: Task 1 physical store and local gateway.
- Produces: provider-neutral variant materialization/publication and transaction-aware quarantine lifecycle.

- [ ] **Step 1: Add failing quarantine and variant publication tests**

```java
@Test void quarantinesRestoresAndPurgesWithoutChangingTheKey() {
    store.put("asset.webp", bytes, "image/webp", WebsiteMediaDigest.sha256(bytes));
    var quarantined = store.quarantine("asset.webp", TRANSACTION_ID);
    assertThat(store.head("asset.webp")).isEmpty();

    store.restore(quarantined);
    assertThat(store.get("asset.webp")).containsExactly(bytes);
    var second = store.quarantine("asset.webp", TRANSACTION_ID);
    store.purge(second);
    assertThat(store.head("asset.webp")).isEmpty();
}
```

Extend `WebsiteMediaVariantIntegrationTest` to assert that a READY variant is readable through the gateway and rollback deletes only the new claim key. Extend permanent-delete rollback coverage to assert original and READY variant bytes are restored.

- [ ] **Step 2: Run the focused tests and verify missing gateway operations fail**

Run:

```powershell
cd services/api
& .\mvnw.cmd '-Dtest=LocalWebsiteMediaObjectStoreTest,WebsiteMediaVariantIntegrationTest,WebsiteMediaIntegrationTest' test
```

Expected: new tests fail because materialize, publish and quarantine group methods are absent.

- [ ] **Step 3: Add exact gateway lifecycle operations**

```java
public Path materialize(String key, Path workDirectory);
public void publish(String key, byte[] bytes, String contentType);
public List<WebsiteMediaQuarantinedObject> quarantineEverywhere(List<String> keys, UUID transactionId);
public void restoreEverywhere(List<WebsiteMediaQuarantinedObject> objects);
public void purgeEverywhere(List<WebsiteMediaQuarantinedObject> objects);
public void deleteEverywhere(String key);
```

`materialize`은 UUID 임시 파일을 만들고 호출자가 finally에서 삭제한다. `quarantineEverywhere`는 중간 실패 시 이미 격리한 객체를 역순 복원한 뒤 예외를 다시 던진다. restore와 purge는 같은 호출을 반복해도 안전해야 한다.

- [ ] **Step 4: Move variant and permanent-delete flows to the gateway**

`WebsiteMediaVariantJob`은 원본을 작업 directory로 materialize하고 encoder 결과를 byte로 읽어 `WebsiteMediaVariantService.complete`에 전달한다. READY transaction synchronization은 새 key를 `storage.publish`하고 commit 시 이전 key를 `deleteEverywhere`, rollback 시 새 key를 `deleteEverywhere` 한다. `STATUS_UNKNOWN`에서는 둘 다 보존한다.

`WebsiteMediaService.permanentlyDelete`는 원본과 READY variant key 목록을 `quarantineEverywhere`로 격리한다. DB commit callback은 purge, rollback callback은 restore를 호출한다. 파일 절대 경로를 로그하지 않고 media ID, 상대 key, transaction ID만 기록한다.

- [ ] **Step 5: Run variant and deletion regression tests**

Run:

```powershell
cd services/api
& .\mvnw.cmd '-Dtest=WebsiteMediaVariantEncoderTest,WebsiteMediaVariantIntegrationTest,WebsiteMediaIntegrationTest' test
```

Expected: encoder 2 tests plus current variant and media integration tests pass with unchanged MIME, cache and rollback assertions.

- [ ] **Step 6: Verify direct filesystem calls remain only in adapters and encoder, then commit**

Run:

```powershell
rg -n "storageDirectory|Files\.(readAllBytes|write|move|deleteIfExists)" src/main/java/team/hotelchain/webcontent -g "*.java"
git diff --check
git add src/main/java/team/hotelchain/webcontent src/test/java/team/hotelchain/webcontent
git commit -m "refactor(media): route lifecycle through storage"
```

Expected: persistent media operations occur only under `webcontent/storage`; `WebsiteMediaVariantEncoder` may still use task-local paths.

---

### Task 3: S3 adapter, validated configuration and isolated S3Mock profile

**Files:**
- Modify: `services/api/pom.xml`
- Modify: `services/api/src/main/resources/application.yml`
- Modify: `compose.yaml`
- Modify: `.env.example`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/storage/WebsiteMediaStorageMode.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/storage/WebsiteMediaStorageProperties.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/storage/WebsiteMediaStorageConfiguration.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/storage/S3WebsiteMediaObjectStore.java`
- Test: `services/api/src/test/java/team/hotelchain/webcontent/storage/S3WebsiteMediaObjectStoreIntegrationTest.java`

**Interfaces:**
- Consumes: Task 1 `WebsiteMediaObjectStore` contract.
- Produces: `localStore`, optional `s3Store`, parsed `WebsiteMediaStorageMode` and startup validation.

- [ ] **Step 1: Start pinned S3Mock and write the failing S3 contract test**

Add this Compose service:

```yaml
s3mock:
  profiles: ["object-storage"]
  image: adobe/s3mock:5.1.0@sha256:65cf60155a2e235fe7d5bf6c633747d6fc7ed93f9f5a6727d86470026b83c2a2
  environment:
    COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS: hotel-media-test
  ports:
    - "127.0.0.1:${WEBSITE_MEDIA_S3MOCK_PORT:-59090}:9090"
  healthcheck:
    test: ["CMD", "wget", "--spider", "-q", "http://localhost:9090/favicon.ico"]
    interval: 2s
    timeout: 3s
    retries: 20
```

In the existing `db` and `api` services, parameterize only the host ports while keeping current defaults:

```yaml
db:
  ports:
    - "127.0.0.1:${HOTEL_DB_PORT:-55432}:5432"
api:
  environment:
    WEBSITE_MEDIA_STORAGE_MODE: ${WEBSITE_MEDIA_STORAGE_MODE:-local}
    WEBSITE_MEDIA_S3_ENDPOINT: ${WEBSITE_MEDIA_S3_ENDPOINT:-}
    WEBSITE_MEDIA_S3_REGION: ${WEBSITE_MEDIA_S3_REGION:-}
    WEBSITE_MEDIA_S3_BUCKET: ${WEBSITE_MEDIA_S3_BUCKET:-}
    WEBSITE_MEDIA_S3_PATH_STYLE: ${WEBSITE_MEDIA_S3_PATH_STYLE:-false}
  ports:
    - "127.0.0.1:${HOTEL_API_PORT:-4080}:4080"
```

The default rendered Compose ports must remain `55432`, `4080`, and `59090`; the variables exist only so Task 7 can run an isolated proof without stopping the user's development stack.

Create `S3WebsiteMediaObjectStoreIntegrationTest` guarded by `@EnabledIfSystemProperty(named = "website.media.s3.test", matches = "true")`. It must put/get/head/list, preserve SHA-256 metadata, quarantine/restore/purge, reject overwrite with a different digest and paginate more than 1000 small objects.

- [ ] **Step 2: Run the S3 test and verify missing adapter failure**

Run:

```powershell
docker compose --profile object-storage up -d s3mock
cd services/api
& .\mvnw.cmd '-Dwebsite.media.s3.test=true' '-Dtest=S3WebsiteMediaObjectStoreIntegrationTest' test
```

Expected: test compilation fails because `S3WebsiteMediaObjectStore` does not exist.

- [ ] **Step 3: Add AWS SDK 2.54.17 dependencies**

Add a managed property and dependencies:

```xml
<aws.sdk.version>2.54.17</aws.sdk.version>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>software.amazon.awssdk</groupId>
      <artifactId>bom</artifactId>
      <version>${aws.sdk.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependency>
  <groupId>software.amazon.awssdk</groupId>
  <artifactId>s3</artifactId>
</dependency>
<dependency>
  <groupId>software.amazon.awssdk</groupId>
  <artifactId>url-connection-client</artifactId>
</dependency>
```

- [ ] **Step 4: Implement validated properties and S3 client**

Use the exact configuration shape:

```yaml
website:
  media:
    storage-dir: ${WEBSITE_MEDIA_STORAGE_DIR:}
    storage:
      mode: ${WEBSITE_MEDIA_STORAGE_MODE:local}
      s3:
        endpoint: ${WEBSITE_MEDIA_S3_ENDPOINT:}
        region: ${WEBSITE_MEDIA_S3_REGION:}
        bucket: ${WEBSITE_MEDIA_S3_BUCKET:}
        path-style: ${WEBSITE_MEDIA_S3_PATH_STYLE:false}
```

`WebsiteMediaStorageProperties` is `@ConfigurationProperties("website.media.storage")`, while the existing local root remains bound from `@Value("${website.media.storage-dir:}")`. `WebsiteMediaStorageConfiguration` declares `@EnableConfigurationProperties(WebsiteMediaStorageProperties.class)` and uses `DefaultCredentialsProvider`, `Region.of`, optional `endpointOverride`, and `S3Configuration.pathStyleAccessEnabled`. In `MIRROR` or `S3_PRIMARY`, blank region/bucket or unavailable credentials fail startup with a Korean configuration message that excludes secret values. `LOCAL` creates no S3 client. The S3Mock test constructs the client with `StaticCredentialsProvider.create(AwsBasicCredentials.create("s3mock-local", "s3mock-local"))`; runtime code never embeds these dummy credentials.

`S3WebsiteMediaObjectStore` maps 404 to empty head/`WebsiteMediaObjectNotFoundException`, paginates `ListObjectsV2`, stores `sha256` as user metadata, uses private objects, and implements quarantine as copy→head/digest verify→source delete. Before a write it treats a same-digest destination as success and a different digest as an error; an absent destination is created with `PutObjectRequest.ifNoneMatch("*")` so a race cannot overwrite another object.

- [ ] **Step 5: Pass the S3 contract and local default regression**

Run:

```powershell
cd services/api
& .\mvnw.cmd '-Dwebsite.media.s3.test=true' '-Dtest=S3WebsiteMediaObjectStoreIntegrationTest' test
& .\mvnw.cmd '-Dtest=LocalWebsiteMediaObjectStoreTest,WebsiteMediaIntegrationTest,WebsiteMediaVariantIntegrationTest' test
```

Expected: S3 contract passes against `http://127.0.0.1:59090`; local tests pass without S3 environment variables.

- [ ] **Step 6: Stop the isolated emulator and commit**

Run:

```powershell
cd ..\..
docker compose --profile object-storage stop s3mock
git diff --check
git add services/api/pom.xml services/api/src/main/resources/application.yml services/api/src/main/java/team/hotelchain/webcontent/storage services/api/src/test/java/team/hotelchain/webcontent/storage compose.yaml .env.example
git commit -m "feat(media): add S3 object store adapter"
```

---

### Task 4: Mirror·S3-primary routing, compensation and fallback

**Files:**
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/storage/WebsiteMediaStorageGateway.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/storage/WebsiteMediaStorageConfiguration.java`
- Create: `services/api/src/test/java/team/hotelchain/webcontent/storage/WebsiteMediaStorageGatewayTest.java`
- Modify: `services/api/src/test/java/team/hotelchain/webcontent/WebsiteMediaIntegrationTest.java`
- Modify: `services/api/src/test/java/team/hotelchain/webcontent/WebsiteMediaVariantIntegrationTest.java`

**Interfaces:**
- Consumes: local and S3 physical stores, `WebsiteMediaStorageMode`.
- Produces: dual-write, compensation, S3-first read, local fallback and `media.storage.fallback` counter.

- [ ] **Step 1: Write deterministic fault-injection gateway tests**

Create a test-only `RecordingObjectStore` with configurable failure per operation. Cover these exact cases:

```java
@Test void mirrorCompensatesLocalWriteWhenS3WriteFails() {
    var local = new RecordingObjectStore("local");
    var s3 = new RecordingObjectStore("s3");
    s3.failNext(Operation.PUT);
    var gateway = gateway(WebsiteMediaStorageMode.MIRROR, local, s3);

    assertThatThrownBy(() -> gateway.put("asset.png", BYTES, "image/png"))
            .isInstanceOf(WebsiteMediaStorageException.class);
    assertThat(local.exists("asset.png")).isFalse();
    assertThat(s3.exists("asset.png")).isFalse();
}

@Test void mirrorReadsOnlyLocal() {
    var local = new RecordingObjectStore("local").seed("asset.png", BYTES);
    var s3 = new RecordingObjectStore("s3").seed("asset.png", OTHER_BYTES);
    assertThat(gateway(WebsiteMediaStorageMode.MIRROR, local, s3).get("asset.png"))
            .containsExactly(BYTES);
    assertThat(s3.calls(Operation.GET)).isZero();
}

@Test void s3PrimaryReadsS3First() {
    var local = new RecordingObjectStore("local").seed("asset.png", OTHER_BYTES);
    var s3 = new RecordingObjectStore("s3").seed("asset.png", BYTES);
    assertThat(gateway(WebsiteMediaStorageMode.S3_PRIMARY, local, s3).get("asset.png"))
            .containsExactly(BYTES);
    assertThat(local.calls(Operation.GET)).isZero();
}

@Test void s3PrimaryFallsBackToLocalAndIncrementsCounter() {
    var local = new RecordingObjectStore("local").seed("asset.png", BYTES);
    var s3 = new RecordingObjectStore("s3");
    s3.failNext(Operation.GET);
    var metrics = new SimpleMeterRegistry();
    var gateway = new WebsiteMediaStorageGateway(
            WebsiteMediaStorageMode.S3_PRIMARY, local, Optional.of(s3), metrics);

    assertThat(gateway.get("asset.png")).containsExactly(BYTES);
    assertThat(metrics.counter("media.storage.fallback", "operation", "read").count())
            .isEqualTo(1.0d);
}

@Test void quarantineFailureRestoresAlreadyQuarantinedStores() {
    var local = new RecordingObjectStore("local").seed("asset.png", BYTES);
    var s3 = new RecordingObjectStore("s3").seed("asset.png", BYTES);
    s3.failNext(Operation.QUARANTINE);

    assertThatThrownBy(() -> gateway(WebsiteMediaStorageMode.MIRROR, local, s3)
            .quarantineEverywhere(List.of("asset.png"), TRANSACTION_ID))
            .isInstanceOf(WebsiteMediaStorageException.class);
    assertThat(local.exists("asset.png")).isTrue();
    assertThat(s3.exists("asset.png")).isTrue();
}

@Test void unknownCleanupFailurePreservesObjectsForAudit() {
    var local = new RecordingObjectStore("local");
    var s3 = new RecordingObjectStore("s3");
    s3.failNext(Operation.PUT);
    local.failNext(Operation.DELETE);

    assertThatThrownBy(() -> gateway(WebsiteMediaStorageMode.MIRROR, local, s3)
            .put("asset.png", BYTES, "image/png"))
            .isInstanceOf(WebsiteMediaStorageException.class);
    assertThat(local.exists("asset.png")).isTrue();
    assertThat(local.calls(Operation.DELETE)).isEqualTo(1);
}
```

`RecordingObjectStore` is a nested test class implementing the full `WebsiteMediaObjectStore` contract. It exposes `seed(String, byte[])`, `failNext(Operation)`, `exists(String)` and `calls(Operation)`; `Operation` contains `PUT`, `GET`, `HEAD`, `LIST`, `QUARANTINE`, `RESTORE`, `PURGE`, `DELETE`. The `gateway(mode, local, s3)` helper creates a fresh `SimpleMeterRegistry` and passes `Optional.of(s3)`.

- [ ] **Step 2: Run gateway tests and verify they fail in local-only routing**

Run:

```powershell
cd services/api
& .\mvnw.cmd '-Dtest=WebsiteMediaStorageGatewayTest' test
```

Expected: mirror/S3-primary constructor or behavior assertions fail.

- [ ] **Step 3: Implement mode routing and metrics**

Use one gateway constructor with explicit dependencies:

```java
public WebsiteMediaStorageGateway(
        WebsiteMediaStorageMode mode,
        WebsiteMediaObjectStore local,
        Optional<WebsiteMediaObjectStore> s3,
        MeterRegistry meterRegistry) {}

public void copyIfAbsent(
        String sourceStoreName, String destinationStoreName,
        String key, String contentType);
```

`put` and `publish` write local then S3 in `mirror`, S3 then local in `s3-primary`, and compensate all newly written stores in reverse order on failure. A pre-existing same-digest object counts as success; a different digest fails without overwrite. `get` and `materialize` follow the read order from the spec. `copyIfAbsent` reads exactly the named source and writes exactly the named destination only when absent; it returns normally for a same-digest destination and throws on mismatch. Only a successful local fallback increments `media.storage.fallback` with tag `operation=read|materialize`. Increment `media.storage.write.failure` and `media.storage.compensation.failure` with `mode` and `store` tags; never use storage key, endpoint or exception text as a metric tag.

- [ ] **Step 4: Add real PostgreSQL·S3Mock mode integration cases**

Configure test instances with temporary local roots and S3Mock bucket prefixes. Verify:

```java
String originalKey = jdbc.queryForObject(
        "select storage_key from website_media_asset where id = ?",
        String.class, uploaded.id());
List<String> readyKeys = jdbc.queryForList(
        "select storage_key from website_media_variant where media_id = ? and status = 'READY' order by target_width",
        String.class, uploaded.id());

assertThat(localStore.get(originalKey)).containsExactly(s3Store.get(originalKey));
assertThat(readyKeys).hasSize(2).allSatisfy(key ->
        assertThat(localStore.head(key).orElseThrow().sha256())
                .isEqualTo(s3Store.head(key).orElseThrow().sha256()));

s3Store.deleteIfExists(originalKey);
byte[] localBytes = localStore.get(originalKey);
assertThat(media.publicContent(uploaded.id()).bytes()).containsExactly(localBytes);
assertThat(meterRegistry.counter("media.storage.fallback", "operation", "read").count())
        .isEqualTo(1.0d);
```

Also force the second store write to fail and add `doesNotCommitUploadWhenSecondStoreWriteFails`, `doesNotCommitReadyVariantWhenSecondStoreWriteFails`, `restoresBothStoresWhenPermanentDeleteTransactionRollsBack`, and `purgesBothStoresWhenPermanentDeleteCommits`. Each test queries the corresponding DB row and both stores after transaction completion.

- [ ] **Step 5: Run gateway, media and variant tests with S3Mock**

Run:

```powershell
cd ..\..
docker compose --profile object-storage up -d s3mock db-test
cd services/api
& .\mvnw.cmd '-Dwebsite.media.s3.test=true' '-Dtest=WebsiteMediaStorageGatewayTest,WebsiteMediaIntegrationTest,WebsiteMediaVariantIntegrationTest' test
```

Expected: all mode, compensation, fallback and existing lifecycle cases pass.

- [ ] **Step 6: Commit the routing slice**

```powershell
git diff --check
git add src/main/java/team/hotelchain/webcontent/storage src/test/java/team/hotelchain/webcontent/storage src/test/java/team/hotelchain/webcontent/WebsiteMediaIntegrationTest.java src/test/java/team/hotelchain/webcontent/WebsiteMediaVariantIntegrationTest.java
git commit -m "feat(media): mirror object storage writes"
```

---

### Task 5: 저장소별 audit와 idempotent backfill API

**Files:**
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaStoreAudit.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaStorageMigrationStatus.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaStorageBackfillResult.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaStorageMigrationService.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaStorageAudit.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaStorageAuditService.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaManagementController.java`
- Modify: `services/api/src/test/java/team/hotelchain/webcontent/WebsiteMediaIntegrationTest.java`

**Interfaces:**
- Consumes: store-specific `head`/`list` and `copyIfAbsent(sourceStoreName, destinationStoreName, key, contentType)` from the gateway.
- Produces: additive audit fields, migration status GET, fixed-size backfill POST.

- [ ] **Step 1: Add failing API integration tests**

Add MockMvc cases for:

```java
mockMvc.perform(get("/api/staff/website/media/storage-audit")
        .header("X-Staff-Session", hqToken))
    .andExpect(jsonPath("$.mode").value("mirror"))
    .andExpect(jsonPath("$.stores[0].storeName").value("local"))
    .andExpect(jsonPath("$.stores[1].storeName").value("s3"));

mockMvc.perform(get("/api/staff/website/media/storage-migration")
        .header("X-Staff-Session", hqToken))
    .andExpect(jsonPath("$.localOnly").value(2))
    .andExpect(jsonPath("$.mismatch").value(1));

mockMvc.perform(post("/api/staff/website/media/storage-migration/backfill")
        .header("X-Staff-Session", hqToken))
    .andExpect(jsonPath("$.copied").value(2))
    .andExpect(jsonPath("$.failed").value(0));
```

Seed 101 DB-referenced local-only keys and assert one call examines/copies exactly 100. Rerun and assert examined/copied 1, then rerun and assert examined/copied 0 while status reports `both=101`. Seed an S3 object with a different digest and assert mismatch 1 with unchanged bytes. In `local`, status reports every present DB key as `localOnly` and POST returns 409; `s3-primary` POST also returns 409. Assert branch staff receives 403 for both endpoints.

- [ ] **Step 2: Run focused API tests and verify 404/missing fields**

Run:

```powershell
cd services/api
& .\mvnw.cmd '-Dwebsite.media.s3.test=true' '-Dtest=WebsiteMediaIntegrationTest#reportsPerStoreAuditAndMigrationCounts+backfillsAtMostOneHundredReferencedObjectsIdempotently+doesNotOverwriteMismatchedS3Objects+restrictsStorageMigrationToHeadquartersAdministrators' test
```

Expected: tests fail because the migration routes and additive audit records do not exist.

- [ ] **Step 3: Implement records and read-only status**

Use these exact response shapes:

```java
public record WebsiteMediaStoreAudit(
        String storeName, boolean healthy,
        List<String> missingStorageKeys,
        List<String> orphanStorageKeys,
        List<String> staleTemporaryStorageKeys) {}

public record WebsiteMediaStorageMigrationStatus(
        String mode, int total, int both, int localOnly, int s3Only,
        int mismatch, int missing, long fallbackCount) {}

public record WebsiteMediaStorageBackfillResult(
        int examined, int copied, int skipped, int mismatch, int failed,
        List<String> failedStorageKeys) {}
```

Add `mode` and `stores` to `WebsiteMediaStorageAudit` after the existing fields so current JSON consumers retain their keys. Top-level legacy lists represent the preferred read store.

- [ ] **Step 4: Implement status and fixed 100-key backfill**

`WebsiteMediaStorageMigrationService.status(token)` calls `requireHeadquarters`, queries the sorted union of uploaded original and READY variant keys, and compares both stores by byte length and SHA-256. Without an S3 store, each locally present key is `localOnly`. `backfill(token)` requires `MIRROR` and returns 409 in `LOCAL` or `S3_PRIMARY`; it examines the first 100 non-`both` keys: copy only `localOnly`, skip `both`, report `s3Only`/`mismatch`/`missing` without writing, and never overwrite. A private `contentTypeForKey(String key)` returns `image/png`, `image/jpeg`, or `image/webp` for `.png`, `.jpg`/`.jpeg`, or `.webp`; any other referenced suffix is counted as failed and is not copied.

Expose exact routes:

```java
@GetMapping("/storage-migration")
public WebsiteMediaStorageMigrationStatus storageMigration(
        @RequestHeader("X-Staff-Session") String token) {
    return storageMigration.status(token);
}

@PostMapping("/storage-migration/backfill")
public WebsiteMediaStorageBackfillResult backfillStorage(
        @RequestHeader("X-Staff-Session") String token) {
    return storageMigration.backfill(token);
}
```

Return only relative keys in `failedStorageKeys`; log provider failures without credentials or endpoints. Increment `media.storage.backfill` once per examined key with `outcome=copied|skipped|mismatch|failed`; do not tag keys.

- [ ] **Step 5: Run full media server slice**

Run:

```powershell
& .\mvnw.cmd '-Dwebsite.media.s3.test=true' '-Dtest=LocalWebsiteMediaObjectStoreTest,S3WebsiteMediaObjectStoreIntegrationTest,WebsiteMediaStorageGatewayTest,WebsiteMediaIntegrationTest,WebsiteMediaVariantIntegrationTest,WebContentIntegrationTest,WebsitePageIntegrationTest,WebsiteTranslationIntegrationTest' test
```

Expected: storage contracts, media lifecycle and page/public URL regressions all pass.

- [ ] **Step 6: Commit the operational API slice**

```powershell
git diff --check
git add src/main/java/team/hotelchain/webcontent src/test/java/team/hotelchain/webcontent
git commit -m "feat(cms): backfill S3 media storage"
```

---

### Task 6: CMS 저장소 상태와 수동 backfill UI

**Files:**
- Create: `SDTPL_ADM/src/components/hotel-admin/media-storage-status.tsx`
- Modify: `SDTPL_ADM/src/components/hotel-admin/media-picker-dialog.tsx:20-32,143-187,247-263,390-410,503-520,537-562`
- Modify: `SDTPL_ADM/src/lib/staff-api.ts:289-302,404-411`
- Modify: `SDTPL_ADM/e2e/website-content-editor.spec.ts:2099-2152`

**Interfaces:**
- Consumes: Task 5 audit/status/backfill JSON.
- Produces: accessible local/mirror/S3-primary status panel and explicit 100-object copy action.

- [ ] **Step 1: Write failing Playwright tests for status and backfill**

Mock the three routes and verify:

```ts
await expect(audit.getByText("mirror · 양쪽 일치 8개", { exact: true })).toBeVisible();
await expect(audit.getByText("S3에 없는 파일 2개", { exact: true })).toBeVisible();
await audit.getByRole("button", { name: "다음 100개 복사", exact: true }).click();
expect(backfillMethod).toBe("POST");
await expect(audit.getByRole("status")).toContainText("2개를 S3에 복사했습니다.");
```

Add cases for button hidden in `local`, disabled during request, mismatch warning, partial failure alert, late response ignored after close, and 390×844 layout.

- [ ] **Step 2: Run the focused tests and verify missing UI failure**

Run:

```powershell
cd SDTPL_ADM
pnpm exec playwright test e2e/website-content-editor.spec.ts --grep "storage migration|S3 backfill"
```

Expected: tests fail because the migration summary and button are absent.

- [ ] **Step 3: Add typed API clients**

```ts
export type WebsiteMediaStorageMigrationStatus = {
  mode: "local" | "mirror" | "s3-primary";
  total: number; both: number; localOnly: number; s3Only: number;
  mismatch: number; missing: number; fallbackCount: number;
};

export type WebsiteMediaStorageBackfillResult = {
  examined: number; copied: number; skipped: number;
  mismatch: number; failed: number; failedStorageKeys: string[];
};

export function getWebsiteMediaStorageMigration(token: string) {
  return mediaRequest<WebsiteMediaStorageMigrationStatus>(
    "/api/staff/website/media/storage-migration", token);
}

export function backfillWebsiteMediaStorage(token: string) {
  return mediaRequest<WebsiteMediaStorageBackfillResult>(
    "/api/staff/website/media/storage-migration/backfill", token, { method: "POST" });
}
```

Extend `WebsiteMediaStorageAudit` with `mode` and `stores`, keeping defaults for older API responses: missing `mode` becomes `local`, missing `stores` becomes one local view from the legacy fields.

- [ ] **Step 4: Extract and implement the status component**

`MediaStorageStatus` owns audit/status/backfill request generations. It renders one section with mode, store cards, counts and relative keys. The backfill confirmation text must say `기존 로컬 파일은 삭제하지 않고 S3에 추가 사본을 만듭니다.` The button is present only in `mirror`, refreshes audit/status after success, and never loops automatically.

Use `role="status"` for completion, `role="alert"` for mismatch/failed requests, `aria-busy` while loading, `break-all` for keys and existing Button styles. Keep a one-column mobile layout and avoid nested dialogs.

- [ ] **Step 5: Run CMS verification and UI detector once after final edits**

Run:

```powershell
pnpm exec playwright test e2e/website-content-editor.spec.ts --grep "media storage audit|healthy media storage|storage migration|S3 backfill"
pnpm exec tsc --noEmit
pnpm exec eslint src/components/hotel-admin/media-storage-status.tsx src/components/hotel-admin/media-picker-dialog.tsx src/lib/staff-api.ts e2e/website-content-editor.spec.ts
& 'C:\Users\jowoo\.agents\skills\impeccable\scripts\impeccable.cmd' detect --json src/components/hotel-admin/media-storage-status.tsx src/components/hotel-admin/media-picker-dialog.tsx src/lib/staff-api.ts
```

Expected: Playwright and TypeScript pass, ESLint has zero errors, detector returns `[]`. Record existing unrelated warnings without expanding scope.

- [ ] **Step 6: Commit the CMS operational UI**

```powershell
git diff --check
git add src/components/hotel-admin/media-storage-status.tsx src/components/hotel-admin/media-picker-dialog.tsx src/lib/staff-api.ts e2e/website-content-editor.spec.ts
git commit -m "feat(cms): manage media storage migration"
```

---

### Task 7: Compose end-to-end cutover proof and documentation

**Files:**
- Modify: `docs/architecture/cms-functional-specification.md`
- Modify: `docs/overview/current-development-context.md`
- Modify: `docs/changes/2026-09-13-media-storage-audit.md`
- Create: `docs/changes/2026-09-13-s3-media-storage-migration.md`
- Modify: `docs/superpowers/specs/2026-09-13-s3-media-storage-migration-design.md`
- Modify: `docs/superpowers/plans/2026-09-13-s3-media-storage-migration.md`

**Interfaces:**
- Consumes: all prior tasks.
- Produces: actual local→mirror→s3-primary evidence, rollback proof and operator-facing record.

- [ ] **Step 1: Start isolated PostgreSQL, S3Mock and API in local mode**

Use a separate Compose project and volumes so development DB/media are not mutated:

```powershell
$env:COMPOSE_PROJECT_NAME='hotel-media-s3-proof'
$env:HOTEL_DB_NAME='hotel_media_s3_proof'
$env:HOTEL_DB_PORT='56432'
$env:HOTEL_API_PORT='4180'
$env:WEBSITE_MEDIA_S3MOCK_PORT='59190'
$env:WEBSITE_MEDIA_STORAGE_MODE='local'
$proofPassword=[guid]::NewGuid().ToString('N')
$env:STAFF_DEV_ENABLED='true'
$env:STAFF_HQ_PASSWORD=$proofPassword
docker compose --profile object-storage up -d --build db api s3mock
```

Use `http://127.0.0.1:4180` for the proof API. Create a test-only HQ session and upload a repository fixture without printing the generated password:

```powershell
$proofBase='http://127.0.0.1:4180'
$loginBody=@{email='hq@hotel-chain.local';password=$proofPassword} | ConvertTo-Json -Compress
$session=Invoke-RestMethod -Method Post -Uri "$proofBase/api/staff/sessions" -ContentType 'application/json' -Body $loginBody
$proofHeaders=@{'X-Staff-Session'=$session.token}
$fixture=Get-Item -LiteralPath (Resolve-Path 'apps/web/public/images/sokcho-coast-hero.png')
$asset=Invoke-RestMethod -Method Post -Uri "$proofBase/api/staff/website/media" -Headers $proofHeaders -Form @{file=$fixture;displayName='S3 proof';defaultAltText='S3 proof image'}
```

Poll the catalog until the asset has READY 640 and 1280 variants, with a hard 60-second deadline. Record original and variant SHA-256 from HTTP and local volume without printing credentials; fail the proof if either variant is not READY by the deadline.

- [ ] **Step 2: Switch to mirror, backfill and verify both stores**

```powershell
$env:WEBSITE_MEDIA_STORAGE_MODE='mirror'
$env:WEBSITE_MEDIA_S3_ENDPOINT='http://s3mock:9090'
$env:WEBSITE_MEDIA_S3_REGION='us-east-1'
$env:WEBSITE_MEDIA_S3_BUCKET='hotel-media-test'
$env:WEBSITE_MEDIA_S3_PATH_STYLE='true'
$env:AWS_ACCESS_KEY_ID='s3mock-local'
$env:AWS_SECRET_ACCESS_KEY='s3mock-local'
docker compose --profile object-storage up -d --build api
```

Call status, then backfill until `localOnly=0`. Upload a second image in mirror and verify both original and variants have matching SHA-256. Do not delete either copy.

- [ ] **Step 3: Switch to S3-primary and force one observable fallback**

Set `WEBSITE_MEDIA_STORAGE_MODE=s3-primary`, rebuild API once, fetch both images from the unchanged public URLs, then temporarily make one S3 test object unavailable while preserving local. Fetch it once and assert HTTP 200, matching SHA-256 and fallback count increment by exactly one. Restore the S3 object from local with the explicit backfill flow.

- [ ] **Step 4: Prove delete commit and rollback in both stores**

Run the two integration cases added in Task 4 against the isolated PostgreSQL/S3Mock configuration:

```powershell
cd services/api
& .\mvnw.cmd '-Dwebsite.media.s3.test=true' '-Dtest=WebsiteMediaIntegrationTest#restoresBothStoresWhenPermanentDeleteTransactionRollsBack+purgesBothStoresWhenPermanentDeleteCommits' test
```

`restoresBothStoresWhenPermanentDeleteTransactionRollsBack` creates and ages a test-only archived asset through its test fixture, injects a DB failure after quarantine, then asserts the original and READY variant source keys exist and their trash keys do not exist in both stores. `purgesBothStoresWhenPermanentDeleteCommits` performs the successful path and asserts source and trash keys are absent from both stores after commit. Do not issue these DELETE requests against the development project or user assets.

- [ ] **Step 5: Roll back configuration to local and verify URLs again**

```powershell
$env:WEBSITE_MEDIA_STORAGE_MODE='local'
docker compose --profile object-storage up -d --build api
```

Assert API health `UP`, both retained assets return the original checksums from the unchanged URLs, and no S3 configuration is required for startup.

- [ ] **Step 6: Run final focused verification**

```powershell
cd services/api
& .\mvnw.cmd '-Dwebsite.media.s3.test=true' '-Dtest=LocalWebsiteMediaObjectStoreTest,S3WebsiteMediaObjectStoreIntegrationTest,WebsiteMediaStorageGatewayTest,WebsiteMediaVariantEncoderTest,WebsiteMediaVariantIntegrationTest,WebsiteMediaIntegrationTest,WebContentIntegrationTest,WebsitePageIntegrationTest,WebsiteTranslationIntegrationTest' test
cd ..\..\SDTPL_ADM
pnpm exec playwright test e2e/website-content-editor.spec.ts --grep "media storage|S3 backfill|media variant|uploads an asset|permanently deletes"
pnpm exec tsc --noEmit
cd ..
git diff --check
```

Read every command's exit code and test count before stating completion.

- [ ] **Step 7: Update Korean documentation with exact evidence**

Record mode semantics, environment names, backfill batch size, mixed-version drain order, fallback behavior, delete compensation, exact test counts, actual checksums, verified Compose project and unverified production CDN/provider items. State that local and S3 test data were isolated and user data was not mutated.

- [ ] **Step 8: Remove only the isolated proof environment and commit**

Resolve and verify `COMPOSE_PROJECT_NAME` equals `hotel-media-s3-proof`, then run:

```powershell
docker compose --profile object-storage down --volumes
git add docs/architecture/cms-functional-specification.md docs/overview/current-development-context.md docs/changes/2026-09-13-media-storage-audit.md docs/changes/2026-09-13-s3-media-storage-migration.md docs/superpowers/specs/2026-09-13-s3-media-storage-migration-design.md docs/superpowers/plans/2026-09-13-s3-media-storage-migration.md
git commit -m "docs: record S3 media migration"
git status --short --branch
```

Expected: only the user-owned `.tmp/` remains untracked. Do not push until the user requests it.

---

## 실행 결과

2026-09-13에 Tasks 1~6 구현과 Task 7의 격리 Compose 전환·롤백·최종 집중 검증을 완료했다. 구현 commit과 정확한 검증 증거는 [S3 미디어 저장소 이관 기록](../../changes/2026-09-13-s3-media-storage-migration.md)에 남겼다.

- 서버 집중 suite: 119건 통과, 실패·오류·skip 0
- 관리자 집중 Playwright: 8건 통과, TypeScript 통과, ESLint 오류 0, UI 탐지 `[]`
- 격리 Compose: `local → mirror → s3-primary → mirror 복구 → local 롤백` 완료, `both=6`, 강제 fallback counter `+1`
- 영구 삭제의 양쪽 저장소 rollback restore·commit purge는 gateway 회귀 테스트로, 실제 S3 격리 동작은 S3Mock adapter 계약 테스트로 나눠 확인했다. 계획에 명시한 동일 이름의 PostgreSQL+S3Mock 통합 테스트 2건은 추가하지 않았다.
- 실제 backfill proof는 참조 객체 6개의 한 batch였다. 101개를 두 batch로 나누는 실제 API 시나리오는 미자동화이며, 최대 100개 제한과 mismatch 비덮어쓰기는 서비스/API 회귀에서 확인했다.
- 운영 provider·CDN·다중 인스턴스 부하는 미검증이다. 사용자 `.tmp/`, 개발 DB와 media volume은 수정하지 않았다.
