package team.hotelchain.webcontent;

import java.util.Map;

public record WebContentDocument(
        Map<String, Object> draftContent,
        int draftVersion,
        Map<String, Object> publishedContent,
        int publishedVersion,
        WebsitePageMetadata draftPage,
        WebsitePageMetadata publishedPage) {
    public WebContentDocument(Map<String, Object> draftContent, int draftVersion,
            Map<String, Object> publishedContent, int publishedVersion) {
        this(draftContent, draftVersion, publishedContent, publishedVersion, null, null);
    }
}
