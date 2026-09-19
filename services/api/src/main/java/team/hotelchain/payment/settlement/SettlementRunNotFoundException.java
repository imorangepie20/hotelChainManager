package team.hotelchain.payment.settlement;

/** 정산 실행을 찾지 못했다. */
public class SettlementRunNotFoundException extends RuntimeException {
    private final java.util.UUID runId;

    public SettlementRunNotFoundException(java.util.UUID runId) {
        super("정산 실행이 존재하지 않습니다.");
        this.runId = runId;
    }

    public java.util.UUID runId() {
        return runId;
    }
}
