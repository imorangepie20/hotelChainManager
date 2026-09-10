package team.hotelchain.reservation;

import java.util.UUID;

public record CancellationResult(UUID reservationId, String status, long refundAmount, String currency) {
}
