package team.hotelchain.webcontent;

import java.util.UUID;

public record WebsiteMediaDraftReplacementTarget(
        UUID pageId,
        String locale,
        String fieldPath,
        int expectedDraftVersion) {
}
