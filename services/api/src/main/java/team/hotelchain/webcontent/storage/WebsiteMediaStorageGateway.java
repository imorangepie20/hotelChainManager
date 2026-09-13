package team.hotelchain.webcontent.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WebsiteMediaStorageGateway {
    private final WebsiteMediaObjectStore local;

    public WebsiteMediaStorageGateway(LocalWebsiteMediaObjectStore local) {
        this.local = local;
    }

    public void put(String key, byte[] bytes, String contentType) {
        local.put(key, bytes, contentType, WebsiteMediaDigest.sha256(bytes));
    }

    public byte[] get(String key) {
        return local.get(key);
    }

    public Path materialize(String key, Path workDirectory) {
        try {
            Files.createDirectories(workDirectory);
            Path target = workDirectory.resolve(UUID.randomUUID() + ".media");
            Files.write(target, get(key), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return target;
        } catch (IOException cause) {
            throw new WebsiteMediaStorageException("미디어 작업 파일을 만들지 못했습니다.", cause);
        }
    }

    public void publish(String key, byte[] bytes, String contentType) {
        put(key, bytes, contentType);
    }

    public Optional<WebsiteMediaObjectMetadata> head(String storeName, String key) {
        return store(storeName).head(key);
    }

    public List<WebsiteMediaObjectMetadata> list(String storeName) {
        return store(storeName).list();
    }

    public Set<String> storeNames() {
        return Set.of(local.name());
    }

    public void deleteEverywhere(String key) {
        local.deleteIfExists(key);
    }

    public List<WebsiteMediaQuarantinedObject> quarantineEverywhere(List<String> keys, UUID transactionId) {
        List<WebsiteMediaQuarantinedObject> quarantined = new ArrayList<>();
        try {
            for (String key : keys) quarantined.add(local.quarantine(key, transactionId));
            return List.copyOf(quarantined);
        } catch (RuntimeException cause) {
            Collections.reverse(quarantined);
            for (WebsiteMediaQuarantinedObject object : quarantined) {
                try {
                    local.restore(object);
                } catch (RuntimeException restoreFailure) {
                    cause.addSuppressed(restoreFailure);
                }
            }
            if (cause instanceof WebsiteMediaObjectNotFoundException) {
                throw new WebsiteMediaStorageException("영구 삭제할 미디어 파일을 찾을 수 없습니다.", cause);
            }
            throw cause;
        }
    }

    public void restoreEverywhere(List<WebsiteMediaQuarantinedObject> objects) {
        List<WebsiteMediaQuarantinedObject> reversed = new ArrayList<>(objects);
        Collections.reverse(reversed);
        reversed.forEach(local::restore);
    }

    public void purgeEverywhere(List<WebsiteMediaQuarantinedObject> objects) {
        objects.forEach(local::purge);
    }

    private WebsiteMediaObjectStore store(String storeName) {
        if (!local.name().equals(storeName)) {
            throw new WebsiteMediaStorageException("알 수 없는 미디어 저장소입니다.");
        }
        return local;
    }
}
