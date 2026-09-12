package team.hotelchain.webcontent;

import java.util.Map;

public record SaveWebsitePageRequest(int expectedDraftVersion, WebsitePageDraftMetadata page, Map<String, Object> content,
        WebsitePageConnections connections) {
}
