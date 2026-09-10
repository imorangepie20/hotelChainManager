package team.hotelchain.staff;

public class StaffAuthenticationException extends RuntimeException {
    public StaffAuthenticationException() {
        super("직원 인증이 필요합니다.");
    }
}
