package team.hotelchain.webcontent.storage;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.UUID;

public final class WebsiteMediaStorageKey {
    private static final String TRASH_PREFIX = ".trash/";

    private WebsiteMediaStorageKey() {
    }

    public static String publicKey(String value) {
        String key = normalized(value);
        if (key.equals(".trash") || key.startsWith(TRASH_PREFIX)) {
            throw new IllegalArgumentException("공개 미디어 키에 격리 경로를 사용할 수 없습니다.");
        }
        return key;
    }

    public static String quarantineKey(UUID transactionId, String sourceKey) {
        if (transactionId == null) throw new IllegalArgumentException("트랜잭션 ID가 필요합니다.");
        return TRASH_PREFIX + transactionId + "/" + publicKey(sourceKey);
    }

    static String internalKey(String value) {
        return normalized(value);
    }

    private static String normalized(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("미디어 키가 필요합니다.");
        String slashKey = value.replace('\\', '/');
        if (slashKey.startsWith("/") || slashKey.matches("^[A-Za-z]:/.*")) {
            throw new IllegalArgumentException("상대 미디어 키만 사용할 수 있습니다.");
        }
        for (String segment : slashKey.split("/", -1)) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("미디어 키에 안전하지 않은 경로가 있습니다.");
            }
        }
        try {
            Path path = Path.of(slashKey).normalize();
            if (path.isAbsolute() || path.startsWith("..")) {
                throw new IllegalArgumentException("상대 미디어 키만 사용할 수 있습니다.");
            }
            return path.toString().replace('\\', '/');
        } catch (InvalidPathException cause) {
            throw new IllegalArgumentException("유효하지 않은 미디어 키입니다.", cause);
        }
    }
}
