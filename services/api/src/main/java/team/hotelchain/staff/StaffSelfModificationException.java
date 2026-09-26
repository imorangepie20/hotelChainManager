package team.hotelchain.staff;

/**
 * 본사가 자기 자신의 역할·소속 지점을 바꾸려 해서 거부됐다.
 * 본사 관리자가 실수로 본인의 권한을 내리거나 지점으로 옮겨
 * 더 이상 본사 메뉴에 들어오지 못하는 것을 막는다.
 */
public class StaffSelfModificationException extends RuntimeException {

    public StaffSelfModificationException() {
        super("본인 계정의 역할·소속 지점은 이 화면에서 바꿀 수 없습니다. 다른 본사 관리자에게 요청해 주세요.");
    }
}
