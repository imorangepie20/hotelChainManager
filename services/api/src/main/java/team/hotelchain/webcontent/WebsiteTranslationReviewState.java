package team.hotelchain.webcontent;

import java.util.List;

public record WebsiteTranslationReviewState(
        WebsiteTranslationReviewStatus status,
        Integer reviewedDraftVersion,
        List<WebsiteTranslationReviewEvent> events) {
}
