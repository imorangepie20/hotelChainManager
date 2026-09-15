package team.hotelchain.payment.settlement;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface TossSettlementClient {
    SettlementPage fetch(LocalDate soldDate, int page, int size);

    record SettlementPage(List<SettlementRecord> records, boolean hasNext) {
        public SettlementPage {
            records = List.copyOf(records);
        }
    }

    record SettlementRecord(
            String merchantAccount,
            String paymentKey,
            String transactionKey,
            String orderId,
            String currency,
            String method,
            long amountKrw,
            long feeKrw,
            long feeSupplyKrw,
            long feeVatKrw,
            long payoutKrw,
            Instant approvedAt,
            LocalDate soldDate,
            LocalDate paidOutDate,
            boolean cancellation) {
    }

    final class SettlementException extends RuntimeException {
        private final String code;
        private final boolean retryable;

        public SettlementException(String code, boolean retryable) {
            super(code);
            this.code = code;
            this.retryable = retryable;
        }

        public SettlementException(String code, boolean retryable, Throwable cause) {
            super(code, cause);
            this.code = code;
            this.retryable = retryable;
        }

        public String code() { return code; }
        public boolean retryable() { return retryable; }
    }
}
