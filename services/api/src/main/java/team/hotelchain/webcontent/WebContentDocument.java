package team.hotelchain.webcontent;

import java.util.Map;

public record WebContentDocument(
        Map<String, Object> draftContent,
        int draftVersion,
        Map<String, Object> publishedContent,
        int publishedVersion,
        WebsitePageMetadata draftPage,
        WebsitePageMetadata publishedPage,
        WebsitePageConnections draftConnections,
        WebsitePageConnections publishedConnections) {
    public WebContentDocument(Map<String, Object> draftContent, int draftVersion,
            Map<String, Object> publishedContent, int publishedVersion) {
        this(draftContent, draftVersion, publishedContent, publishedVersion, null, null,
                WebsitePageConnections.empty(), WebsitePageConnections.empty());
    }

    public WebContentDocument(Map<String, Object> draftContent, int draftVersion,
            Map<String, Object> publishedContent, int publishedVersion,
            WebsitePageMetadata draftPage, WebsitePageMetadata publishedPage) {
        this(draftContent, draftVersion, publishedContent, publishedVersion, draftPage, publishedPage,
                WebsitePageConnections.empty(), WebsitePageConnections.empty());
    }
}
