package team.hotelchain.webcontent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WebsiteMediaVariant(
        UUID id,
        String format,
        int targetWidth,
        String status,
        String deliveryUrl,
        String mimeType,
        Long byteSize,
        Integer width,
        Integer height,
        int attemptCount,
        String lastError,
        OffsetDateTime updatedAt) {
}
