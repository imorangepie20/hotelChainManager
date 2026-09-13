package team.hotelchain.webcontent.storage;

import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    private WebsiteMediaObjectStore store(String storeName) {
        if (!local.name().equals(storeName)) {
            throw new WebsiteMediaStorageException("알 수 없는 미디어 저장소입니다.");
        }
        return local;
    }
}
