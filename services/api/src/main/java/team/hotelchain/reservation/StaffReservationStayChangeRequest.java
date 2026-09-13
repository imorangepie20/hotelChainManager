package team.hotelchain.reservation;

import java.time.LocalDate;
import java.util.UUID;

public record StaffReservationStayChangeRequest(
        LocalDate checkIn,
        LocalDate checkOut,
        UUID roomTypeId,
        UUID ratePlanId,
        long expectedTotal) {
}
