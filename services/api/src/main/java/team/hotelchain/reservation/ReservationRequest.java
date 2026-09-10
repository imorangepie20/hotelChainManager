package team.hotelchain.reservation;

import java.time.LocalDate;
import java.util.UUID;

public record ReservationRequest(
        UUID roomTypeId,
        UUID ratePlanId,
        LocalDate checkIn,
        LocalDate checkOut,
        int adults,
        int children,
        int rooms,
        long expectedTotal,
        ReservationGuest guest) {
}
