package team.hotelchain.webcontent;

import java.time.Instant;
import java.util.UUID;

public record WebsitePreviewGrantResponse(
        UUID grantId,
        String previewToken,
        String previewPath,
        Instant expiresAt) {
}
