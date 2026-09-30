package team.hotelchain.webcontent;

import java.util.Map;

public record SaveWebContentRequest(int expectedDraftVersion, Map<String, Object> content,
        WebsitePageDraftMetadata page, WebsitePageConnections connections) {
    public SaveWebContentRequest(int expectedDraftVersion, Map<String, Object> content) {
        this(expectedDraftVersion, content, null, null);
    }

    public SaveWebContentRequest(int expectedDraftVersion, Map<String, Object> content,
            WebsitePageDraftMetadata page) {
        this(expectedDraftVersion, content, page, null);
    }
}
