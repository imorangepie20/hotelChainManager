package team.hotelchain.webcontent.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class WebsiteMediaDigest {
    private WebsiteMediaDigest() {
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException cause) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", cause);
        }
    }

    public static String sha256(Path path) throws IOException {
        return sha256(Files.readAllBytes(path));
    }
}
