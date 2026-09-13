package team.hotelchain.reservation;

import java.util.UUID;

public record StaffReservationPartyUpdateResult(UUID reservationId, int adults, int children) {
}
