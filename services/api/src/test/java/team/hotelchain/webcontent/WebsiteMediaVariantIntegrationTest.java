package team.hotelchain.webcontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import team.hotelchain.staff.StaffAccessService;

@SpringBootTest
@Transactional
class WebsiteMediaVariantIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired DataSource dataSource;
    @org.springframework.beans.factory.annotation.Autowired StaffAccessService staffAccess;
    @org.springframework.beans.factory.annotation.Autowired WebsiteMediaService media;
    @org.springframework.beans.factory.annotation.Autowired WebsiteMediaVariantService variants;

    @BeforeEach
    void seed() throws IOException {
        deleteStorage();
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role) values (?, ?, ?, ?, 'HQ_ADMIN')",
                UUID.randomUUID(), "variant-hq@example.com", "Variant 본사 관리자",
                new BCryptPasswordEncoder().encode("hq-password"));
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

    private String headquartersToken() {
        return staffAccess.login("variant-hq@example.com", "hq-password").token();
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
