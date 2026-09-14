package team.hotelchain.reservationchange;

import java.time.Instant;
import java.util.UUID;

public record CustomerReservationChangeStartView(
        UUID requestId, String status, String direction, long version, Instant expiresAt) {
}
