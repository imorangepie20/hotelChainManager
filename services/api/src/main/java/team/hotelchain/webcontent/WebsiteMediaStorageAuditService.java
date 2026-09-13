package team.hotelchain.webcontent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.webcontent.storage.WebsiteMediaObjectMetadata;
import team.hotelchain.webcontent.storage.WebsiteMediaStorageGateway;
import team.hotelchain.webcontent.storage.WebsiteMediaStorageKey;
import team.hotelchain.webcontent.storage.WebsiteMediaStorageProperties;

@Service
public class WebsiteMediaStorageAuditService {
    private static final Duration STALE_TEMPORARY_FILE_AGE = Duration.ofMinutes(10);

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;
    private final Clock clock;
    private final WebsiteMediaStorageGateway storage;
    private final WebsiteMediaStorageProperties properties;

    public WebsiteMediaStorageAuditService(
            JdbcTemplate jdbc,
            StaffAccessService access,
            Clock clock,
            WebsiteMediaStorageGateway storage,
            WebsiteMediaStorageProperties properties) {
        this.jdbc = jdbc;
        this.access = access;
        this.clock = clock;
        this.storage = storage;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public WebsiteMediaStorageAudit audit(String token) {
        access.requireHeadquarters(token);
        OffsetDateTime checkedAt = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        Set<String> expectedStorageKeys = new TreeSet<>();
        jdbc.queryForList("""
                select storage_key
                  from website_media_asset
                 where origin = 'UPLOADED' and storage_key is not null
                union all
                select storage_key
                  from website_media_variant
                 where storage_key is not null
                """, String.class).stream()
                .map(this::relativeStorageKey)
                .forEach(expectedStorageKeys::add);

        Instant staleBefore = checkedAt.toInstant().minus(STALE_TEMPORARY_FILE_AGE);
        List<WebsiteMediaStoreAudit> stores = new ArrayList<>();
        for (String storeName : storage.storeNames()) {
            stores.add(auditStore(storeName, expectedStorageKeys, staleBefore));
        }
        String preferredStore = properties.getMode() == team.hotelchain.webcontent.storage.WebsiteMediaStorageMode.S3_PRIMARY
                ? "s3" : "local";
        WebsiteMediaStoreAudit preferred = stores.stream()
                .filter(store -> store.storeName().equals(preferredStore))
                .findFirst()
                .orElseThrow();
        return new WebsiteMediaStorageAudit(
                checkedAt,
                stores.stream().allMatch(WebsiteMediaStoreAudit::healthy),
                preferred.missingStorageKeys(),
                preferred.orphanStorageKeys(),
                preferred.staleTemporaryStorageKeys(),
                modeName(),
                List.copyOf(stores));
    }

    private WebsiteMediaStoreAudit auditStore(
            String storeName,
            Set<String> expectedStorageKeys,
            Instant staleBefore) {
        Set<String> actualStorageKeys = new TreeSet<>();
        Set<String> orphanStorageKeys = new TreeSet<>();
        Set<String> staleTemporaryStorageKeys = new TreeSet<>();
        storage.list(storeName).forEach(metadata -> classifyFile(
                metadata, expectedStorageKeys, actualStorageKeys,
                orphanStorageKeys, staleTemporaryStorageKeys, staleBefore));
        List<String> missing = expectedStorageKeys.stream()
                .filter(key -> !actualStorageKeys.contains(key))
                .toList();
        List<String> orphan = List.copyOf(orphanStorageKeys);
        List<String> stale = List.copyOf(staleTemporaryStorageKeys);
        return new WebsiteMediaStoreAudit(
                storeName,
                missing.isEmpty() && orphan.isEmpty() && stale.isEmpty(),
                missing,
                orphan,
                stale);
    }

    private String modeName() {
        return properties.getMode().name().toLowerCase().replace('_', '-');
    }

    private void classifyFile(
            WebsiteMediaObjectMetadata metadata,
            Set<String> expectedStorageKeys,
            Set<String> actualStorageKeys,
            Set<String> orphanStorageKeys,
            Set<String> staleTemporaryStorageKeys,
            Instant staleBefore) {
        String storageKey = metadata.key();
        actualStorageKeys.add(storageKey);
        if (expectedStorageKeys.contains(storageKey)) return;
        if (!storageKey.endsWith(".tmp")) {
            orphanStorageKeys.add(storageKey);
            return;
        }
        if (!metadata.lastModified().isAfter(staleBefore)) {
            staleTemporaryStorageKeys.add(storageKey);
        }
    }

    private String relativeStorageKey(String storageKey) {
        try {
            return WebsiteMediaStorageKey.publicKey(storageKey);
        } catch (IllegalArgumentException cause) {
            throw new IllegalStateException("유효하지 않은 미디어 저장소 키가 등록되어 있습니다.", cause);
        }
    }
}
