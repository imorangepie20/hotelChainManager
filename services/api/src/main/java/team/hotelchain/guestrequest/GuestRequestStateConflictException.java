package team.hotelchain.guestrequest;

/**
 * 고객 요청을 진행 중으로 바꾸거나 완료·종료할 때 이미 같은 상태면 발생한다.
 */
public class GuestRequestStateConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GuestRequestStateConflictException(String message) {
        super(message);
    }
}
