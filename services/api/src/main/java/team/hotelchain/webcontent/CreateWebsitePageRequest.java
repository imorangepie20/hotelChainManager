package team.hotelchain.webcontent;

import java.util.Map;
import java.util.UUID;

public record CreateWebsitePageRequest(UUID parentId, ContentKind contentKind, UUID hotelId,
        WebsitePageDraftMetadata page, Map<String, Object> content, WebsitePageConnections connections) {
}
