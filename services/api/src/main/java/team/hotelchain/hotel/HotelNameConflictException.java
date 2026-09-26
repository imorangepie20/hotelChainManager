package team.hotelchain.hotel;

/**
 * 같은 이름의 지점이 이미 있을 때 던진다.
 * <p>
 * 지점 이름은 고객 웹·관리자 화면에서 지점을 식별하는 라벨이므로 중복을 허용하지 않는다.
 * 삭제하지 않고 이름을 바꿀 수단이 없으므로, 의도하지 않은 중복 생성을 만들지 않는다.
 */
public class HotelNameConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public HotelNameConflictException(String name) {
        super("이미 같은 이름의 지점이 있습니다: " + name);
    }
}
