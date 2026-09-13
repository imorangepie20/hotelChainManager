package team.hotelchain.reservationchange;

import java.net.URI;
import java.util.UUID;

public interface PaymentAdjustmentGateway {
    GatewayAdjustmentResult createCheckout(GatewayCheckoutCommand command);
    GatewayAdjustmentResult refund(GatewayRefundCommand command);
    GatewayAdjustmentResult query(GatewayQueryCommand command);

    record GatewayCheckoutCommand(
            UUID attemptId,
            String idempotencyKey,
            long amountKrw,
            String currency,
            URI returnUrl) {
    }

    record GatewayRefundCommand(
            UUID attemptId,
            String idempotencyKey,
            String originalGatewayTransactionId,
            long amountKrw,
            String currency) {
    }

    record GatewayQueryCommand(UUID attemptId, String gatewayTransactionId) {
    }

    record GatewayAdjustmentResult(
            String providerEventId,
            String gatewayTransactionId,
            GatewayResultStatus status,
            URI checkoutUrl,
            String errorCode) {
    }

    enum GatewayResultStatus {
        PENDING,
        SUCCEEDED,
        FAILED,
        UNKNOWN
    }
}
