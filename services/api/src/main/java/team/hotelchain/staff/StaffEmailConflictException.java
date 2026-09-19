package team.hotelchain.staff;

/**
 * 본사가 직원을 만들 때 같은 이메일이 이미 있으면 409로 응답한다.
 */
public class StaffEmailConflictException extends RuntimeException {

    private final String email;

    public StaffEmailConflictException(String email) {
        super("이미 등록된 이메일입니다.");
        this.email = email;
    }

    public String email() {
        return email;
    }
}
