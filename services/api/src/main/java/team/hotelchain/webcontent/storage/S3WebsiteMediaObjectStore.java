package team.hotelchain.webcontent.storage;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class S3WebsiteMediaObjectStore implements WebsiteMediaObjectStore {
    private static final String SHA256_METADATA = "sha256";

    private final S3Client client;
    private final String bucket;

    public S3WebsiteMediaObjectStore(S3Client client, String bucket) {
        if (client == null) throw new IllegalArgumentException("S3 클라이언트가 필요합니다.");
        if (bucket == null || bucket.isBlank()) throw new IllegalArgumentException("S3 버킷이 필요합니다.");
        this.client = client;
        this.bucket = bucket.trim();
    }

    @Override
    public String name() {
        return "s3";
    }

    @Override
    public void put(String key, byte[] bytes, String contentType, String sha256) {
        String objectKey = WebsiteMediaStorageKey.publicKey(key);
        Optional<WebsiteMediaObjectMetadata> current = head(objectKey);
        if (current.isPresent()) {
            requireSameDigest(current.get(), sha256, "write");
            return;
        }
        try {
            client.putObject(request -> request.bucket(bucket)
                            .key(objectKey)
                            .contentType(contentType)
                            .metadata(Map.of(SHA256_METADATA, sha256))
                            .ifNoneMatch("*"),
                    RequestBody.fromBytes(bytes));
        } catch (S3Exception cause) {
            if (cause.statusCode() == 412) {
                Optional<WebsiteMediaObjectMetadata> raced = head(objectKey);
                if (raced.isPresent()) {
                    requireSameDigest(raced.get(), sha256, "write");
                    return;
                }
            }
            throw failure("write", cause);
        } catch (RuntimeException cause) {
            throw failure("write", cause);
        }
    }

    @Override
    public byte[] get(String key) {
        String objectKey = WebsiteMediaStorageKey.publicKey(key);
        try {
            return client.getObjectAsBytes(request -> request.bucket(bucket).key(objectKey)).asByteArray();
        } catch (S3Exception cause) {
            if (cause.statusCode() == 404) throw new WebsiteMediaObjectNotFoundException(name(), "read");
            throw failure("read", cause);
        } catch (RuntimeException cause) {
            throw failure("read", cause);
        }
    }

    @Override
    public Optional<WebsiteMediaObjectMetadata> head(String key) {
        return headInternal(WebsiteMediaStorageKey.publicKey(key), "head");
    }

    @Override
    public List<WebsiteMediaObjectMetadata> list() {
        try {
            return client.listObjectsV2Paginator(request -> request.bucket(bucket)).contents().stream()
                    .filter(object -> !object.key().equals(".trash") && !object.key().startsWith(".trash/"))
                    .map(object -> headInternal(object.key(), "list").orElseThrow(
                            () -> new WebsiteMediaObjectNotFoundException(name(), "list")))
                    .sorted(Comparator.comparing(WebsiteMediaObjectMetadata::key))
                    .toList();
        } catch (WebsiteMediaStorageException cause) {
            throw cause;
        } catch (RuntimeException cause) {
            throw failure("list", cause);
        }
    }

    @Override
    public WebsiteMediaQuarantinedObject quarantine(String key, UUID transactionId) {
        String sourceKey = WebsiteMediaStorageKey.publicKey(key);
        String quarantineKey = WebsiteMediaStorageKey.quarantineKey(transactionId, sourceKey);
        WebsiteMediaObjectMetadata source = headInternal(sourceKey, "quarantine")
                .orElseThrow(() -> new WebsiteMediaObjectNotFoundException(name(), "quarantine"));
        copy(sourceKey, quarantineKey, "quarantine");
        try {
            requireSameObject(source, headInternal(quarantineKey, "quarantine")
                    .orElseThrow(() -> new WebsiteMediaObjectNotFoundException(name(), "quarantine")), "quarantine");
            deleteInternal(sourceKey, "quarantine");
            return new WebsiteMediaQuarantinedObject(name(), sourceKey, quarantineKey);
        } catch (RuntimeException cause) {
            try {
                deleteInternal(quarantineKey, "quarantine-cleanup");
            } catch (RuntimeException cleanupFailure) {
                cause.addSuppressed(cleanupFailure);
            }
            throw cause;
        }
    }

    @Override
    public void restore(WebsiteMediaQuarantinedObject object) {
        requireStore(object);
        Optional<WebsiteMediaObjectMetadata> quarantined = headInternal(object.quarantineKey(), "restore");
        if (quarantined.isEmpty()) {
            if (head(object.sourceKey()).isPresent()) return;
            throw new WebsiteMediaObjectNotFoundException(name(), "restore");
        }
        Optional<WebsiteMediaObjectMetadata> target = head(object.sourceKey());
        if (target.isPresent()) {
            requireSameObject(quarantined.get(), target.get(), "restore");
        } else {
            copy(object.quarantineKey(), object.sourceKey(), "restore");
            requireSameObject(quarantined.get(), head(object.sourceKey())
                    .orElseThrow(() -> new WebsiteMediaObjectNotFoundException(name(), "restore")), "restore");
        }
        deleteInternal(object.quarantineKey(), "restore");
    }

    @Override
    public void purge(WebsiteMediaQuarantinedObject object) {
        requireStore(object);
        deleteInternal(object.quarantineKey(), "purge");
    }

    @Override
    public void deleteIfExists(String key) {
        deleteInternal(WebsiteMediaStorageKey.publicKey(key), "delete");
    }

    private Optional<WebsiteMediaObjectMetadata> headInternal(String key, String operation) {
        String objectKey = WebsiteMediaStorageKey.internalKey(key);
        try {
            HeadObjectResponse response = client.headObject(request -> request.bucket(bucket).key(objectKey));
            return Optional.of(new WebsiteMediaObjectMetadata(
                    objectKey,
                    response.contentLength(),
                    response.lastModified(),
                    response.metadata().get(SHA256_METADATA)));
        } catch (S3Exception cause) {
            if (cause.statusCode() == 404) return Optional.empty();
            throw failure(operation, cause);
        } catch (RuntimeException cause) {
            throw failure(operation, cause);
        }
    }

    private void copy(String sourceKey, String targetKey, String operation) {
        try {
            client.copyObject(CopyObjectRequest.builder()
                    .bucket(bucket)
                    .key(WebsiteMediaStorageKey.internalKey(targetKey))
                    .copySource(encodedCopySource(WebsiteMediaStorageKey.internalKey(sourceKey)))
                    .metadataDirective(MetadataDirective.COPY)
                    .build());
        } catch (RuntimeException cause) {
            throw failure(operation, cause);
        }
    }

    private void deleteInternal(String key, String operation) {
        try {
            client.deleteObject(request -> request.bucket(bucket).key(WebsiteMediaStorageKey.internalKey(key)));
        } catch (RuntimeException cause) {
            throw failure(operation, cause);
        }
    }

    private String encodedCopySource(String key) {
        return URLEncoder.encode(bucket + "/" + key, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%2F", "/");
    }

    private void requireSameDigest(WebsiteMediaObjectMetadata metadata, String sha256, String operation) {
        if (sha256 == null || !sha256.equals(metadata.sha256())) throw failure(operation, null);
    }

    private void requireSameObject(
            WebsiteMediaObjectMetadata expected,
            WebsiteMediaObjectMetadata actual,
            String operation) {
        if (expected.byteSize() != actual.byteSize()
                || expected.sha256() == null
                || !expected.sha256().equals(actual.sha256())) {
            throw failure(operation, null);
        }
    }

    private void requireStore(WebsiteMediaQuarantinedObject object) {
        if (object == null || !name().equals(object.storeName())) throw failure("quarantine-reference", null);
    }

    private WebsiteMediaStorageException failure(String operation, Throwable cause) {
        String message = "미디어 저장소 작업에 실패했습니다. store=" + name() + ", operation=" + operation;
        return cause == null ? new WebsiteMediaStorageException(message) : new WebsiteMediaStorageException(message, cause);
    }
}
