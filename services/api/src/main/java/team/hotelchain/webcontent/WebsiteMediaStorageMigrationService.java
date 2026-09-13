package team.hotelchain.webcontent;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.webcontent.storage.WebsiteMediaObjectMetadata;
import team.hotelchain.webcontent.storage.WebsiteMediaStorageGateway;
import team.hotelchain.webcontent.storage.WebsiteMediaStorageMode;
import team.hotelchain.webcontent.storage.WebsiteMediaStorageProperties;

@Service
public class WebsiteMediaStorageMigrationService {
    private static final Logger log = LoggerFactory.getLogger(WebsiteMediaStorageMigrationService.class);
    private static final int BATCH_SIZE = 100;

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;
    private final WebsiteMediaStorageGateway storage;
    private final WebsiteMediaStorageProperties properties;
    private final MeterRegistry meterRegistry;

    public WebsiteMediaStorageMigrationService(
            JdbcTemplate jdbc,
            StaffAccessService access,
            WebsiteMediaStorageGateway storage,
            WebsiteMediaStorageProperties properties,
            MeterRegistry meterRegistry) {
        this.jdbc = jdbc;
        this.access = access;
        this.storage = storage;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Transactional(readOnly = true)
    public WebsiteMediaStorageMigrationStatus status(String token) {
        access.requireHeadquarters(token);
        Counts counts = new Counts();
        for (String key : referencedKeys()) counts.add(classify(key));
        return new WebsiteMediaStorageMigrationStatus(
                modeName(),
                counts.total,
                counts.both,
                counts.localOnly,
                counts.s3Only,
                counts.mismatch,
                counts.missing,
                Math.round(meterRegistry.counter(
                        "media.storage.fallback", "operation", "read").count()));
    }

    @Transactional(readOnly = true)
    public WebsiteMediaStorageBackfillResult backfill(String token) {
        access.requireHeadquarters(token);
        if (properties.getMode() != WebsiteMediaStorageMode.MIRROR) {
            throw new WebsiteMediaConflictException(
                    "WEBSITE_MEDIA_STORAGE_MODE_CONFLICT",
                    "mirror 모드에서만 S3 백필을 실행할 수 있습니다.");
        }

        int examined = 0;
        int copied = 0;
        int skipped = 0;
        int mismatch = 0;
        int failed = 0;
        List<String> failedKeys = new ArrayList<>();
        for (String key : referencedKeys()) {
            State state = classify(key);
            if (state == State.BOTH) continue;
            if (examined >= BATCH_SIZE) break;
            examined++;
            if (state == State.MISMATCH) {
                mismatch++;
                outcome("mismatch");
                continue;
            }
            if (state != State.LOCAL_ONLY) {
                skipped++;
                outcome("skipped");
                continue;
            }
            try {
                storage.copyIfAbsent("local", "s3", key, contentTypeForKey(key));
                copied++;
                outcome("copied");
            } catch (RuntimeException cause) {
                failed++;
                failedKeys.add(key);
                outcome("failed");
                log.warn("미디어 S3 백필에 실패했습니다. key={}, cause={}", key, cause.getClass().getSimpleName());
            }
        }
        return new WebsiteMediaStorageBackfillResult(
                examined, copied, skipped, mismatch, failed, List.copyOf(failedKeys));
    }

    private List<String> referencedKeys() {
        return jdbc.queryForList("""
                select storage_key
                  from website_media_asset
                 where origin = 'UPLOADED' and storage_key is not null
                union
                select storage_key
                  from website_media_variant
                 where status = 'READY' and storage_key is not null
                 order by storage_key
                """, String.class);
    }

    private State classify(String key) {
        Optional<WebsiteMediaObjectMetadata> local = storage.head("local", key);
        Optional<WebsiteMediaObjectMetadata> s3 = storage.storeNames().contains("s3")
                ? storage.head("s3", key) : Optional.empty();
        if (local.isEmpty() && s3.isEmpty()) return State.MISSING;
        if (local.isPresent() && s3.isEmpty()) return State.LOCAL_ONLY;
        if (local.isEmpty()) return State.S3_ONLY;
        WebsiteMediaObjectMetadata left = local.orElseThrow();
        WebsiteMediaObjectMetadata right = s3.orElseThrow();
        return left.byteSize() == right.byteSize()
                        && left.sha256() != null
                        && left.sha256().equals(right.sha256())
                ? State.BOTH : State.MISMATCH;
    }

    private String contentTypeForKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        throw new IllegalArgumentException("지원하지 않는 미디어 확장자입니다.");
    }

    private String modeName() {
        return properties.getMode().name().toLowerCase().replace('_', '-');
    }

    private void outcome(String value) {
        meterRegistry.counter("media.storage.backfill", "outcome", value).increment();
    }

    private enum State {
        BOTH, LOCAL_ONLY, S3_ONLY, MISMATCH, MISSING
    }

    private static final class Counts {
        private int total;
        private int both;
        private int localOnly;
        private int s3Only;
        private int mismatch;
        private int missing;

        private void add(State state) {
            total++;
            switch (state) {
                case BOTH -> both++;
                case LOCAL_ONLY -> localOnly++;
                case S3_ONLY -> s3Only++;
                case MISMATCH -> mismatch++;
                case MISSING -> missing++;
            }
        }
    }
}
