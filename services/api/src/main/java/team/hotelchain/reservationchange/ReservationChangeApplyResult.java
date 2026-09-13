package team.hotelchain.reservationchange;

import java.time.Instant;
import java.util.UUID;

public record ReservationChangeApplyResult(
        UUID requestId,
        UUID reservationId,
        long reservationOperationRevision,
        Instant completedAt) {
}
