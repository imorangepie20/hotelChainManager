package team.hotelchain.webcontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.multipart.MultipartFile;
import team.hotelchain.staff.StaffAccessDeniedException;
import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.webcontent.storage.WebsiteMediaStorageGateway;

@SpringBootTest(properties = "website.media.variant-job-enabled=false")
@Transactional
@Import(WebsiteMediaVariantIntegrationTest.ClockConfiguration.class)
class WebsiteMediaVariantIntegrationTest {
    private static final Instant START = Instant.parse("2026-09-13T00:00:00Z");
    private static final UUID BRANCH_HOTEL = UUID.fromString("13000000-0000-0000-0000-000000000091");

    @org.springframework.beans.factory.annotation.Autowired JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired DataSource dataSource;
    @org.springframework.beans.factory.annotation.Autowired StaffAccessService staffAccess;
    @org.springframework.beans.factory.annotation.Autowired WebsiteMediaService media;
    @org.springframework.beans.factory.annotation.Autowired WebsiteMediaVariantService variants;
    @org.springframework.beans.factory.annotation.Autowired WebsiteMediaVariantEncoder encoder;
    @org.springframework.beans.factory.annotation.Autowired WebsiteMediaStorageGateway storageGateway;
    @org.springframework.beans.factory.annotation.Autowired WebApplicationContext context;
    @org.springframework.beans.factory.annotation.Autowired TestClock clock;
    @org.springframework.beans.factory.annotation.Autowired TransactionTemplate transactions;
    WebsiteMediaVariantJob job;

