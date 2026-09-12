package team.hotelchain.webcontent;

import java.util.Map;
import java.util.UUID;

public record WebsitePageDocument(
        UUID id,
        String pageType,
        ContentKind contentKind,
        UUID hotelId,
        Map<String, Object> draftContent,
        WebsitePageConnections draftConnections,
        int draftVersion,
        WebsitePageMetadata draftMetadata,
        Map<String, Object> publishedContent,
        WebsitePageConnections publishedConnections,
        int publishedVersion,
        WebsitePageMetadata publishedMetadata,
        String lifecycleStatus,
        int lifecycleVersion) {
}
