package team.hotelchain.webcontent;

import java.util.UUID;

public record WebsiteMediaDraftReplacementUsage(
        UUID pageId,
        String pageLabel,
        String pagePath,
        String pageType,
        String locale,
        String fieldPath,
        int expectedDraftVersion) {
}