    @BeforeEach
    void seed() throws IOException {
        removeCommittedWorkerFixtures();
        deleteStorage();
        clock.reset();
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        jdbc.update("insert into hotel values (?, ?, ?, ?)",
                BRANCH_HOTEL, "Variant 테스트 지점", "속초", "Asia/Seoul");
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role) values (?, ?, ?, ?, 'HQ_ADMIN')",
                UUID.randomUUID(), "variant-hq@example.com", "Variant 본사 관리자",
                passwordEncoder.encode("hq-password"));
        jdbc.update("""
                insert into staff_member (id, email, display_name, password_hash, role, hotel_id)
                values (?, ?, ?, ?, 'BRANCH_STAFF', ?)
                """, UUID.randomUUID(), "variant-branch@example.com", "Variant 지점 직원",
                passwordEncoder.encode("branch-password"), BRANCH_HOTEL);
        job = new WebsiteMediaVariantJob(variants, encoder, storageGateway);
    }

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

    @Test
    void preservesExistingCmsRowsAndBackfillsOnlyEligibleActiveUploads() {
        String schema = "media_variant_" + UUID.randomUUID().toString().replace("-", "");
        UUID large = UUID.randomUUID();
        UUID small = UUID.randomUUID();
        UUID archived = UUID.randomUUID();
        try {
            isolatedFlyway(schema, MigrationVersion.fromVersion("24")).migrate();
            try (var connection = dataSource.getConnection()) {
                var isolatedJdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                insertUploadedAsset(isolatedJdbc, schema, large, 1600, "ACTIVE");
                insertUploadedAsset(isolatedJdbc, schema, small, 500, "ACTIVE");
                insertUploadedAsset(isolatedJdbc, schema, archived, 1600, "ARCHIVED");
                List<String> checksums = cmsChecksums(isolatedJdbc, schema);

                isolatedFlyway(schema, MigrationVersion.fromVersion("25")).migrate();

                assertThat(cmsChecksums(isolatedJdbc, schema)).isEqualTo(checksums);
                assertThat(isolatedJdbc.query("""
                        select asset_id, target_width
                          from "%s".website_media_variant
                         order by asset_id, target_width
                        """.formatted(schema), (rs, rowNumber) -> new MigrationVariantRow(
                        rs.getObject("asset_id", UUID.class), rs.getInt("target_width"))))
                        .containsExactly(new MigrationVariantRow(large, 640), new MigrationVariantRow(large, 1280));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("V25 미디어 variant migration을 검증할 수 없습니다.", exception);
        } finally {
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.execute("drop schema if exists \"" + schema + "\" cascade");
            } catch (Exception exception) {
                throw new IllegalStateException("격리된 migration test schema를 정리할 수 없습니다.", exception);
            }
        }
    }

    @Test
    void v27AddsNullableClaimTokenWithoutChangingExistingMediaRows() {
        String schema = "media_variant_claim_" + UUID.randomUUID().toString().replace("-", "");
        UUID assetId = UUID.randomUUID();
        try {
            isolatedFlyway(schema, MigrationVersion.fromVersion("24")).migrate();
            try (var connection = dataSource.getConnection()) {
                var isolatedJdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                insertUploadedAsset(isolatedJdbc, schema, assetId, 640, "ACTIVE");
                isolatedFlyway(schema, MigrationVersion.fromVersion("26")).migrate();
                OffsetDateTime leaseExpiresAt = OffsetDateTime.ofInstant(START.plusSeconds(300), ZoneOffset.UTC);
                isolatedJdbc.update("""
                        update "%s".website_media_variant
                           set status = 'PROCESSING', attempt_count = 1, lease_expires_at = ?
                         where asset_id = ? and target_width = 640
                        """.formatted(schema), leaseExpiresAt, assetId);
                List<String> checksums = cmsChecksums(isolatedJdbc, schema);
                MigrationClaimRow existingClaim = migrationClaimRow(isolatedJdbc, schema, assetId);

                isolatedFlyway(schema, MigrationVersion.fromVersion("27")).migrate();

                assertThat(cmsChecksums(isolatedJdbc, schema)).isEqualTo(checksums);
                assertThat(migrationClaimRow(isolatedJdbc, schema, assetId)).isEqualTo(existingClaim);
                assertThat(isolatedJdbc.queryForObject("""
                        select count(*)
                          from information_schema.columns
                         where table_schema = ? and table_name = 'website_media_variant'
                           and column_name = 'claim_token'
                        """, Integer.class, schema)).isOne();
                assertThat(isolatedJdbc.queryForObject("""
                        select count(*)
                          from "%s".website_media_variant
                         where claim_token is not null
                        """.formatted(schema), Integer.class)).isZero();
            }
        } catch (Exception exception) {
            throw new IllegalStateException("V27 미디어 variant claim migration을 검증할 수 없습니다.", exception);
        } finally {
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.execute("drop schema if exists \"" + schema + "\" cascade");
            } catch (Exception exception) {
                throw new IllegalStateException("격리된 migration test schema를 정리할 수 없습니다.", exception);
            }
        }
    }

    @Test
    void enqueueIsIdempotentAndRestoreRecreatesOnlyMissingEligibleRows() throws IOException {
        String token = headquartersToken();
        WebsiteMediaAsset asset = media.upload(token, imageFile(1600, 900), "복원 이미지", "복원 이미지 설명");
        jdbc.update("delete from website_media_variant where asset_id = ?", asset.id());

        variants.enqueueEligible(asset.id(), asset.width());
        variants.enqueueEligible(asset.id(), asset.width());

        assertThat(jdbc.queryForObject("select count(*) from website_media_variant where asset_id = ?", Integer.class, asset.id()))
                .isEqualTo(2);

        WebsiteMediaAsset archived = media.archive(token, asset.id(), new WebsiteMediaVersionRequest(asset.version()));
        jdbc.update("delete from website_media_variant where asset_id = ?", asset.id());
        WebsiteMediaAsset restored = media.restore(token, asset.id(), new WebsiteMediaVersionRequest(archived.version()));

        assertThat(restored.variants()).extracting(WebsiteMediaVariant::targetWidth, WebsiteMediaVariant::status)
                .containsExactly(tuple(640, "PENDING"), tuple(1280, "PENDING"));
    }

    @Test
    void catalogExposesDeliveryUrlsOnlyForReadyVariants() throws IOException {
        String token = headquartersToken();
        WebsiteMediaAsset asset = media.upload(token, imageFile(1600, 900), "카탈로그 이미지", "카탈로그 이미지 설명");
        jdbc.update("""
                update website_media_variant
                   set status = 'READY', storage_key = ?, mime_type = 'image/webp', byte_size = 1234,
                       width = 640, height = 360, updated_at = current_timestamp
                 where asset_id = ? and target_width = 640
                """, asset.id() + "-640.webp", asset.id());

        WebsiteMediaAsset catalogAsset = media.catalog(token).stream()
                .filter(candidate -> candidate.id().equals(asset.id()))
                .findFirst()
                .orElseThrow();

        assertThat(catalogAsset.variants()).hasSize(2);
        assertThat(catalogAsset.variants().getFirst().deliveryUrl())
                .isEqualTo("/api/website/media/" + asset.id() + "/variants/640.webp");
        assertThat(catalogAsset.variants().getLast().deliveryUrl()).isNull();
    }

    @Test
    void rejectsReadyVariantsWithIncompleteResultMetadata() throws IOException {
        WebsiteMediaAsset asset = media.upload(
                headquartersToken(), imageFile(640, 360), "불완전 variant 원본", "불완전 variant 설명");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("""
                update website_media_variant
                   set status = 'READY', storage_key = ?, mime_type = null,
                       byte_size = null, width = null, height = null
                 where asset_id = ? and target_width = 640
                """, asset.id() + "-640.webp", asset.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void doesNotEnqueueBundledAssetsWhenRestored() {
        String token = headquartersToken();
        WebsiteMediaAsset bundled = media.catalog(token, true).stream()
                .filter(asset -> asset.id().equals(WebsiteMediaService.BUNDLED_ASSET_ID))
                .findFirst()
                .orElseThrow();
        jdbc.update("update website_media_asset set status = 'ARCHIVED', archived_at = current_timestamp where id = ?",
                bundled.id());

        WebsiteMediaAsset restored = media.restore(
                token, bundled.id(), new WebsiteMediaVersionRequest(bundled.version()));

        assertThat(restored.variants()).isEmpty();
        assertThat(jdbc.queryForObject(
                "select count(*) from website_media_variant where asset_id = ?", Integer.class, bundled.id()))
                .isZero();
    }

    @Test
    void deliversOnlyReadyVariantsOfActiveUploadedAssets() throws Exception {
        WebsiteMediaAsset asset = uploadImage(640, 360);
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        String path = "/api/website/media/" + asset.id() + "/variants/640.webp";

        jdbc.update("""
                update website_media_variant
                   set status = 'PROCESSING', attempt_count = 1, lease_expires_at = ?, claim_token = ?
                 where asset_id = ? and target_width = 640
                """, OffsetDateTime.ofInstant(START.plusSeconds(300), ZoneOffset.UTC), UUID.randomUUID(), asset.id());
        mvc.perform(get(path)).andExpect(status().isNotFound());

        jdbc.update("""
                update website_media_variant
                   set status = 'FAILED', lease_expires_at = null, claim_token = null,
                       next_attempt_at = ?, last_error = '미디어 variant 생성에 실패했습니다.'
                 where asset_id = ? and target_width = 640
                """, OffsetDateTime.ofInstant(START.plusSeconds(30), ZoneOffset.UTC), asset.id());
        mvc.perform(get(path)).andExpect(status().isNotFound());

        variants.retry(headquartersToken(), asset.id(), 640);
        assertThat(job.processNext()).isTrue();

        var response = mvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/webp"))
                .andExpect(header().string("Cache-Control", "public, max-age=31536000, immutable"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse();
        assertThat(response.getContentAsByteArray()).isNotEmpty();

        media.archive(headquartersToken(), asset.id(), new WebsiteMediaVersionRequest(asset.version()));

        mvc.perform(get(path)).andExpect(status().isNotFound());
    }

    @Test
    void publicVariantRouteHidesBundledAssetsAndMissingReadyFiles() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        WebsiteMediaAsset missingFile = uploadImage(640, 360);
        jdbc.update("""
                update website_media_variant
                   set status = 'READY', storage_key = ?, mime_type = 'image/webp', byte_size = 10,
                       width = 640, height = 360
                 where asset_id = ? and target_width = 640
                """, missingFile.id() + "-missing.webp", missingFile.id());

        mvc.perform(get("/api/website/media/" + missingFile.id() + "/variants/640.webp"))
                .andExpect(status().isNotFound());

        String bundledStorageKey = "bundled-640-" + UUID.randomUUID() + ".webp";
        byte[] bundledBytes = "bundled-variant".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(storageDirectory().resolve(bundledStorageKey), bundledBytes);
        jdbc.update("""
                insert into website_media_variant (
                    id, asset_id, format, target_width, status, storage_key, mime_type,
                    byte_size, width, height, attempt_count
                ) values (?, ?, 'WEBP', 640, 'READY', ?, 'image/webp', ?, 640, 360, 1)
                """, UUID.randomUUID(), WebsiteMediaService.BUNDLED_ASSET_ID,
                bundledStorageKey, (long) bundledBytes.length);

        mvc.perform(get("/api/website/media/" + WebsiteMediaService.BUNDLED_ASSET_ID + "/variants/640.webp"))
                .andExpect(status().isNotFound());
    }

    @Test
    void manualRetryResetsOnlyAFailedVariantToPending() throws IOException {
        WebsiteMediaAsset asset = uploadImage(640, 360);
        markFailed(asset.id(), 640);

        variants.retry(headquartersToken(), asset.id(), 640);

        RetryRow row = retryRow(asset.id(), 640);
        assertThat(row.status()).isEqualTo("PENDING");
        assertThat(row.attemptCount()).isZero();
        assertThat(row.nextAttemptAt()).isNull();
        assertThat(row.leaseExpiresAt()).isNull();
        assertThat(row.claimToken()).isNull();
        assertThat(row.lastError()).isNull();
    }

    @Test
    void oldSuccessfulCompletionCannotPublishAfterManualRetryReusesAttemptOne() throws Exception {
        WebsiteMediaAsset asset = uploadImage(640, 360);
        WebsiteMediaVariantService.VariantClaim oldClaim = variants.claimNext().orElseThrow();
        expireLease(oldClaim.variantId());
        WebsiteMediaVariantService.VariantClaim reclaimed = variants.claimNext().orElseThrow();
        assertThat(variants.completeFailed(
                reclaimed.variantId(), reclaimed.attemptCount(), reclaimed.claimToken(),
                "미디어 variant 생성에 실패했습니다.")).isTrue();

        variants.retry(headquartersToken(), asset.id(), 640);
        WebsiteMediaVariantService.VariantClaim newClaim = variants.claimNext().orElseThrow();
        assertThat(newClaim.attemptCount()).isEqualTo(oldClaim.attemptCount());
        assertThat(newClaim.claimToken()).isNotEqualTo(oldClaim.claimToken());

        String oldStorageKey = asset.id() + "-640-old-" + UUID.randomUUID() + ".webp";
        Path temporaryTarget = storageDirectory().resolve(oldStorageKey + ".tmp");
        Path finalTarget = storageDirectory().resolve(oldStorageKey);
        WebsiteMediaVariantEncoder.Result result = encoder.encode(
                storageDirectory().resolve(asset.id() + ".png"), temporaryTarget, 640);
        try {
            assertThat(variants.completeReady(
                    oldClaim.variantId(), oldClaim.attemptCount(), oldClaim.claimToken(),
                    oldStorageKey, result, Files.readAllBytes(temporaryTarget))).isFalse();
            assertThat(temporaryTarget).exists();
            assertThat(finalTarget).doesNotExist();
            assertThat(claimState(asset.id())).isEqualTo(
                    new ClaimState("PROCESSING", 1, newClaim.claimToken()));
        } finally {
            Files.deleteIfExists(temporaryTarget);
            Files.deleteIfExists(finalTarget);
        }
    }

    @Test
    void oldFailedCompletionCannotFailNewClaimAfterManualRetryReusesAttemptOne() throws IOException {
        WebsiteMediaAsset asset = uploadImage(640, 360);
        WebsiteMediaVariantService.VariantClaim oldClaim = variants.claimNext().orElseThrow();
        expireLease(oldClaim.variantId());
        WebsiteMediaVariantService.VariantClaim reclaimed = variants.claimNext().orElseThrow();
        assertThat(variants.completeFailed(
                reclaimed.variantId(), reclaimed.attemptCount(), reclaimed.claimToken(),
                "미디어 variant 생성에 실패했습니다.")).isTrue();

        variants.retry(headquartersToken(), asset.id(), 640);
        WebsiteMediaVariantService.VariantClaim newClaim = variants.claimNext().orElseThrow();
        assertThat(newClaim.attemptCount()).isEqualTo(oldClaim.attemptCount());
        assertThat(newClaim.claimToken()).isNotEqualTo(oldClaim.claimToken());

        assertThat(variants.completeFailed(
                oldClaim.variantId(), oldClaim.attemptCount(), oldClaim.claimToken(),
                "미디어 variant 생성에 실패했습니다.")).isFalse();
        assertThat(claimState(asset.id())).isEqualTo(
                new ClaimState("PROCESSING", 1, newClaim.claimToken()));
    }

    @Test
    void rejectsManualRetryForUnsupportedStateWidthAssetOriginAndRole() throws IOException {
        String headquarters = headquartersToken();
        WebsiteMediaAsset asset = uploadImage(640, 360);

        assertThatThrownBy(() -> variants.retry(headquarters, asset.id(), 640))
                .isInstanceOf(WebsiteMediaConflictException.class)
                .extracting(error -> ((WebsiteMediaConflictException) error).code())
                .isEqualTo("WEBSITE_MEDIA_VARIANT_RETRY_CONFLICT");
        assertThatThrownBy(() -> variants.retry(headquarters, asset.id(), 800))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> variants.retry(branchToken(), asset.id(), 640))
                .isInstanceOf(StaffAccessDeniedException.class);

        WebsiteMediaAsset archived = media.archive(
                headquarters, asset.id(), new WebsiteMediaVersionRequest(asset.version()));
        assertThatThrownBy(() -> variants.retry(headquarters, archived.id(), 640))
                .isInstanceOf(WebsiteMediaConflictException.class)
                .extracting(error -> ((WebsiteMediaConflictException) error).code())
                .isEqualTo("WEBSITE_MEDIA_VARIANT_RETRY_CONFLICT");
        assertThatThrownBy(() -> variants.retry(headquarters, WebsiteMediaService.BUNDLED_ASSET_ID, 640))
                .isInstanceOf(WebsiteMediaConflictException.class)
                .extracting(error -> ((WebsiteMediaConflictException) error).code())
                .isEqualTo("WEBSITE_MEDIA_VARIANT_RETRY_CONFLICT");
    }

    @Test
    void retryEndpointMapsValidationConflictAndAuthorizationStatuses() throws Exception {
        String headquarters = headquartersToken();
        String branch = branchToken();
        WebsiteMediaAsset asset = uploadImage(640, 360);
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        String path = "/api/staff/website/media/" + asset.id() + "/variants/640/retry";

        mvc.perform(post(path).header("X-Staff-Session", headquarters))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WEBSITE_MEDIA_VARIANT_RETRY_CONFLICT"));
        mvc.perform(post("/api/staff/website/media/" + asset.id() + "/variants/800/retry")
                        .header("X-Staff-Session", headquarters))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post(path).header("X-Staff-Session", branch))
                .andExpect(status().isForbidden());

        markFailed(asset.id(), 640);
        mvc.perform(post(path).header("X-Staff-Session", headquarters))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants[0].status").value("PENDING"));

        WebsiteMediaAsset archived = media.archive(
                headquarters, asset.id(), new WebsiteMediaVersionRequest(asset.version()));
        mvc.perform(post(path).header("X-Staff-Session", headquarters))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WEBSITE_MEDIA_VARIANT_RETRY_CONFLICT"));
        mvc.perform(post("/api/staff/website/media/" + WebsiteMediaService.BUNDLED_ASSET_ID
                        + "/variants/640/retry").header("X-Staff-Session", headquarters))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WEBSITE_MEDIA_VARIANT_RETRY_CONFLICT"));
        assertThat(archived.status()).isEqualTo("ARCHIVED");
    }

    @Test
    void claimsQueuedVariantsOnceAndCompletesOneAsReady() throws Exception {
        WebsiteMediaAsset asset = uploadImage(1600, 900);

        assertThat(variants.claimNext()).get().extracting(WebsiteMediaVariantService.VariantClaim::targetWidth)
                .isEqualTo(640);
        assertThat(variants.claimNext()).get().extracting(WebsiteMediaVariantService.VariantClaim::targetWidth)
                .isEqualTo(1280);
        assertThat(variants.claimNext()).isEmpty();

        resetBothRowsToPending(asset.id());
        assertThat(job.processNext()).isTrue();

        VariantResultRow result = jdbc.queryForObject("""
                select status, storage_key, mime_type, byte_size, width, height
                  from website_media_variant
                 where asset_id = ? and target_width = 640
                """, (rs, rowNumber) -> new VariantResultRow(
                rs.getString("status"), rs.getString("storage_key"), rs.getString("mime_type"),
                rs.getLong("byte_size"), rs.getInt("width"), rs.getInt("height")), asset.id());
        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.storageKey())
                .startsWith(asset.id() + "-640-")
                .endsWith(".webp");
        assertThat(result.mimeType()).isEqualTo("image/webp");
        assertThat(result.byteSize()).isEqualTo(Files.size(storageDirectory().resolve(result.storageKey())));
        assertThat(result.width()).isEqualTo(640);
        assertThat(result.height()).isEqualTo(360);
        assertThat(ImageIO.read(storageDirectory().resolve(result.storageKey()).toFile())).isNotNull();
    }

    @Test
    void retriesFailuresAfterThirtySecondsAndTwoMinutesThenStopsAtThreeAttempts() throws Exception {
        WebsiteMediaAsset asset = uploadImage(640, 360);
        Files.delete(storageDirectory().resolve(asset.id() + ".png"));

        assertThat(job.processNext()).isTrue();
        assertFailure(asset.id(), 1, START.plusSeconds(30));

        clock.advanceSeconds(29);
        assertThat(job.processNext()).isFalse();
        clock.advanceSeconds(1);
        assertThat(job.processNext()).isTrue();
        assertFailure(asset.id(), 2, START.plusSeconds(150));

        clock.advanceSeconds(120);
        assertThat(job.processNext()).isTrue();
        FailureRow terminal = failureRow(asset.id());
        assertThat(terminal.status()).isEqualTo("FAILED");
        assertThat(terminal.attemptCount()).isEqualTo(3);
        assertThat(terminal.nextAttemptAt()).isNull();
        assertThat(terminal.lastError()).isNotBlank().hasSizeLessThanOrEqualTo(500);
        assertThat(job.processNext()).isFalse();
    }

    @Test
    void reclaimsOnlyExpiredProcessingVariantsBelowTheAttemptLimit() throws Exception {
        WebsiteMediaAsset asset = uploadImage(640, 360);
        jdbc.update("""
                update website_media_variant
                   set status = 'PROCESSING', attempt_count = 2, lease_expires_at = ?, next_attempt_at = null
                 where asset_id = ? and target_width = 640
                """, OffsetDateTime.ofInstant(START.minusSeconds(1), ZoneOffset.UTC), asset.id());

        assertThat(variants.claimNext()).get()
                .extracting(WebsiteMediaVariantService.VariantClaim::attemptCount).isEqualTo(3);

        jdbc.update("""
                update website_media_variant
                   set status = 'PROCESSING', attempt_count = 3, lease_expires_at = ?, next_attempt_at = null
                 where asset_id = ? and target_width = 640
                """, OffsetDateTime.ofInstant(START.minusSeconds(1), ZoneOffset.UTC), asset.id());
        assertThat(variants.claimNext()).isEmpty();
        FailureRow terminal = failureRow(asset.id());
        assertThat(terminal.status()).isEqualTo("FAILED");
        assertThat(terminal.attemptCount()).isEqualTo(3);
        assertThat(terminal.nextAttemptAt()).isNull();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void skipsLockedTerminalLeasesAndBoundsCleanupWithoutBlockingAnEligibleClaim() throws Exception {
        try {
            WebsiteMediaAsset locked = uploadImage(640, 360);
            WebsiteMediaAsset expiredOne = uploadImage(640, 360);
            WebsiteMediaAsset expiredTwo = uploadImage(640, 360);
            WebsiteMediaAsset eligible = uploadImage(640, 360);
            for (WebsiteMediaAsset asset : List.of(locked, expiredOne, expiredTwo)) {
                jdbc.update("""
                        update website_media_variant
                           set status = 'PROCESSING', attempt_count = 3, lease_expires_at = ?
                         where asset_id = ?
                        """, OffsetDateTime.ofInstant(START.minusSeconds(1), ZoneOffset.UTC), asset.id());
            }

            try (var lockConnection = dataSource.getConnection()) {
                lockConnection.setAutoCommit(false);
                try {
                    try (var statement = lockConnection.prepareStatement(
                            "select id from website_media_variant where asset_id = ? for update")) {
                        statement.setObject(1, locked.id());
                        try (var rows = statement.executeQuery()) {
                            assertThat(rows.next()).isTrue();
                        }
                    }

                    Optional<WebsiteMediaVariantService.VariantClaim> claimed = assertDoesNotThrow(() ->
                            transactions.execute(status -> {
                                jdbc.execute("set local lock_timeout = '1s'");
                                return variants.claimNext();
                            }), "a held terminal-row lock must not delay an unrelated eligible claim");
                    assertThat(claimed).get()
                            .extracting(WebsiteMediaVariantService.VariantClaim::assetId).isEqualTo(eligible.id());
                    assertThat(failureRow(locked.id()).status()).isEqualTo("PROCESSING");
                    assertThat(List.of(failureRow(expiredOne.id()).status(), failureRow(expiredTwo.id()).status()))
                            .containsExactlyInAnyOrder("FAILED", "PROCESSING");
                } finally {
                    lockConnection.rollback();
                }
            }

            assertThat(variants.claimNext()).isEmpty();
            assertThat(variants.claimNext()).isEmpty();
            assertThat(List.of(failureRow(locked.id()).status(), failureRow(expiredOne.id()).status(),
                    failureRow(expiredTwo.id()).status())).containsOnly("FAILED");
        } finally {
            removeCommittedWorkerFixtures();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentClaimsNeverReturnTheSameVariant() throws Exception {
        try {
            uploadImage(1600, 900);
            CountDownLatch start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                Future<Optional<WebsiteMediaVariantService.VariantClaim>> first =
                        executor.submit(() -> { start.await(); return variants.claimNext(); });
                Future<Optional<WebsiteMediaVariantService.VariantClaim>> second =
                        executor.submit(() -> { start.await(); return variants.claimNext(); });
                start.countDown();

                assertThat(first.get()).isPresent();
                assertThat(second.get()).isPresent();
                assertThat(List.of(first.get().orElseThrow().variantId(), second.get().orElseThrow().variantId()))
                        .doesNotHaveDuplicates();
            }
        } finally {
            removeCommittedWorkerFixtures();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void staleWorkerCannotOverwriteTheReadyFilePublishedByItsReclaimer() throws Exception {
        CountDownLatch staleEncoded = new CountDownLatch(1);
        CountDownLatch resumeStaleWorker = new CountDownLatch(1);
        AtomicReference<Path> staleTemporaryFile = new AtomicReference<>();
        AtomicReference<byte[]> staleChecksum = new AtomicReference<>();
        WebsiteMediaVariantEncoder blockingEncoder = mock(WebsiteMediaVariantEncoder.class);
        doAnswer(invocation -> {
            Path source = invocation.getArgument(0);
            Path temporaryTarget = invocation.getArgument(1);
            int targetWidth = invocation.getArgument(2);
            WebsiteMediaVariantEncoder.Result result = encoder.encode(source, temporaryTarget, targetWidth);
            staleTemporaryFile.set(temporaryTarget);
            staleChecksum.set(sha256(temporaryTarget));
            staleEncoded.countDown();
            if (!resumeStaleWorker.await(10, TimeUnit.SECONDS)) {
                throw new IOException("stale worker 재개 신호를 받지 못했습니다.");
            }
            return result;
        }).when(blockingEncoder).encode(any(Path.class), any(Path.class), anyInt());

        try (var executor = Executors.newSingleThreadExecutor()) {
            try {
                WebsiteMediaAsset asset = uploadImage(640, 360);
                WebsiteMediaVariantJob staleJob =
                        new WebsiteMediaVariantJob(variants, blockingEncoder, storageGateway);
                Future<Boolean> staleResult = executor.submit(staleJob::processNext);
                assertThat(staleEncoded.await(10, TimeUnit.SECONDS)).isTrue();

                jdbc.update("""
                        update website_media_variant
                           set lease_expires_at = ?
                         where asset_id = ? and target_width = 640
                        """, OffsetDateTime.ofInstant(START.minusSeconds(1), ZoneOffset.UTC), asset.id());
                overwriteWithWhitePng(storageDirectory().resolve(asset.id() + ".png"), 640, 360);

                assertThat(job.processNext()).isTrue();
                VariantResultRow reclaimerMetadata = variantResultRow(asset.id());
                Path readyFile = storageDirectory().resolve(reclaimerMetadata.storageKey());
                byte[] reclaimerChecksum = sha256(readyFile);
                assertThat(staleChecksum.get()).isNotEqualTo(reclaimerChecksum);

                resumeStaleWorker.countDown();
                assertThat(staleResult.get(10, TimeUnit.SECONDS)).isTrue();

                assertThat(sha256(readyFile)).isEqualTo(reclaimerChecksum);
                assertThat(variantResultRow(asset.id())).isEqualTo(reclaimerMetadata);
                assertThat(staleTemporaryFile.get()).doesNotExist();
            } finally {
                resumeStaleWorker.countDown();
            }
        } finally {
            removeCommittedWorkerFixtures();
            deleteStorage();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollbackCompensationCannotDeleteTheNewerClaimsPublishedFile() throws Exception {
        try {
            WebsiteMediaAsset asset = uploadImage(640, 360);
            WebsiteMediaVariantService.VariantClaim claimA = variants.claimNext().orElseThrow();
            String legacySharedKey = asset.id() + "-640.webp";
            Path oldTemporaryTarget = storageDirectory().resolve(legacySharedKey + ".old.tmp");
            Path oldFinalTarget = storageDirectory().resolve(legacySharedKey);
            Files.write(oldTemporaryTarget, "old-worker-publication".getBytes(java.nio.charset.StandardCharsets.UTF_8));

            WebsiteMediaVariantService.ReadyObjectPublication oldPublication =
                    new WebsiteMediaVariantService.ReadyObjectPublication(
                            claimA.variantId(), storageGateway, legacySharedKey, null,
                            Files.readAllBytes(oldTemporaryTarget), "image/webp");
            oldPublication.publish();

            jdbc.update("""
                    update website_media_variant
                       set lease_expires_at = ?
                     where id = ?
                    """, OffsetDateTime.ofInstant(START.minusSeconds(1), ZoneOffset.UTC), claimA.variantId());

            assertThat(job.processNext()).isTrue();
            VariantResultRow newerMetadata = variantResultRow(asset.id());
            Path newerReadyFile = storageDirectory().resolve(newerMetadata.storageKey());
            byte[] newerChecksum = sha256(newerReadyFile);

            oldPublication.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

            assertThat(newerReadyFile).exists();
            assertThat(sha256(newerReadyFile)).isEqualTo(newerChecksum);
            assertThat(variantResultRow(asset.id())).isEqualTo(newerMetadata);
        } finally {
            removeCommittedWorkerFixtures();
            deleteStorage();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void unknownTransactionOutcomePreservesThePublishedUniqueFile() throws Exception {
        try {
            Files.createDirectories(storageDirectory());
            Path temporaryTarget = storageDirectory().resolve("unknown-outcome.tmp");
            Path uniqueFinalTarget = storageDirectory().resolve("asset-640-claim-unique.webp");
            byte[] encodedBytes = "encoded-webp".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            Files.write(temporaryTarget, encodedBytes);

            WebsiteMediaVariantService.ReadyObjectPublication publication =
                    new WebsiteMediaVariantService.ReadyObjectPublication(
                            UUID.randomUUID(), storageGateway, uniqueFinalTarget.getFileName().toString(), null,
                            encodedBytes, "image/webp");
            publication.publish();
            publication.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);

            assertThat(uniqueFinalTarget).exists();
            assertThat(Files.readAllBytes(uniqueFinalTarget)).isEqualTo(encodedBytes);
        } finally {
            removeCommittedWorkerFixtures();
            deleteStorage();
        }
    }

    private String headquartersToken() {
        return staffAccess.login("variant-hq@example.com", "hq-password").token();
    }

    private String branchToken() {
        return staffAccess.login("variant-branch@example.com", "branch-password").token();
    }

    private WebsiteMediaAsset uploadImage(int width, int height) throws IOException {
        return media.upload(headquartersToken(), imageFile(width, height),
                "variant-worker-" + UUID.randomUUID(), "variant worker 테스트 이미지");
    }

    private Path storageDirectory() {
        return Path.of(System.getProperty("java.io.tmpdir"), "hotel-chain-media").toAbsolutePath().normalize();
    }

    private void removeCommittedWorkerFixtures() {
        jdbc.update("delete from website_media_asset where display_name like 'variant-worker-%'");
        jdbc.update("""
                delete from staff_session
                 where staff_id in (select id from staff_member where email in (?, ?))
                """, "variant-hq@example.com", "variant-branch@example.com");
        jdbc.update("delete from staff_member where email in (?, ?)",
                "variant-hq@example.com", "variant-branch@example.com");
        jdbc.update("delete from hotel where id = ?", BRANCH_HOTEL);
    }

    private void resetBothRowsToPending(UUID assetId) {
        jdbc.update("""
                update website_media_variant
                   set status = 'PENDING', storage_key = null, mime_type = null, byte_size = null,
                       width = null, height = null, attempt_count = 0, next_attempt_at = null,
                       lease_expires_at = null, claim_token = null, last_error = null
                 where asset_id = ?
                """, assetId);
    }

    private void markFailed(UUID assetId, int targetWidth) {
        jdbc.update("""
                update website_media_variant
                   set status = 'FAILED', storage_key = null, mime_type = null, byte_size = null,
                       width = null, height = null, attempt_count = 3, next_attempt_at = null,
                       lease_expires_at = null, claim_token = null,
                       last_error = '미디어 variant 생성에 실패했습니다.'
                 where asset_id = ? and target_width = ?
                """, assetId, targetWidth);
    }

    private void expireLease(UUID variantId) {
        jdbc.update("update website_media_variant set lease_expires_at = ? where id = ?",
                OffsetDateTime.ofInstant(START.minusSeconds(1), ZoneOffset.UTC), variantId);
    }

    private ClaimState claimState(UUID assetId) {
        return jdbc.queryForObject("""
                select status, attempt_count, claim_token
                  from website_media_variant
                 where asset_id = ? and target_width = 640
                """, (rs, rowNumber) -> new ClaimState(
                rs.getString("status"), rs.getInt("attempt_count"),
                rs.getObject("claim_token", UUID.class)), assetId);
    }

    private RetryRow retryRow(UUID assetId, int targetWidth) {
        return jdbc.queryForObject("""
                select status, attempt_count, next_attempt_at, lease_expires_at, claim_token, last_error
                  from website_media_variant
                 where asset_id = ? and target_width = ?
                """, (rs, rowNumber) -> new RetryRow(
                rs.getString("status"), rs.getInt("attempt_count"),
                rs.getObject("next_attempt_at", OffsetDateTime.class),
                rs.getObject("lease_expires_at", OffsetDateTime.class),
                rs.getObject("claim_token", UUID.class), rs.getString("last_error")),
                assetId, targetWidth);
    }

    private void assertFailure(UUID assetId, int attemptCount, Instant nextAttemptAt) {
        FailureRow failure = failureRow(assetId);
        assertThat(failure.status()).isEqualTo("FAILED");
        assertThat(failure.attemptCount()).isEqualTo(attemptCount);
        assertThat(failure.nextAttemptAt())
                .isEqualTo(OffsetDateTime.ofInstant(nextAttemptAt, ZoneOffset.UTC));
    }

    private FailureRow failureRow(UUID assetId) {
        return jdbc.queryForObject("""
                select status, attempt_count, next_attempt_at, last_error
                  from website_media_variant
                 where asset_id = ? and target_width = 640
                """, (rs, rowNumber) -> new FailureRow(
                rs.getString("status"), rs.getInt("attempt_count"),
                rs.getObject("next_attempt_at", OffsetDateTime.class), rs.getString("last_error")), assetId);
    }

    private VariantResultRow variantResultRow(UUID assetId) {
        return jdbc.queryForObject("""
                select status, storage_key, mime_type, byte_size, width, height
                  from website_media_variant
                 where asset_id = ? and target_width = 640
                """, (rs, rowNumber) -> new VariantResultRow(
                rs.getString("status"), rs.getString("storage_key"), rs.getString("mime_type"),
                rs.getLong("byte_size"), rs.getInt("width"), rs.getInt("height")), assetId);
    }

    private void overwriteWithWhitePng(Path path, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", path.toFile());
    }

    private byte[] sha256(Path path) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
    }

    private MemoryMultipartFile imageFile(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MemoryMultipartFile("variant.png", "image/png", output.toByteArray());
    }

    private Flyway isolatedFlyway(String schema, MigrationVersion target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    private void insertUploadedAsset(JdbcTemplate isolatedJdbc, String schema, UUID id, int width, String status) {
        isolatedJdbc.update("""
                insert into "%s".website_media_asset (
                    id, origin, delivery_path, storage_key, display_name, default_alt_text,
                    mime_type, byte_size, width, height, status, version
                ) values (?, 'UPLOADED', ?, ?, ?, 'migration alt', 'image/png', 100, ?, 100, ?, 1)
                """.formatted(schema), id, "/api/website/media/" + id + "/content", id + ".png",
                "migration-" + id, width, status);
    }

    private List<String> cmsChecksums(JdbcTemplate isolatedJdbc, String schema) {
        return List.of(
                checksum(isolatedJdbc, schema, "website_media_asset", "asset.id"),
                checksum(isolatedJdbc, schema, "website_media_usage",
                        "usage.page_id, usage.document_state, usage.field_path"),
                checksum(isolatedJdbc, schema, "website_page", "page.id"));
    }

    private MigrationClaimRow migrationClaimRow(JdbcTemplate isolatedJdbc, String schema, UUID assetId) {
        return isolatedJdbc.queryForObject("""
                select status, attempt_count, lease_expires_at
                  from "%s".website_media_variant
                 where asset_id = ? and target_width = 640
                """.formatted(schema), (rs, rowNumber) -> new MigrationClaimRow(
                rs.getString("status"), rs.getInt("attempt_count"),
                rs.getObject("lease_expires_at", OffsetDateTime.class)), assetId);
    }

    private String checksum(JdbcTemplate isolatedJdbc, String schema, String table, String orderBy) {
        String alias = switch (table) {
            case "website_media_asset" -> "asset";
            case "website_media_usage" -> "usage";
            case "website_page" -> "page";
            default -> throw new IllegalArgumentException("지원하지 않는 checksum table입니다.");
        };
        return isolatedJdbc.queryForObject("select md5(coalesce(string_agg(to_jsonb(%s)::text, '' order by %s), '')) from \"%s\".%s %s"
                .formatted(alias, orderBy, schema, table, alias), String.class);
    }

    private void deleteStorage() throws IOException {
        Path storage = Path.of(System.getProperty("java.io.tmpdir"), "hotel-chain-media");
        if (!Files.exists(storage)) return;
        try (var paths = Files.walk(storage)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); }
                catch (IOException error) { throw new IllegalStateException(error); }
            });
        }
    }

    private record MigrationVariantRow(UUID assetId, int targetWidth) {
    }

    private record MigrationClaimRow(String status, int attemptCount, OffsetDateTime leaseExpiresAt) {
    }

    private record VariantResultRow(
            String status, String storageKey, String mimeType, long byteSize, int width, int height) {
    }

    private record FailureRow(String status, int attemptCount, OffsetDateTime nextAttemptAt, String lastError) {
    }

    private record ClaimState(String status, int attemptCount, UUID claimToken) {
    }

    private record RetryRow(
            String status,
            int attemptCount,
            OffsetDateTime nextAttemptAt,
            OffsetDateTime leaseExpiresAt,
            UUID claimToken,
            String lastError) {
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock(START);
        }
    }

    static final class TestClock extends Clock {
        private final AtomicReference<Instant> instant;

        TestClock(Instant initial) {
            instant = new AtomicReference<>(initial);
        }

        void reset() {
            instant.set(START);
        }

        void advanceSeconds(long seconds) {
            instant.updateAndGet(current -> current.plusSeconds(seconds));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }

    private record MemoryMultipartFile(String originalFilename, String contentType, byte[] bytes) implements MultipartFile {
        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return bytes.length == 0; }
        @Override public long getSize() { return bytes.length; }
        @Override public byte[] getBytes() { return bytes.clone(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        @Override public void transferTo(java.io.File destination) throws IOException { Files.write(destination.toPath(), bytes); }
        @Override public void transferTo(Path destination) throws IOException { Files.write(destination, bytes); }
    }
}
