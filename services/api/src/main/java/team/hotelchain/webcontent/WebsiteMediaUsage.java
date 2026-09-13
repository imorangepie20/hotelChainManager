package team.hotelchain.webcontent;

import java.util.UUID;

public record WebsiteMediaUsage(
        UUID pageId,
        String pageLabel,
        String pagePath,
        String pageType,
        String documentState,
        String fieldPath,
        String altText,
        String locale) {
}
