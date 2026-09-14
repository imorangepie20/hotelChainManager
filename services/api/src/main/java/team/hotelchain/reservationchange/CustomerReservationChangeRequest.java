package team.hotelchain.reservationchange;

import java.util.UUID;

public record CustomerReservationChangeRequest(
        UUID quoteId, UUID roomTypeId, UUID ratePlanId, Long expectedTotal) {
}
