package team.hotelchain.webcontent;

import java.util.List;

public record WebsiteMediaDraftReplacementImpact(
        WebsiteMediaAsset sourceAsset,
        WebsiteMediaAsset targetAsset,
        List<WebsiteMediaDraftReplacementUsage> replaceableUsages,
        int publishedUsageCount,
        int archivedDraftUsageCount) {
}
