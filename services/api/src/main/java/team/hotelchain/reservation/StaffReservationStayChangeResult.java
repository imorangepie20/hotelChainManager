package team.hotelchain.reservation;

import java.time.LocalDate;
import java.util.UUID;

public record StaffReservationStayChangeResult(
        UUID reservationId,
        LocalDate checkIn,
        LocalDate checkOut,
        UUID roomTypeId,
        UUID ratePlanId,
        long totalKrw,
        long differenceKrw,
        String currency) {
}
