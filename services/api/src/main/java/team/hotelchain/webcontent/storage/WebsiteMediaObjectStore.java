package team.hotelchain.webcontent.storage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebsiteMediaObjectStore {
    String name();

    void put(String key, byte[] bytes, String contentType, String sha256);

    byte[] get(String key);

    Optional<WebsiteMediaObjectMetadata> head(String key);

    List<WebsiteMediaObjectMetadata> list();

    WebsiteMediaQuarantinedObject quarantine(String key, UUID transactionId);

    void restore(WebsiteMediaQuarantinedObject object);

    void purge(WebsiteMediaQuarantinedObject object);

    void deleteIfExists(String key);
}
