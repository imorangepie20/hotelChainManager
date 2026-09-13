package team.hotelchain.webcontent;

import java.util.UUID;

public record WebsitePageMoveImpactItem(UUID pageId, String currentDraftPath, String nextDraftPath,
        String currentPublishedPath, String nextPublishedPath, int depth, boolean published) {
}
