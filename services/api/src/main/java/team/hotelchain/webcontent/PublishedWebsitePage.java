package team.hotelchain.webcontent;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PublishedWebsitePage(UUID id, String type, ContentKind contentKind, String path, UUID hotelId,
        Map<String, Object> content, WebsitePageConnections connections,
        Map<UUID, List<PublicWebsiteMediaVariant>> mediaVariants) {

    public PublishedWebsitePage(UUID id, String type, ContentKind contentKind, String path, UUID hotelId,
            Map<String, Object> content, WebsitePageConnections connections) {
        this(id, type, contentKind, path, hotelId, content, connections, Map.of());
    }
}
