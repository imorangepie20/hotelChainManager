package team.hotelchain.webcontent.storage;

import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class WebsiteMediaStorageGateway {
    private final WebsiteMediaStorageMode mode;
    private final WebsiteMediaObjectStore local;
    private final Optional<WebsiteMediaObjectStore> s3;
    private final MeterRegistry meterRegistry;
    private final Map<String, WebsiteMediaObjectStore> stores;

    public WebsiteMediaStorageGateway(
            WebsiteMediaStorageMode mode,
            WebsiteMediaObjectStore local,
            Optional<WebsiteMediaObjectStore> s3,
            MeterRegistry meterRegistry) {
        this.mode = mode == null ? WebsiteMediaStorageMode.LOCAL : mode;
        this.local = requireStore(local, "local");
        this.s3 = s3 == null ? Optional.empty() : s3;
        this.meterRegistry = meterRegistry;
        if (this.mode != WebsiteMediaStorageMode.LOCAL && this.s3.isEmpty()) {
            throw new IllegalStateException("미디어 S3 저장소가 구성되지 않았습니다.");
        }
        Map<String, WebsiteMediaObjectStore> configured = new LinkedHashMap<>();
        configured.put(this.local.name(), this.local);
        this.s3.ifPresent(store -> configured.put(store.name(), store));
        this.stores = Map.copyOf(configured);
    }

    public void put(String key, byte[] bytes, String contentType) {
        writeEverywhere(key, bytes, contentType);
    }

    public byte[] get(String key) {
        return read(key, "read");
    }

    public Path materialize(String key, Path workDirectory) {
        try {
            Files.createDirectories(workDirectory);
            Path target = workDirectory.resolve(UUID.randomUUID() + ".media");
            Files.write(target, read(key, "materialize"), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return target;
        } catch (IOException cause) {
            throw new WebsiteMediaStorageException("미디어 작업 파일을 만들지 못했습니다.", cause);
        }
    }

    public void publish(String key, byte[] bytes, String contentType) {
        writeEverywhere(key, bytes, contentType);
    }

    public Optional<WebsiteMediaObjectMetadata> head(String storeName, String key) {
        return store(storeName).head(key);
    }

    public List<WebsiteMediaObjectMetadata> list(String storeName) {
        return store(storeName).list();
    }

    public Set<String> storeNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        activeStores().forEach(store -> names.add(store.name()));
        return Collections.unmodifiableSet(names);
    }

    public void copyIfAbsent(
            String sourceStoreName,
            String destinationStoreName,
            String key,
            String contentType) {
        WebsiteMediaObjectStore source = store(sourceStoreName);
        WebsiteMediaObjectStore destination = store(destinationStoreName);
        byte[] bytes = source.get(key);
        String digest = WebsiteMediaDigest.sha256(bytes);
        Optional<WebsiteMediaObjectMetadata> existing = destination.head(key);
        if (existing.isPresent()) {
            if (digest.equals(existing.get().sha256())) return;
            throw new WebsiteMediaStorageException("대상 미디어 객체의 체크섬이 다릅니다.");
        }
        destination.put(key, bytes, contentType, digest);
    }

    public void deleteEverywhere(String key) {
        RuntimeException failure = null;
        for (WebsiteMediaObjectStore store : activeStores()) {
            try {
                store.deleteIfExists(key);
            } catch (RuntimeException cause) {
                if (failure == null) failure = cause;
                else failure.addSuppressed(cause);
            }
        }
        if (failure != null) throw failure;
    }

    public List<WebsiteMediaQuarantinedObject> quarantineEverywhere(List<String> keys, UUID transactionId) {
        List<WebsiteMediaQuarantinedObject> quarantined = new ArrayList<>();
        try {
            for (String key : keys) {
                for (WebsiteMediaObjectStore store : activeStores()) {
                    quarantined.add(store.quarantine(key, transactionId));
                }
            }
            return List.copyOf(quarantined);
        } catch (RuntimeException cause) {
            restoreAfterFailure(quarantined, cause);
            if (cause instanceof WebsiteMediaObjectNotFoundException) {
                throw new WebsiteMediaStorageException("영구 삭제할 미디어 파일을 찾을 수 없습니다.", cause);
            }
            throw cause;
        }
    }

    public void restoreEverywhere(List<WebsiteMediaQuarantinedObject> objects) {
        List<WebsiteMediaQuarantinedObject> reversed = new ArrayList<>(objects);
        Collections.reverse(reversed);
        RuntimeException failure = null;
        for (WebsiteMediaQuarantinedObject object : reversed) {
            try {
                store(object.storeName()).restore(object);
            } catch (RuntimeException cause) {
                if (failure == null) failure = cause;
                else failure.addSuppressed(cause);
            }
        }
        if (failure != null) throw failure;
    }

    public void purgeEverywhere(List<WebsiteMediaQuarantinedObject> objects) {
        RuntimeException failure = null;
        for (WebsiteMediaQuarantinedObject object : objects) {
            try {
                store(object.storeName()).purge(object);
            } catch (RuntimeException cause) {
                if (failure == null) failure = cause;
                else failure.addSuppressed(cause);
            }
        }
        if (failure != null) throw failure;
    }

    private void writeEverywhere(String key, byte[] bytes, String contentType) {
        String digest = WebsiteMediaDigest.sha256(bytes);
        List<WebsiteMediaObjectStore> newlyWritten = new ArrayList<>();
        for (WebsiteMediaObjectStore store : writeOrder()) {
            try {
                boolean absent = store.head(key).isEmpty();
                store.put(key, bytes, contentType, digest);
                if (absent) newlyWritten.add(store);
            } catch (RuntimeException cause) {
                increment("media.storage.write.failure", store.name());
                compensate(key, newlyWritten, cause);
                throw cause;
            }
        }
    }

    private byte[] read(String key, String operation) {
        if (mode != WebsiteMediaStorageMode.S3_PRIMARY) return local.get(key);
        WebsiteMediaObjectStore primary = s3.orElseThrow();
        try {
            return primary.get(key);
        } catch (RuntimeException primaryFailure) {
            try {
                byte[] bytes = local.get(key);
                meterRegistry.counter("media.storage.fallback", "operation", operation).increment();
                return bytes;
            } catch (RuntimeException localFailure) {
                primaryFailure.addSuppressed(localFailure);
                throw primaryFailure;
            }
        }
    }

    private void compensate(String key, List<WebsiteMediaObjectStore> newlyWritten, RuntimeException failure) {
        List<WebsiteMediaObjectStore> reversed = new ArrayList<>(newlyWritten);
        Collections.reverse(reversed);
        for (WebsiteMediaObjectStore store : reversed) {
            try {
                store.deleteIfExists(key);
            } catch (RuntimeException cleanupFailure) {
                increment("media.storage.compensation.failure", store.name());
                failure.addSuppressed(cleanupFailure);
            }
        }
    }

    private void restoreAfterFailure(
            List<WebsiteMediaQuarantinedObject> quarantined,
            RuntimeException failure) {
        List<WebsiteMediaQuarantinedObject> reversed = new ArrayList<>(quarantined);
        Collections.reverse(reversed);
        for (WebsiteMediaQuarantinedObject object : reversed) {
            try {
                store(object.storeName()).restore(object);
            } catch (RuntimeException restoreFailure) {
                failure.addSuppressed(restoreFailure);
            }
        }
    }

    private List<WebsiteMediaObjectStore> writeOrder() {
        return switch (mode) {
            case LOCAL -> List.of(local);
            case MIRROR -> List.of(local, s3.orElseThrow());
            case S3_PRIMARY -> List.of(s3.orElseThrow(), local);
        };
    }

    private List<WebsiteMediaObjectStore> activeStores() {
        return writeOrder();
    }

    private WebsiteMediaObjectStore store(String storeName) {
        WebsiteMediaObjectStore store = stores.get(storeName);
        if (store == null || !storeNamesForLookup().contains(storeName)) {
            throw new WebsiteMediaStorageException("알 수 없는 미디어 저장소입니다.");
        }
        return store;
    }

    private Set<String> storeNamesForLookup() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        activeStores().forEach(store -> names.add(store.name()));
        return names;
    }

    private WebsiteMediaObjectStore requireStore(WebsiteMediaObjectStore store, String expectedName) {
        if (store == null || !expectedName.equals(store.name())) {
            throw new IllegalArgumentException("미디어 저장소 구성이 올바르지 않습니다.");
        }
        return store;
    }

    private void increment(String metricName, String storeName) {
        meterRegistry.counter(
                metricName,
                "mode", mode.name().toLowerCase().replace('_', '-'),
                "store", storeName).increment();
    }
}
