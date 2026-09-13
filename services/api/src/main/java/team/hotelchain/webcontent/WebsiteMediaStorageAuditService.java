package team.hotelchain.webcontent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.InvalidPathException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.hotelchain.staff.StaffAccessService;

@Service
public class WebsiteMediaStorageAuditService {
    private static final Duration STALE_TEMPORARY_FILE_AGE = Duration.ofMinutes(10);

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;
    private final Clock clock;
    private final Path storageDirectory;

    public WebsiteMediaStorageAuditService(
            JdbcTemplate jdbc,
            StaffAccessService access,
            Clock clock,
            @Value("${website.media.storage-dir:}") String configuredStorageDirectory) {
        this.jdbc = jdbc;
        this.access = access;
        this.clock = clock;
        this.storageDirectory = (configuredStorageDirectory == null || configuredStorageDirectory.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "hotel-chain-media")
                : Path.of(configuredStorageDirectory)).toAbsolutePath().normalize();
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

        Set<String> actualStorageKeys = new TreeSet<>();
        Set<String> orphanStorageKeys = new TreeSet<>();
        Set<String> staleTemporaryStorageKeys = new TreeSet<>();
        Instant staleBefore = checkedAt.toInstant().minus(STALE_TEMPORARY_FILE_AGE);

        if (Files.exists(storageDirectory)) {
            Path trashDirectory = storageDirectory.resolve(".trash").normalize();
            try (Stream<Path> paths = Files.walk(storageDirectory)) {
                paths.filter(path -> !path.startsWith(trashDirectory))
                        .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .forEach(path -> classifyFile(
                                path, expectedStorageKeys, actualStorageKeys,
                                orphanStorageKeys, staleTemporaryStorageKeys, staleBefore));
            } catch (IOException cause) {
                throw new IllegalStateException("미디어 저장소를 점검하지 못했습니다.", cause);
            }
        }

        List<String> missingStorageKeys = expectedStorageKeys.stream()
                .filter(key -> !actualStorageKeys.contains(key))
                .toList();
        List<String> orphanKeys = List.copyOf(orphanStorageKeys);
        List<String> staleKeys = List.copyOf(staleTemporaryStorageKeys);
        return new WebsiteMediaStorageAudit(
                checkedAt,
                missingStorageKeys.isEmpty() && orphanKeys.isEmpty() && staleKeys.isEmpty(),
                missingStorageKeys,
                orphanKeys,
                staleKeys);
    }

    private void classifyFile(
            Path path,
            Set<String> expectedStorageKeys,
            Set<String> actualStorageKeys,
            Set<String> orphanStorageKeys,
            Set<String> staleTemporaryStorageKeys,
            Instant staleBefore) {
        String storageKey = storageDirectory.relativize(path.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
        actualStorageKeys.add(storageKey);
        if (expectedStorageKeys.contains(storageKey)) return;
        if (!storageKey.endsWith(".tmp")) {
            orphanStorageKeys.add(storageKey);
            return;
        }
        try {
            if (!Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant().isAfter(staleBefore)) {
                staleTemporaryStorageKeys.add(storageKey);
            }
        } catch (IOException cause) {
            throw new IllegalStateException("미디어 임시 파일의 수정 시각을 확인하지 못했습니다.", cause);
        }
    }

    private String relativeStorageKey(String storageKey) {
        try {
            Path relativePath = Path.of(storageKey.replace('\\', '/')).normalize();
            if (relativePath.isAbsolute() || relativePath.startsWith("..") || relativePath.toString().isBlank()) {
                throw new IllegalStateException("상대 경로가 아닌 미디어 저장소 키가 등록되어 있습니다.");
            }
            return relativePath.toString().replace('\\', '/');
        } catch (InvalidPathException cause) {
            throw new IllegalStateException("유효하지 않은 미디어 저장소 키가 등록되어 있습니다.", cause);
        }
    }
}
