package team.hotelchain.reservationchange;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("${reservation.change.settlement-enabled:false} and '${reservation.change.gateway:disabled}' == 'fake'")
public class FakePaymentAdjustmentGateway implements PaymentAdjustmentGateway {
    @Override
    public GatewayAdjustmentResult createCheckout(GatewayCheckoutCommand command) {
        return new GatewayAdjustmentResult(
                null,
                "fake-checkout-" + command.attemptId(),
                GatewayResultStatus.PENDING,
                URI.create("http://localhost:4080/api/test/reservation-change-payments/" + command.attemptId()),
                null);
    }

    @Override
    public GatewayAdjustmentResult refund(GatewayRefundCommand command) {
        return new GatewayAdjustmentResult(
                "fake-refund-" + command.attemptId(),
                "fake-refund-transaction-" + command.attemptId(),
                GatewayResultStatus.SUCCEEDED,
                null,
                null);
    }

    @Override
    public GatewayAdjustmentResult query(GatewayQueryCommand command) {
        return new GatewayAdjustmentResult(
                "fake-query-" + command.attemptId(),
                command.gatewayTransactionId(),
                GatewayResultStatus.UNKNOWN,
                null,
                "FAKE_RESULT_UNKNOWN");
    }
}
