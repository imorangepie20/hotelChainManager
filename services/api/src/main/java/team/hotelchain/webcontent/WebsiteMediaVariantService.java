package team.hotelchain.webcontent;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import team.hotelchain.staff.StaffAccessService;

@Service
public class WebsiteMediaVariantService {
    private static final Logger log = LoggerFactory.getLogger(WebsiteMediaVariantService.class);
    private static final List<Integer> TARGET_WIDTHS = List.of(640, 1280);
    private static final Duration LEASE_DURATION = Duration.ofMinutes(5);
    private static final String GENERIC_FAILURE = "미디어 variant 생성에 실패했습니다.";
    private static final Set<String> ALLOWED_FAILURES = Set.of(
            GENERIC_FAILURE,
            "원본 미디어 파일을 찾을 수 없습니다.",
            "미디어 저장 경로가 올바르지 않습니다.");

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final StaffAccessService access;
    private final Path storageDirectory;

    public WebsiteMediaVariantService(
            JdbcTemplate jdbc,
            Clock clock,
            StaffAccessService access,
            @Value("${website.media.storage-dir:}") String configuredStorageDirectory) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.access = access;
        this.storageDirectory = configuredStorageDirectory == null || configuredStorageDirectory.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "hotel-chain-media")
                : Path.of(configuredStorageDirectory);
    }

    @Transactional
    public void enqueueEligible(UUID assetId, int sourceWidth) {
        for (int targetWidth : TARGET_WIDTHS) {
            if (targetWidth <= sourceWidth) {
                jdbc.update("""
                        insert into website_media_variant (id, asset_id, format, target_width, status)
                        values (?, ?, 'WEBP', ?, 'PENDING')
                        on conflict (asset_id, format, target_width) do nothing
                        """, UUID.randomUUID(), assetId, targetWidth);
            }
        }
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<WebsiteMediaVariant>> findByAssetIds(List<UUID> assetIds) {
        if (assetIds.isEmpty()) return Map.of();
        String placeholders = String.join(", ", Collections.nCopies(assetIds.size(), "?"));
        Map<UUID, List<WebsiteMediaVariant>> variantsByAsset = new LinkedHashMap<>();
        jdbc.query("""
                select id, asset_id, format, target_width, status, storage_key, mime_type,
                       byte_size, width, height, attempt_count, last_error, updated_at
                  from website_media_variant
                 where asset_id in (%s)
                 order by target_width
                """.formatted(placeholders), rs -> {
            UUID assetId = rs.getObject("asset_id", UUID.class);
            String status = rs.getString("status");
            int targetWidth = rs.getInt("target_width");
            String deliveryUrl = "READY".equals(status)
                    ? "/api/website/media/" + assetId + "/variants/" + targetWidth + ".webp"
                    : null;
            WebsiteMediaVariant variant = new WebsiteMediaVariant(
                    rs.getObject("id", UUID.class), rs.getString("format"), targetWidth, status, deliveryUrl,
                    rs.getString("mime_type"), rs.getObject("byte_size", Long.class),
                    rs.getObject("width", Integer.class), rs.getObject("height", Integer.class),
                    rs.getInt("attempt_count"), rs.getString("last_error"),
                    rs.getObject("updated_at", OffsetDateTime.class));
            variantsByAsset.computeIfAbsent(assetId, ignored -> new ArrayList<>()).add(variant);
        }, assetIds.toArray());
        return variantsByAsset;
    }

    @Transactional(readOnly = true)
    public WebsiteMediaContent publicContent(UUID mediaId, int targetWidth) {
        String storageKey = jdbc.query("""
                select variant.storage_key
                  from website_media_variant variant
                  join website_media_asset asset on asset.id = variant.asset_id
                 where asset.id = ? and asset.status = 'ACTIVE' and asset.origin = 'UPLOADED'
                   and variant.format = 'WEBP' and variant.target_width = ? and variant.status = 'READY'
                """, rs -> rs.next() ? rs.getString("storage_key") : null, mediaId, targetWidth);
        if (storageKey == null) throw new WebsiteMediaNotFoundException(mediaId);

        Path root = storageDirectory.toAbsolutePath().normalize();
        Path source = root.resolve(storageKey).normalize();
        if (!source.startsWith(root) || !Files.isRegularFile(source)) {
            throw new WebsiteMediaNotFoundException(mediaId);
        }
        try {
            return new WebsiteMediaContent(Files.readAllBytes(source), "image/webp");
        } catch (IOException exception) {
            throw new WebsiteMediaNotFoundException(mediaId);
        }
    }

    @Transactional
    public void retry(String token, UUID mediaId, int targetWidth) {
        access.requireHeadquarters(token);
        if (!TARGET_WIDTHS.contains(targetWidth)) {
            throw new IllegalArgumentException("미디어 variant 폭은 640 또는 1280이어야 합니다.");
        }

        List<RetryAsset> assets = jdbc.query("""
                select status, origin
                  from website_media_asset
                 where id = ?
                 for update
                """, (rs, rowNumber) -> new RetryAsset(rs.getString("status"), rs.getString("origin")), mediaId);
        if (assets.isEmpty()) throw new WebsiteMediaNotFoundException(mediaId);
        RetryAsset asset = assets.getFirst();
        if (!"ACTIVE".equals(asset.status()) || !"UPLOADED".equals(asset.origin())) throw retryConflict();

        List<RetryVariant> retryVariants = jdbc.query("""
                select id, status
                  from website_media_variant
                 where asset_id = ? and format = 'WEBP' and target_width = ?
                 for update
                """, (rs, rowNumber) -> new RetryVariant(
                rs.getObject("id", UUID.class), rs.getString("status")), mediaId, targetWidth);
        if (retryVariants.isEmpty()) throw new WebsiteMediaNotFoundException(mediaId);
        RetryVariant variant = retryVariants.getFirst();
        if (!"FAILED".equals(variant.status())) throw retryConflict();

        int updated = jdbc.update("""
                update website_media_variant
                   set status = 'PENDING', storage_key = null, mime_type = null, byte_size = null,
                       width = null, height = null, attempt_count = 0, next_attempt_at = null,
                       lease_expires_at = null, claim_token = null, last_error = null, updated_at = ?
                 where id = ? and status = 'FAILED'
                """, now(), variant.id());
        if (updated != 1) throw retryConflict();
    }

    @Transactional
    public List<String> storageKeys(UUID mediaId) {
        return jdbc.query("""
                select status, storage_key
                  from website_media_variant
                 where asset_id = ?
                 order by target_width
                 for update
                """, (rs, rowNumber) -> new VariantStorage(
                rs.getString("status"), rs.getString("storage_key")), mediaId).stream()
                .filter(variant -> "READY".equals(variant.status()))
                .map(VariantStorage::storageKey)
                .toList();
    }

    @Transactional
    public Optional<VariantClaim> claimNext() {
        OffsetDateTime now = now();
        jdbc.update("""
                update website_media_variant
                   set status = 'FAILED', lease_expires_at = null, next_attempt_at = null,
                       claim_token = null, last_error = ?, updated_at = ?
                 where status = 'PROCESSING' and attempt_count >= 3 and lease_expires_at <= ?
                """, GENERIC_FAILURE, now, now);

        List<VariantClaim> candidates = jdbc.query("""
                select variant.id, variant.asset_id, variant.target_width, asset.storage_key,
                       variant.attempt_count
                  from website_media_variant variant
                  join website_media_asset asset on asset.id = variant.asset_id
                 where asset.status = 'ACTIVE' and asset.origin = 'UPLOADED'
                   and variant.attempt_count < 3
                   and (
                       variant.status = 'PENDING'
                       or (variant.status = 'FAILED' and variant.next_attempt_at <= ?)
                       or (variant.status = 'PROCESSING' and variant.lease_expires_at <= ?)
                   )
                 order by variant.created_at, variant.target_width
                 for update of variant skip locked
                 limit 1
                """, (rs, rowNumber) -> new VariantClaim(
                rs.getObject("id", UUID.class),
                rs.getObject("asset_id", UUID.class),
                rs.getInt("target_width"),
                rs.getString("storage_key"),
                rs.getInt("attempt_count") + 1,
                UUID.randomUUID()), now, now);
        if (candidates.isEmpty()) return Optional.empty();

        VariantClaim claim = candidates.getFirst();
        jdbc.update("""
                update website_media_variant
                   set status = 'PROCESSING', attempt_count = ?, next_attempt_at = null,
                       lease_expires_at = ?, claim_token = ?, last_error = null, updated_at = ?
                 where id = ?
                """, claim.attemptCount(), now.plus(LEASE_DURATION), claim.claimToken(), now, claim.variantId());
        return Optional.of(claim);
    }

    @Transactional(rollbackFor = IOException.class)
    public boolean completeReady(
            UUID variantId,
            int attemptCount,
            UUID claimToken,
            String storageKey,
            WebsiteMediaVariantEncoder.Result result,
            Path temporaryTarget,
            Path finalTarget) throws IOException {
        List<VariantState> states = jdbc.query("""
                select status, attempt_count, claim_token, storage_key
                  from website_media_variant
                 where id = ?
                 for update
                """, (rs, rowNumber) -> new VariantState(
                rs.getString("status"), rs.getInt("attempt_count"),
                rs.getObject("claim_token", UUID.class), rs.getString("storage_key")), variantId);
        if (states.isEmpty()
                || !"PROCESSING".equals(states.getFirst().status())
                || states.getFirst().attemptCount() != attemptCount
                || claimToken == null
                || !claimToken.equals(states.getFirst().claimToken())) {
            return false;
        }

        Path previousTarget = previousTarget(finalTarget, states.getFirst().storageKey());
        ReadyFilePublication publication = new ReadyFilePublication(
                variantId, temporaryTarget, finalTarget, previousTarget);
        TransactionSynchronizationManager.registerSynchronization(publication);
        publication.publish();
        int updated = jdbc.update("""
                update website_media_variant
                   set status = 'READY', storage_key = ?, mime_type = ?, byte_size = ?,
                       width = ?, height = ?, next_attempt_at = null, lease_expires_at = null,
                       claim_token = null, last_error = null, updated_at = ?
                 where id = ? and status = 'PROCESSING' and attempt_count = ? and claim_token = ?
                """, storageKey, result.mimeType(), result.byteSize(), result.width(), result.height(),
                now(), variantId, attemptCount, claimToken);
        if (updated != 1) throw new IllegalStateException("현재 미디어 variant claim을 완료할 수 없습니다.");
        return true;
    }

    @Transactional
    public boolean completeFailed(UUID variantId, int attemptCount, UUID claimToken, String errorSummary) {
        OffsetDateTime now = now();
        OffsetDateTime nextAttemptAt = switch (attemptCount) {
            case 1 -> now.plusSeconds(30);
            case 2 -> now.plusMinutes(2);
            default -> null;
        };
        int updated = jdbc.update("""
                update website_media_variant
                   set status = 'FAILED', next_attempt_at = ?, lease_expires_at = null,
                       claim_token = null, last_error = ?, updated_at = ?
                 where id = ? and status = 'PROCESSING' and attempt_count = ? and claim_token = ?
                """, nextAttemptAt, allowedFailure(errorSummary), now, variantId, attemptCount, claimToken);
        return updated == 1;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private String allowedFailure(String errorSummary) {
        return ALLOWED_FAILURES.contains(errorSummary) ? errorSummary : GENERIC_FAILURE;
    }

    private WebsiteMediaConflictException retryConflict() {
        return new WebsiteMediaConflictException("WEBSITE_MEDIA_VARIANT_RETRY_CONFLICT",
                "실패한 활성 업로드 미디어 variant만 다시 시도할 수 있습니다.");
    }

    public record VariantClaim(
            UUID variantId,
            UUID assetId,
            int targetWidth,
            String sourceStorageKey,
            int attemptCount,
            UUID claimToken) {
    }

    private Path previousTarget(Path finalTarget, String storageKey) {
        if (storageKey == null || finalTarget.getParent() == null) return null;
        Path root = finalTarget.getParent().toAbsolutePath().normalize();
        Path candidate = root.resolve(storageKey).toAbsolutePath().normalize();
        return candidate.startsWith(root) && !candidate.equals(finalTarget) ? candidate : null;
    }

    private record VariantState(String status, int attemptCount, UUID claimToken, String storageKey) {
    }

    private record RetryAsset(String status, String origin) {
    }

    private record RetryVariant(UUID id, String status) {
    }

    private record VariantStorage(String status, String storageKey) {
    }

    static final class ReadyFilePublication implements TransactionSynchronization {
        private final UUID variantId;
        private final Path temporaryTarget;
        private final Path finalTarget;
        private final Path previousTarget;
        private boolean published;

        ReadyFilePublication(UUID variantId, Path temporaryTarget, Path finalTarget) {
            this(variantId, temporaryTarget, finalTarget, null);
        }

        private ReadyFilePublication(
                UUID variantId,
                Path temporaryTarget,
                Path finalTarget,
                Path previousTarget) {
            this.variantId = variantId;
            this.temporaryTarget = temporaryTarget;
            this.finalTarget = finalTarget;
            this.previousTarget = previousTarget;
        }

        void publish() throws IOException {
            if (Files.exists(finalTarget)) throw new FileAlreadyExistsException(finalTarget.toString());
            moveWithoutReplacement(temporaryTarget, finalTarget);
            published = true;
        }

        @Override
        public void afterCompletion(int status) {
            try {
                if (status == STATUS_COMMITTED) {
                    if (previousTarget != null) Files.deleteIfExists(previousTarget);
                    return;
                }
                if (status == STATUS_ROLLED_BACK && published) Files.deleteIfExists(finalTarget);
            } catch (IOException exception) {
                log.error(
                        "미디어 variant 파일 발행 상태를 정리하지 못했습니다. variantId={}, target={}, previous={}, transactionStatus={}",
                        variantId, finalTarget, previousTarget, status, exception);
            }
        }

        private static void moveWithoutReplacement(Path source, Path target) throws IOException {
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(source, target);
            }
        }
    }
}
