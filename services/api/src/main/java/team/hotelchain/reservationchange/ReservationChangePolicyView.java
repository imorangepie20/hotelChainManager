package team.hotelchain.reservationchange;

public record ReservationChangePolicyView(
        boolean settlementEnabled,
        long directLimitKrw,
        long approvalTtlSeconds,
        long holdTtlSeconds) {
}
