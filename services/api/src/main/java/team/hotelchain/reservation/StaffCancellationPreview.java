package team.hotelchain.reservation;

import java.time.Instant;
import java.util.UUID;

public record StaffCancellationPreview(
        UUID reservationId,
        String status,
        boolean cancellable,
        long refundAmount,
        int refundPercent,
        String currency,
        Instant cutoffAt,
        String unavailableReason
) {
}
