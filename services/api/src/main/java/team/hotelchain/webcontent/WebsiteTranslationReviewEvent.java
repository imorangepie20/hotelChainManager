package team.hotelchain.webcontent;

import java.time.Instant;
import java.util.UUID;

public record WebsiteTranslationReviewEvent(
        long id,
        String action,
        int draftVersion,
        UUID actorId,
        String actorDisplayName,
        Instant createdAt,
        String comment) {
}
