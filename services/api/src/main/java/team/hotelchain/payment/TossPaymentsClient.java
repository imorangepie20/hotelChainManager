package team.hotelchain.payment;

public interface TossPaymentsClient {
    ProviderPayment confirm(ConfirmCommand command);

    ProviderPayment lookup(String paymentKey);

    ProviderPayment cancel(CancelCommand command);

    default ProviderPayment lookupCancel(CancelCommand command) {
        return new ProviderPayment(null, null, 0, null, ProviderStatus.UNKNOWN, null, "REFUND_LOOKUP_UNAVAILABLE");
    }

    record ConfirmCommand(String paymentKey, String orderId, long amountKrw, String currency, String idempotencyKey) {
    }

    record CancelCommand(String paymentKey, long amountKrw, String reason, String idempotencyKey) {
    }

    record ProviderPayment(String paymentKey, String orderId, long amountKrw, String currency,
            ProviderStatus status, String transactionKey, String errorCode) {
    }

    enum ProviderStatus {
        DONE, FAILED, UNKNOWN
    }
}
