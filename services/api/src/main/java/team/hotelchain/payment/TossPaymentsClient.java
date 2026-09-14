package team.hotelchain.payment;

public interface TossPaymentsClient {
    ProviderPayment confirm(ConfirmCommand command);
    ProviderPayment lookup(String paymentKey);
    record ConfirmCommand(String paymentKey, String orderId, long amountKrw, String currency, String idempotencyKey) { }
    record ProviderPayment(String paymentKey, String orderId, long amountKrw, String currency, ProviderStatus status, String transactionKey, String errorCode) { }
    enum ProviderStatus { DONE, FAILED, UNKNOWN }
}
