package team.hotelchain.payment;

import java.util.UUID;

public final class TossReservationPaymentView {
    private TossReservationPaymentView() { }
    public record CheckoutView(String orderId, long amountKrw, String currency, String clientKey, String successUrl, String failUrl, String environmentLabel) { }
    public record ConfirmPaymentRequest(String orderId, String paymentKey, Long amountKrw) { }
    public record StatusView(UUID reservationId, String orderId, String status, String paymentStatus) { }
}
