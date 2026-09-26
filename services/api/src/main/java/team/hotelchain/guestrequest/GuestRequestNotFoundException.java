package team.hotelchain.guestrequest;

/**
 * 본사·지점 직원이 고객 요청을 찾지 못했을 때 발생한다.
 */
public class GuestRequestNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GuestRequestNotFoundException(java.util.UUID requestId) {
        super("고객 요청을 찾을 수 없습니다. 목록을 새로고침해 주세요. id=" + requestId);
    }
}
