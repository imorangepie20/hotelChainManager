package team.hotelchain.staff;

/**
 * 본사가 임시 비밀번호를 연속으로 재발급하려고 할 때 던진다.
 * 응답 유실 뒤 새 멱원 키로 재시도하는 동안 비밀번호가 계속 바뀌는 것을 막는다.
 */
public class StaffPasswordCooldownException extends RuntimeException {

    private final long remainingSeconds;

    public StaffPasswordCooldownException(long remainingSeconds) {
        super("직원 임시 비밀번호 재발급은 " + remainingSeconds + "초가 지난 뒤에 다시 가능합니다.");
        this.remainingSeconds = remainingSeconds;
    }

    public long remainingSeconds() {
        return remainingSeconds;
    }
}
