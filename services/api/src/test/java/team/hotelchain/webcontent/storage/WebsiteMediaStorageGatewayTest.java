package team.hotelchain.webcontent.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WebsiteMediaStorageGatewayTest {
    private static final byte[] BYTES = {1, 2, 3};
    private static final byte[] OTHER_BYTES = {4, 5, 6};
    private static final UUID TRANSACTION_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void mirrorCompensatesLocalWriteWhenS3WriteFails() {
        var local = new RecordingObjectStore("local");
        var s3 = new RecordingObjectStore("s3");
        s3.failNext(Operation.PUT);

        assertThatThrownBy(() -> gateway(WebsiteMediaStorageMode.MIRROR, local, s3)
                .put("asset.png", BYTES, "image/png"))
                .isInstanceOf(WebsiteMediaStorageException.class);
        assertThat(local.exists("asset.png")).isFalse();
        assertThat(s3.exists("asset.png")).isFalse();
    }

    @Test
    void mirrorReadsOnlyLocal() {
        var local = new RecordingObjectStore("local").seed("asset.png", BYTES);
        var s3 = new RecordingObjectStore("s3").seed("asset.png", OTHER_BYTES);

        assertThat(gateway(WebsiteMediaStorageMode.MIRROR, local, s3).get("asset.png"))
                .containsExactly(BYTES);
        assertThat(s3.calls(Operation.GET)).isZero();
    }

    @Test
    void s3PrimaryReadsS3First() {
        var local = new RecordingObjectStore("local").seed("asset.png", OTHER_BYTES);
        var s3 = new RecordingObjectStore("s3").seed("asset.png", BYTES);

        assertThat(gateway(WebsiteMediaStorageMode.S3_PRIMARY, local, s3).get("asset.png"))
                .containsExactly(BYTES);
        assertThat(local.calls(Operation.GET)).isZero();
    }

    @Test
    void s3PrimaryFallsBackToLocalAndIncrementsCounter() {
        var local = new RecordingObjectStore("local").seed("asset.png", BYTES);
        var s3 = new RecordingObjectStore("s3");
        s3.failNext(Operation.GET);
        var metrics = new SimpleMeterRegistry();
        var gateway = new WebsiteMediaStorageGateway(
                WebsiteMediaStorageMode.S3_PRIMARY, local, Optional.of(s3), metrics);

        assertThat(gateway.get("asset.png")).containsExactly(BYTES);
        assertThat(metrics.counter("media.storage.fallback", "operation", "read").count())
                .isEqualTo(1.0d);
    }

    @Test
    void quarantineFailureRestoresAlreadyQuarantinedStores() {
        var local = new RecordingObjectStore("local").seed("asset.png", BYTES);
        var s3 = new RecordingObjectStore("s3").seed("asset.png", BYTES);
        s3.failNext(Operation.QUARANTINE);

        assertThatThrownBy(() -> gateway(WebsiteMediaStorageMode.MIRROR, local, s3)
                .quarantineEverywhere(List.of("asset.png"), TRANSACTION_ID))
                .isInstanceOf(WebsiteMediaStorageException.class);
        assertThat(local.exists("asset.png")).isTrue();
        assertThat(s3.exists("asset.png")).isTrue();
    }

    @Test
    void unknownCleanupFailurePreservesObjectsForAudit() {
        var local = new RecordingObjectStore("local");
        var s3 = new RecordingObjectStore("s3");
        s3.failNext(Operation.PUT);
        local.failNext(Operation.DELETE);

        assertThatThrownBy(() -> gateway(WebsiteMediaStorageMode.MIRROR, local, s3)
                .put("asset.png", BYTES, "image/png"))
                .isInstanceOf(WebsiteMediaStorageException.class);
        assertThat(local.exists("asset.png")).isTrue();
        assertThat(local.calls(Operation.DELETE)).isEqualTo(1);
    }

    @Test
    void copyIfAbsentUsesOnlyNamedStoresAndRejectsMismatch() {
        var local = new RecordingObjectStore("local").seed("asset.png", BYTES);
        var s3 = new RecordingObjectStore("s3");
        var gateway = gateway(WebsiteMediaStorageMode.MIRROR, local, s3);

        gateway.copyIfAbsent("local", "s3", "asset.png", "image/png");
        gateway.copyIfAbsent("local", "s3", "asset.png", "image/png");

        assertThat(s3.get("asset.png")).containsExactly(BYTES);
        assertThat(local.calls(Operation.PUT)).isZero();
        s3.seed("other.png", OTHER_BYTES);
        local.seed("other.png", BYTES);
        assertThatThrownBy(() -> gateway.copyIfAbsent("local", "s3", "other.png", "image/png"))
                .isInstanceOf(WebsiteMediaStorageException.class);
    }

    private WebsiteMediaStorageGateway gateway(
            WebsiteMediaStorageMode mode,
            RecordingObjectStore local,
            RecordingObjectStore s3) {
        return new WebsiteMediaStorageGateway(mode, local, Optional.of(s3), new SimpleMeterRegistry());
    }

    private enum Operation {
        PUT, GET, HEAD, LIST, QUARANTINE, RESTORE, PURGE, DELETE
    }

    private static final class RecordingObjectStore implements WebsiteMediaObjectStore {
        private final String name;
        private final Map<String, StoredObject> objects = new HashMap<>();
        private final Map<Operation, Integer> calls = new EnumMap<>(Operation.class);
        private final List<Operation> failures = new ArrayList<>();

        private RecordingObjectStore(String name) {
            this.name = name;
        }

        RecordingObjectStore seed(String key, byte[] bytes) {
            objects.put(key, new StoredObject(bytes.clone(), WebsiteMediaDigest.sha256(bytes)));
            return this;
        }

        void failNext(Operation operation) {
            failures.add(operation);
        }

        boolean exists(String key) {
            return objects.containsKey(key);
        }

        int calls(Operation operation) {
            return calls.getOrDefault(operation, 0);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void put(String key, byte[] bytes, String contentType, String sha256) {
            call(Operation.PUT);
            StoredObject current = objects.get(key);
            if (current != null && !current.sha256().equals(sha256)) throw failure();
            objects.putIfAbsent(key, new StoredObject(bytes.clone(), sha256));
        }

        @Override
        public byte[] get(String key) {
            call(Operation.GET);
            StoredObject value = objects.get(key);
            if (value == null) throw new WebsiteMediaObjectNotFoundException(name, "read");
            return value.bytes().clone();
        }

        @Override
        public Optional<WebsiteMediaObjectMetadata> head(String key) {
            call(Operation.HEAD);
            StoredObject value = objects.get(key);
            return value == null ? Optional.empty() : Optional.of(metadata(key, value));
        }

        @Override
        public List<WebsiteMediaObjectMetadata> list() {
            call(Operation.LIST);
            return objects.entrySet().stream()
                    .filter(entry -> !entry.getKey().startsWith(".trash/"))
                    .map(entry -> metadata(entry.getKey(), entry.getValue()))
                    .toList();
        }

        @Override
        public WebsiteMediaQuarantinedObject quarantine(String key, UUID transactionId) {
            call(Operation.QUARANTINE);
            StoredObject value = objects.remove(key);
            if (value == null) throw new WebsiteMediaObjectNotFoundException(name, "quarantine");
            String quarantineKey = WebsiteMediaStorageKey.quarantineKey(transactionId, key);
            objects.put(quarantineKey, value);
            return new WebsiteMediaQuarantinedObject(name, key, quarantineKey);
        }

        @Override
        public void restore(WebsiteMediaQuarantinedObject object) {
            call(Operation.RESTORE);
            StoredObject value = objects.remove(object.quarantineKey());
            if (value == null) throw new WebsiteMediaObjectNotFoundException(name, "restore");
            objects.put(object.sourceKey(), value);
        }

        @Override
        public void purge(WebsiteMediaQuarantinedObject object) {
            call(Operation.PURGE);
            objects.remove(object.quarantineKey());
        }

        @Override
        public void deleteIfExists(String key) {
            call(Operation.DELETE);
            objects.remove(key);
        }

        private void call(Operation operation) {
            calls.merge(operation, 1, Integer::sum);
            if (failures.remove(operation)) throw failure();
        }

        private WebsiteMediaObjectMetadata metadata(String key, StoredObject value) {
            return new WebsiteMediaObjectMetadata(key, value.bytes().length, Instant.EPOCH, value.sha256());
        }

        private WebsiteMediaStorageException failure() {
            return new WebsiteMediaStorageException("recording store failure");
        }
    }

    private record StoredObject(byte[] bytes, String sha256) {
    }
}
