package team.hotelchain.reservation;

public class CancellationNotAllowedException extends BusinessConflictException {

    public CancellationNotAllowedException() {
        super("CANCELLATION_NOT_ALLOWED", "무료 취소 가능 시간이 지났습니다.");
    }
}
