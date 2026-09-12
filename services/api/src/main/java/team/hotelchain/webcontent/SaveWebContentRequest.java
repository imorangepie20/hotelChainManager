package team.hotelchain.webcontent;

import java.util.Map;

public record SaveWebContentRequest(int expectedDraftVersion, Map<String, Object> content,
        WebsitePageDraftMetadata page) {
    public SaveWebContentRequest(int expectedDraftVersion, Map<String, Object> content) {
        this(expectedDraftVersion, content, null);
    }
}
