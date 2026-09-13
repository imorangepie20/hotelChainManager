package team.hotelchain.webcontent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WebsiteMediaAsset(
        UUID id,
        String displayName,
        String deliveryUrl,
        String mimeType,
        long byteSize,
        int width,
        int height,
        String defaultAltText,
        int usageCount,
        String status,
        int version,
        OffsetDateTime archivedAt,
        OffsetDateTime permanentDeleteAvailableAt) {
}
