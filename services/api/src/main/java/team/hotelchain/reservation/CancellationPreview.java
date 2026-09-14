package team.hotelchain.reservation;

import java.time.Instant;
import java.util.UUID;

public record CancellationPreview(
        UUID reservationId,
        String status,
        boolean cancellable,
        long refundAmount,
        String currency,
        Instant cutoffAt,
        String timezone,
        String unavailableReason) {
}
