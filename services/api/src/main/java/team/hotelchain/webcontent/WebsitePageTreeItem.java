package team.hotelchain.webcontent;

import java.util.List;
import java.util.UUID;

public record WebsitePageTreeItem(
        UUID id,
        UUID hotelId,
        String pageType,
        String label,
        String draftPath,
        String publishedPath,
        String status,
        String lifecycleStatus,
        int lifecycleVersion,
        List<WebsitePageTreeItem> children) {
}
