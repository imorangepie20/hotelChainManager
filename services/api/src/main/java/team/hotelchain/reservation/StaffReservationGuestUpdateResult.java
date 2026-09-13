package team.hotelchain.reservation;

import java.util.UUID;

public record StaffReservationGuestUpdateResult(UUID reservationId, String guestName, String guestEmail) {
}
