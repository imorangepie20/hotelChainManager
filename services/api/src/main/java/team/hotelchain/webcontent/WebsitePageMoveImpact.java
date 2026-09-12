package team.hotelchain.webcontent;

import java.util.List;
import java.util.UUID;

public record WebsitePageMoveImpact(UUID pageId, UUID newParentId, String newRootDraftPath,
        List<WebsitePageMoveImpactItem> items) {
}
