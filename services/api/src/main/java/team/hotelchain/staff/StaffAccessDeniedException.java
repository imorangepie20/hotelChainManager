package team.hotelchain.staff;

public class StaffAccessDeniedException extends RuntimeException {
    public StaffAccessDeniedException() {
        super("이 지점에 접근할 권한이 없습니다.");
    }
}
