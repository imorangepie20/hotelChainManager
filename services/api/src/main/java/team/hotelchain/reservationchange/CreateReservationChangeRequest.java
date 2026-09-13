package team.hotelchain.reservationchange;

import java.time.LocalDate;
import java.util.UUID;

public record CreateReservationChangeRequest(
        LocalDate checkIn,
        LocalDate checkOut,
        UUID roomTypeId,
        UUID ratePlanId,
        Long expectedTotal) {
}
