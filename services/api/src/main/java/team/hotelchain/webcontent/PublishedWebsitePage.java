package team.hotelchain.webcontent;

import java.util.Map;
import java.util.UUID;

public record PublishedWebsitePage(UUID id, String type, ContentKind contentKind, String path, UUID hotelId,
        Map<String, Object> content, WebsitePageConnections connections) {
}
