package team.hotelchain.payment;

import java.util.UUID;

public record PaymentResult(UUID reservationId, String status, String paymentStatus) {
}
