package team.hotelchain.reservationchange;

import java.time.Instant;
import java.util.UUID;

public record ReservationChangePaymentLinkView(
        UUID requestId,
        String status,
        long version,
        String customerUrl,
        Instant createdAt,
        Instant expiresAt) {
}
