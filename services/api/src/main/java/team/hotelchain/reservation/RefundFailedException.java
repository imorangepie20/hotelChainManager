package team.hotelchain.reservation;

public class RefundFailedException extends BusinessConflictException {

    public RefundFailedException() {
        super("REFUND_FAILED", "테스트 환불에 실패했습니다. 예약은 확정 상태로 유지됩니다.");
    }
}
