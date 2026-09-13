package team.hotelchain.webcontent;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

@Service
public class WebsiteMediaService {
    private static final Logger log = LoggerFactory.getLogger(WebsiteMediaService.class);
    public static final UUID BUNDLED_ASSET_ID = UUID.fromString("14000000-0000-0000-0000-000000000001");
    public static final long MAX_UPLOAD_BYTES = 10_485_760L;
    public static final long MAX_IMAGE_PIXELS = 24_000_000L;
    public static final int PERMANENT_DELETE_GRACE_DAYS = 30;

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;
    private final WebsiteMediaVariantService variants;
    private final Path storageDirectory;

    public WebsiteMediaService(JdbcTemplate jdbc, StaffAccessService access, WebsiteMediaVariantService variants,
            @Value("${website.media.storage-dir:}") String configuredStorageDirectory) {
        this.jdbc = jdbc;
        this.access = access;
        this.variants = variants;
        this.storageDirectory = configuredStorageDirectory == null || configuredStorageDirectory.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "hotel-chain-media")
                : Path.of(configuredStorageDirectory);
    }

    @Transactional(readOnly = true)
    public List<WebsiteMediaAsset> catalog(String token) {
        return catalog(token, false);
    }

    @Transactional(readOnly = true)
    public List<WebsiteMediaAsset> catalog(String token, boolean includeArchived) {
        access.requireHeadquarters(token);
        String activeOnly = includeArchived ? "" : " where asset.status = 'ACTIVE'";
        List<WebsiteMediaAsset> assets = jdbc.query("""
                select asset.id, asset.display_name, asset.delivery_path, asset.mime_type, asset.byte_size,
                       asset.width, asset.height, asset.default_alt_text, asset.status, asset.version,
                       asset.archived_at,
                       count(usage.asset_id) as usage_count
                  from website_media_asset asset
                  left join website_media_usage usage on usage.asset_id = asset.id
                """ + activeOnly + """
                 group by asset.id, asset.display_name, asset.delivery_path, asset.mime_type, asset.byte_size,
                          asset.width, asset.height, asset.default_alt_text, asset.status, asset.version, asset.created_at,
                          asset.archived_at
                 order by asset.created_at desc, asset.display_name
                """, (rs, index) -> new WebsiteMediaAsset(
                rs.getObject("id", UUID.class), rs.getString("display_name"), rs.getString("delivery_path"),
                rs.getString("mime_type"), rs.getLong("byte_size"), rs.getInt("width"), rs.getInt("height"),
                rs.getString("default_alt_text"), rs.getInt("usage_count"), rs.getString("status"),
                rs.getInt("version"), rs.getObject("archived_at", OffsetDateTime.class),
                permanentDeleteAvailableAt(rs.getObject("archived_at", OffsetDateTime.class)), List.of()));
        return withVariants(assets);
    }

    @Transactional
    public WebsiteMediaAsset upload(String token, MultipartFile file, String displayName, String defaultAltText) {
        StaffPrincipal actor = access.requireHeadquarters(token);
        String name = requiredText(displayName, "displayName", 160);
        String alt = requiredText(defaultAltText, "defaultAltText", 200);
        if (file == null || file.isEmpty()) throw invalid("이미지 파일이 필요합니다.");
        if (file.getSize() > MAX_UPLOAD_BYTES) throw invalid("이미지 파일은 10 MiB를 넘을 수 없습니다.");

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("이미지 파일을 읽을 수 없습니다.");
        }
        if (bytes.length == 0) throw invalid("이미지 파일이 필요합니다.");
        if (bytes.length > MAX_UPLOAD_BYTES) throw invalid("이미지 파일은 10 MiB를 넘을 수 없습니다.");
        ImageInfo image = inspect(bytes);

        UUID id = UUID.randomUUID();
        String storageKey = id + ("image/png".equals(image.mimeType()) ? ".png" : ".jpg");
        Path root = storageDirectory.toAbsolutePath().normalize();
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) throw new IllegalStateException("미디어 저장 경로가 올바르지 않습니다.");

        try {
            Files.createDirectories(root);
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new IllegalStateException("이미지 파일을 저장하지 못했습니다.", exception);
        }

        try {
            jdbc.update("""
                    insert into website_media_asset (
                        id, origin, delivery_path, storage_key, display_name, default_alt_text,
                        mime_type, byte_size, width, height, status, version, created_by, updated_by
                    ) values (?, 'UPLOADED', ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 1, ?, ?)
                    """, id, deliveryPath(id), storageKey, name, alt, image.mimeType(), (long) bytes.length,
                    image.width(), image.height(), actor.id(), actor.id());
            variants.enqueueEligible(id, image.width());
        } catch (RuntimeException exception) {
            deleteStoredFile(target);
            throw exception;
        }
        return catalogAsset(id);
    }

    @Transactional
    public WebsiteMediaAsset updateMetadata(String token, UUID mediaId, WebsiteMediaMetadataRequest request) {
        StaffPrincipal actor = access.requireHeadquarters(token);
        if (request == null) throw invalid("미디어 메타데이터가 필요합니다.");
        String name = requiredText(request.displayName(), "displayName", 160);
        String alt = requiredText(request.defaultAltText(), "defaultAltText", 200);
        MediaAssetRow current = requireAssetForUpdate(mediaId);
        requireVersion(current, request.expectedVersion());
        int updated = jdbc.update("""
                update website_media_asset
                   set display_name = ?, default_alt_text = ?, version = version + 1,
                       updated_at = current_timestamp, updated_by = ?
                 where id = ? and version = ?
                """, name, alt, actor.id(), mediaId, request.expectedVersion());
        if (updated == 0) throw versionConflict();
        return catalogAsset(mediaId);
    }

    @Transactional
    public WebsiteMediaAsset archive(String token, UUID mediaId, WebsiteMediaVersionRequest request) {
        return changeStatus(token, mediaId, request, "ACTIVE", "ARCHIVED");
    }

    @Transactional
    public WebsiteMediaAsset restore(String token, UUID mediaId, WebsiteMediaVersionRequest request) {
        return changeStatus(token, mediaId, request, "ARCHIVED", "ACTIVE");
    }

    @Transactional
    public void permanentlyDelete(String token, UUID mediaId, WebsiteMediaVersionRequest request) {
        access.requireHeadquarters(token);
        if (request == null) throw invalid("미디어 버전이 필요합니다.");
        MediaAssetRow current = requireAssetForUpdate(mediaId);
        requireVersion(current, request.expectedVersion());
        if (!"UPLOADED".equals(current.origin())) throw deleteConflict("번들 미디어는 영구 삭제할 수 없습니다.");
        if (!"ARCHIVED".equals(current.status())) throw deleteConflict("보관된 미디어만 영구 삭제할 수 있습니다.");
        if (usageCount(mediaId) > 0) throw assetInUse();
        OffsetDateTime availableAt = permanentDeleteAvailableAt(current.archivedAt());
        if (availableAt == null || availableAt.isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw deleteConflict("보관 후 30일이 지난 미디어만 영구 삭제할 수 있습니다.");
        }

        List<String> storageKeys = new ArrayList<>();
        storageKeys.add(current.storageKey());
        storageKeys.addAll(variants.storageKeys(mediaId));
        List<QuarantinedFile> files = storageKeys.stream().map(this::quarantinedFile).toList();
        registerFileCompletion(files);
        files.forEach(this::moveToQuarantine);

        int deleted = jdbc.update("delete from website_media_asset where id = ? and status = 'ARCHIVED' and version = ?",
                mediaId, request.expectedVersion());
        if (deleted == 0) throw versionConflict();
    }

    @Transactional(readOnly = true)
    public List<WebsiteMediaUsage> usages(String token, UUID mediaId) {
        access.requireHeadquarters(token);
        if (asset(mediaId, false, "") == null) throw new WebsiteMediaNotFoundException(mediaId);
        return jdbc.query("""
                select usage.page_id,
                       case when usage.locale = 'en' then
                           case when usage.document_state = 'DRAFT' then translation.draft_menu_label else translation.published_menu_label end
                           else case when usage.document_state = 'DRAFT' then page.draft_menu_label else page.published_menu_label end end as page_label,
                       case when usage.locale = 'en' then
                           case when usage.document_state = 'DRAFT' then translation.draft_path else translation.published_path end
                           else case when usage.document_state = 'DRAFT' then page.draft_path else page.published_path end end as page_path,
                       page.page_type, usage.document_state, usage.field_path, usage.alt_text, usage.locale
                  from website_media_usage usage
                  join website_page page on page.id = usage.page_id
                  left join website_page_translation translation on translation.page_id = usage.page_id and translation.locale = usage.locale
                 where usage.asset_id = ?
                 order by usage.document_state, page_path, usage.field_path
                """, (rs, index) -> new WebsiteMediaUsage(
                rs.getObject("page_id", UUID.class), rs.getString("page_label"), rs.getString("page_path"),
                rs.getString("page_type"), rs.getString("document_state"), rs.getString("field_path"),
                rs.getString("alt_text"), rs.getString("locale")), mediaId);
    }

    @Transactional(readOnly = true)
    public WebsiteMediaContent publicContent(UUID mediaId) {
        MediaAssetRow asset = asset(mediaId, true, "");
        if (asset == null || !"UPLOADED".equals(asset.origin()) || asset.storageKey() == null) {
            throw new WebsiteMediaNotFoundException(mediaId);
        }
        Path root = storageDirectory.toAbsolutePath().normalize();
        Path source = root.resolve(asset.storageKey()).normalize();
        if (!source.startsWith(root) || !Files.isRegularFile(source)) throw new WebsiteMediaNotFoundException(mediaId);
        try {
            return new WebsiteMediaContent(Files.readAllBytes(source), asset.mimeType());
        } catch (IOException exception) {
            throw new WebsiteMediaNotFoundException(mediaId);
        }
    }

    MediaAssetRow requireActiveAsset(UUID mediaId) {
        MediaAssetRow asset = asset(mediaId, true, " for key share");
        if (asset == null) throw invalid("선택한 미디어를 찾을 수 없거나 사용할 수 없습니다.");
        return asset;
    }

    MediaAssetRow requireReplacementSource(UUID mediaId, String lockClause) {
        MediaAssetRow current = asset(mediaId, false, lockClause);
        if (current == null) throw new WebsiteMediaNotFoundException(mediaId);
        if (!"ACTIVE".equals(current.status())) throw replacementConflict();
        return current;
    }

    MediaAssetRow requireReplacementTarget(UUID mediaId, String lockClause) {
        MediaAssetRow current = requireReplacementSource(mediaId, lockClause);
        if (!"UPLOADED".equals(current.origin())) {
            throw invalid("교체 대상은 새로 업로드한 미디어여야 합니다.");
        }
        return current;
    }

    WebsiteMediaAsset catalogAssetForReplacement(UUID mediaId) {
        return catalogAsset(mediaId);
    }

    WebsiteMediaAsset catalogAssetForManagement(UUID mediaId) {
        return catalogAsset(mediaId);
    }

    private WebsiteMediaAsset catalogAsset(UUID mediaId) {
        WebsiteMediaAsset asset = jdbc.query("""
                select asset.id, asset.display_name, asset.delivery_path, asset.mime_type, asset.byte_size,
                       asset.width, asset.height, asset.default_alt_text, asset.status, asset.version,
                       asset.archived_at,
                       count(usage.asset_id) as usage_count
                  from website_media_asset asset
                  left join website_media_usage usage on usage.asset_id = asset.id
                 where asset.id = ?
                 group by asset.id, asset.display_name, asset.delivery_path, asset.mime_type, asset.byte_size,
                          asset.width, asset.height, asset.default_alt_text, asset.status, asset.version,
                          asset.archived_at
                """, rs -> rs.next() ? new WebsiteMediaAsset(
                rs.getObject("id", UUID.class), rs.getString("display_name"), rs.getString("delivery_path"),
                rs.getString("mime_type"), rs.getLong("byte_size"), rs.getInt("width"), rs.getInt("height"),
                rs.getString("default_alt_text"), rs.getInt("usage_count"), rs.getString("status"),
                rs.getInt("version"), rs.getObject("archived_at", OffsetDateTime.class),
                permanentDeleteAvailableAt(rs.getObject("archived_at", OffsetDateTime.class)), List.of()) : null, mediaId);
        return asset == null ? null : withVariants(List.of(asset)).getFirst();
    }

    private WebsiteMediaAsset changeStatus(String token, UUID mediaId, WebsiteMediaVersionRequest request,
            String expectedStatus, String nextStatus) {
        StaffPrincipal actor = access.requireHeadquarters(token);
        if (request == null) throw invalid("미디어 버전이 필요합니다.");
        MediaAssetRow current = requireAssetForUpdate(mediaId);
        requireVersion(current, request.expectedVersion());
        if (!expectedStatus.equals(current.status())) throw versionConflict();
        if ("ARCHIVED".equals(nextStatus) && usageCount(mediaId) > 0) throw assetInUse();
        int updated = jdbc.update("""
                update website_media_asset
                   set status = ?, archived_at = case when ? = 'ARCHIVED' then current_timestamp else null end,
                       version = version + 1, updated_at = current_timestamp, updated_by = ?
                 where id = ? and status = ? and version = ?
                """, nextStatus, nextStatus, actor.id(), mediaId, expectedStatus, request.expectedVersion());
        if (updated == 0) throw versionConflict();
        if ("ACTIVE".equals(nextStatus) && "UPLOADED".equals(current.origin())) {
            variants.enqueueEligible(mediaId, current.width());
        }
        return catalogAsset(mediaId);
    }

    private List<WebsiteMediaAsset> withVariants(List<WebsiteMediaAsset> assets) {
        Map<UUID, List<WebsiteMediaVariant>> variantsByAsset = variants.findByAssetIds(
                assets.stream().map(WebsiteMediaAsset::id).toList());
        return assets.stream().map(asset -> new WebsiteMediaAsset(
                asset.id(), asset.displayName(), asset.deliveryUrl(), asset.mimeType(), asset.byteSize(),
                asset.width(), asset.height(), asset.defaultAltText(), asset.usageCount(), asset.status(),
                asset.version(), asset.archivedAt(), asset.permanentDeleteAvailableAt(),
                variantsByAsset.getOrDefault(asset.id(), List.of()))).toList();
    }

    private MediaAssetRow requireAssetForUpdate(UUID mediaId) {
        MediaAssetRow asset = asset(mediaId, false, " for update");
        if (asset == null) throw new WebsiteMediaNotFoundException(mediaId);
        return asset;
    }

    private int usageCount(UUID mediaId) {
        Integer count = jdbc.queryForObject("select count(*) from website_media_usage where asset_id = ?", Integer.class, mediaId);
        return count == null ? 0 : count;
    }

    private void requireVersion(MediaAssetRow asset, int expectedVersion) {
        if (expectedVersion <= 0) throw invalid("미디어 버전은 1 이상이어야 합니다.");
        if (asset.version() != expectedVersion) throw versionConflict();
    }

    private WebsiteMediaConflictException versionConflict() {
        return new WebsiteMediaConflictException("WEBSITE_MEDIA_VERSION_CONFLICT",
                "다른 사용자가 미디어를 변경했습니다. 최신 목록을 다시 불러와 주세요.");
    }

    private WebsiteMediaConflictException assetInUse() {
        return new WebsiteMediaConflictException("WEBSITE_MEDIA_IN_USE",
                "초안 또는 발행 페이지에서 사용 중인 미디어는 보관하거나 영구 삭제할 수 없습니다.");
    }

    private WebsiteMediaConflictException deleteConflict(String message) {
        return new WebsiteMediaConflictException("WEBSITE_MEDIA_DELETE_CONFLICT", message);
    }

    private WebsiteMediaConflictException replacementConflict() {
        return new WebsiteMediaConflictException("WEBSITE_MEDIA_REPLACEMENT_CONFLICT",
                "미디어 사용 위치가 변경되었습니다. 영향 범위를 다시 확인해 주세요.");
    }

    private MediaAssetRow asset(UUID mediaId, boolean activeOnly, String lockClause) {
        String status = activeOnly ? " and status = 'ACTIVE'" : "";
        return jdbc.query("select id, origin, delivery_path, storage_key, mime_type, width, status, version, archived_at from website_media_asset where id = ?"
                        + status + lockClause,
                rs -> rs.next() ? new MediaAssetRow(rs.getObject("id", UUID.class), rs.getString("origin"),
                        rs.getString("delivery_path"), rs.getString("storage_key"), rs.getString("mime_type"),
                        rs.getInt("width"), rs.getString("status"), rs.getInt("version"),
                        rs.getObject("archived_at", OffsetDateTime.class)) : null,
                mediaId);
    }

    private OffsetDateTime permanentDeleteAvailableAt(OffsetDateTime archivedAt) {
        return archivedAt == null ? null : archivedAt.plusDays(PERMANENT_DELETE_GRACE_DAYS);
    }

    private QuarantinedFile quarantinedFile(String storageKey) {
        Path source = storedFile(storageKey);
        Path root = storageDirectory.toAbsolutePath().normalize();
        Path trash = root.resolve(".trash").normalize();
        Path quarantine = trash.resolve(UUID.randomUUID() + ".delete").normalize();
        if (!quarantine.startsWith(trash)) throw new IllegalStateException("미디어 저장 경로가 올바르지 않습니다.");
        return new QuarantinedFile(source, quarantine);
    }

    private Path storedFile(String storageKey) {
        Path root = storageDirectory.toAbsolutePath().normalize();
        Path source = storageKey == null ? root : root.resolve(storageKey).normalize();
        if (storageKey == null || !source.startsWith(root) || !Files.isRegularFile(source)) {
            throw new IllegalStateException("영구 삭제할 미디어 파일을 찾을 수 없습니다.");
        }
        return source;
    }

    private void moveToQuarantine(QuarantinedFile file) {
        try {
            Files.createDirectories(file.quarantine().getParent());
            Files.move(file.source(), file.quarantine());
        } catch (IOException exception) {
            throw new IllegalStateException("미디어 파일을 삭제 준비 상태로 옮기지 못했습니다.", exception);
        }
    }

    private void registerFileCompletion(List<QuarantinedFile> files) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                files.forEach(file -> restoreOrDelete(status, file));
            }
        });
    }

    private void restoreOrDelete(int status, QuarantinedFile file) {
        try {
            if (status == TransactionSynchronization.STATUS_COMMITTED) {
                Files.deleteIfExists(file.quarantine());
            } else if (Files.exists(file.quarantine())) {
                Files.createDirectories(file.source().getParent());
                Files.move(file.quarantine(), file.source());
            }
        } catch (IOException exception) {
            log.error("미디어 파일 삭제 상태를 정리하지 못했습니다. source={}, quarantine={}, transactionStatus={}",
                    file.source(), file.quarantine(), status, exception);
        }
    }

    private ImageInfo inspect(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw invalid("PNG 또는 JPEG 이미지만 업로드할 수 있습니다.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid("PNG 또는 JPEG 이미지만 업로드할 수 있습니다.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String mimeType = switch (reader.getFormatName().toLowerCase(java.util.Locale.ROOT)) {
                    case "png" -> "image/png";
                    case "jpeg", "jpg" -> "image/jpeg";
                    default -> throw invalid("PNG 또는 JPEG 이미지만 업로드할 수 있습니다.");
                };
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw invalid("이미지 크기는 24,000,000 픽셀을 넘을 수 없습니다.");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) throw invalid("이미지 파일을 읽을 수 없습니다.");
                return new ImageInfo(mimeType, width, height);
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw invalid("PNG 또는 JPEG 이미지만 업로드할 수 있습니다.");
        }
    }

    private void deleteStoredFile(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // The database insert failed; a later storage audit can remove an unreachable file.
        }
    }

    private String deliveryPath(UUID mediaId) {
        return "/api/website/media/" + mediaId + "/content";
    }

    private String requiredText(String value, String field, int maximumLength) {
        if (value == null || value.isBlank() || value.trim().length() > maximumLength) {
            throw invalid(field + "는 1~" + maximumLength + "자여야 합니다.");
        }
        return value.trim();
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("미디어 형식 오류: " + message);
    }

    record MediaAssetRow(UUID id, String origin, String deliveryPath, String storageKey, String mimeType, int width,
            String status, int version, OffsetDateTime archivedAt) {
    }

    private record ImageInfo(String mimeType, int width, int height) {
    }

    private record QuarantinedFile(Path source, Path quarantine) {
    }
}
