package team.hotelchain.payment.settlement;

/** 실패한 정산 실행만 재시도할 수 있다. */
public class SettlementNotRetryableException extends RuntimeException {
    private final String currentStatus;

    public SettlementNotRetryableException(String currentStatus) {
        super("실패한 정산 실행만 다시 실행할 수 있습니다. 현재 상태: " + currentStatus);
        this.currentStatus = currentStatus;
    }

    public String currentStatus() {
        return currentStatus;
    }
}
