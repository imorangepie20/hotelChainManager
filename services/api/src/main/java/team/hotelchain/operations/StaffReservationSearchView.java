package team.hotelchain.operations;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record StaffReservationSearchView(
        UUID hotelId,
        LocalDate date,
        boolean truncated,
        List<ReservationSummary> reservations
) {
    public record ReservationSummary(
            UUID reservationId,
            String guestName,
            String guestEmail,
            String roomTypeName,
            String ratePlanName,
            LocalDate checkIn,
            LocalDate checkOut,
            int adults,
            int children,
            int rooms,
            String status,
            long totalKrw,
            String currency,
            List<String> assignedRoomNumbers
    ) {
    }
}
