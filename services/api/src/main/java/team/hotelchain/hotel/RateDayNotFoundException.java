package team.hotelchain.hotel;

/**
 * 본사가 가격을 바꾸려는 일자의 요금 행이 없다.
 * <p>
 * {@code rate_day}는 객실 유형을 만들 때 90일분만 심으므로, 그 범위를
 * 벗어난 날짜는 가격을 정할 수 없다. 가격을 새로 만드는 것이 아니라
 * 이미 판매 중인 가격을 바꾸는 것이기 때문에 404로 거부한다.
 */
public class RateDayNotFoundException extends RuntimeException {

    public RateDayNotFoundException() {
        super("해당 날짜의 요금이 없습니다. 객실 유형을 만들 때 심어둔 기간 안에서 바꿀 수 있습니다.");
    }
}
