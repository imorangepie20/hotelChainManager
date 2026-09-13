package team.hotelchain.webcontent.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalWebsiteMediaObjectStore implements WebsiteMediaObjectStore {
    private final Path root;

    @Autowired
    public LocalWebsiteMediaObjectStore(@Value("${website.media.storage-dir:}") String configuredRoot) {
        this(configuredRoot == null || configuredRoot.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "hotel-chain-media")
                : Path.of(configuredRoot));
    }

    public LocalWebsiteMediaObjectStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return "local";
    }

    @Override
    public void put(String key, byte[] bytes, String contentType, String sha256) {
        Path target = publicPath(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException cause) {
            throw failure("write", cause);
        }
    }

    @Override
    public byte[] get(String key) {
        Path source = publicPath(key);
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new WebsiteMediaObjectNotFoundException(name(), "read");
        }
        try {
            return Files.readAllBytes(source);
        } catch (IOException cause) {
            throw failure("read", cause);
        }
    }

    @Override
    public Optional<WebsiteMediaObjectMetadata> head(String key) {
        Path source = publicPath(key);
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        return Optional.of(metadata(source, WebsiteMediaStorageKey.publicKey(key)));
    }

    @Override
    public List<WebsiteMediaObjectMetadata> list() {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return List.of();
        Path trash = root.resolve(".trash");
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> !path.startsWith(trash))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> metadata(path, relativeKey(path)))
                    .sorted(Comparator.comparing(WebsiteMediaObjectMetadata::key))
                    .toList();
        } catch (IOException cause) {
            throw failure("list", cause);
        }
    }

    @Override
    public WebsiteMediaQuarantinedObject quarantine(String key, UUID transactionId) {
        String sourceKey = WebsiteMediaStorageKey.publicKey(key);
        String quarantineKey = WebsiteMediaStorageKey.quarantineKey(transactionId, sourceKey);
        Path source = publicPath(sourceKey);
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new WebsiteMediaObjectNotFoundException(name(), "quarantine");
        }
        Path target = internalPath(quarantineKey);
        try {
            Files.createDirectories(target.getParent());
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            return new WebsiteMediaQuarantinedObject(name(), sourceKey, quarantineKey);
        } catch (IOException cause) {
            throw failure("quarantine", cause);
        }
    }

    @Override
    public void restore(WebsiteMediaQuarantinedObject object) {
        requireStore(object);
        Path source = internalPath(object.quarantineKey());
        Path target = publicPath(object.sourceKey());
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) return;
            throw new WebsiteMediaObjectNotFoundException(name(), "restore");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException cause) {
            throw failure("restore", cause);
        }
    }

    @Override
    public void purge(WebsiteMediaQuarantinedObject object) {
        requireStore(object);
        try {
            Files.deleteIfExists(internalPath(object.quarantineKey()));
        } catch (IOException cause) {
            throw failure("purge", cause);
        }
    }

    @Override
    public void deleteIfExists(String key) {
        try {
            Files.deleteIfExists(publicPath(key));
        } catch (IOException cause) {
            throw failure("delete", cause);
        }
    }

    private WebsiteMediaObjectMetadata metadata(Path path, String key) {
        try {
            return new WebsiteMediaObjectMetadata(
                    key,
                    Files.size(path),
                    Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant(),
                    WebsiteMediaDigest.sha256(path));
        } catch (IOException cause) {
            throw failure("head", cause);
        }
    }

    private Path publicPath(String key) {
        return resolve(WebsiteMediaStorageKey.publicKey(key));
    }

    private Path internalPath(String key) {
        return resolve(WebsiteMediaStorageKey.internalKey(key));
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || path.equals(root)) throw failure("resolve", null);
        return path;
    }

    private String relativeKey(Path path) {
        return root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private void requireStore(WebsiteMediaQuarantinedObject object) {
        if (object == null || !name().equals(object.storeName())) throw failure("quarantine-reference", null);
    }

    private WebsiteMediaStorageException failure(String operation, Throwable cause) {
        String message = "미디어 저장소 작업에 실패했습니다. store=" + name() + ", operation=" + operation;
        return cause == null ? new WebsiteMediaStorageException(message) : new WebsiteMediaStorageException(message, cause);
    }
}
