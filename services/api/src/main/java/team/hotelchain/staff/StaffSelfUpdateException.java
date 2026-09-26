package team.hotelchain.staff;

/**
 * 직원이 본인의 현재 비밀번호를 틀렸을 때 던진다.
 * <p>
 * 본인 계정 수정은 로그인한 세션만 있으면 되므로, 세션을 탈취한 사람이
 * 비밀번호를 바꾸는 것을 막으려면 현재 비밀번호를 다시 물어봐야 한다.
 * 400으로 매핑한다.
 */
public class StaffSelfUpdateException extends RuntimeException {

    public StaffSelfUpdateException() {
        super("현재 비밀번호가 올바르지 않습니다. 다시 확인해 주세요.");
    }
}
