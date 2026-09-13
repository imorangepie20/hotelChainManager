package team.hotelchain.reservation;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record StaffReservationStayChangePreview(
        UUID reservationId,
        LocalDate checkIn,
        LocalDate checkOut,
        long currentTotalKrw,
        String currency,
        List<StaffReservationStayChangeOffer> offers) {
}
