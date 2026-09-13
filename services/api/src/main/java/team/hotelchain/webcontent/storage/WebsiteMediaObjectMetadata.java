package team.hotelchain.webcontent.storage;

import java.time.Instant;

public record WebsiteMediaObjectMetadata(String key, long byteSize, Instant lastModified, String sha256) {
}
