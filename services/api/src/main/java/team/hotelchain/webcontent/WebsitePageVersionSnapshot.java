package team.hotelchain.webcontent;

import java.time.Instant;
import java.util.Map;

public record WebsitePageVersionSnapshot(
        int version,
        Instant publishedAt,
        WebsitePageMetadata metadata,
        Integer publishedFromDraftVersion,
        Map<String, Object> content) {
}
