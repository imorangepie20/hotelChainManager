package team.hotelchain.reservationchange;

public record ReservationChangeReconciliationRequest(
        String action,
        long version,
        String reason) {
}
