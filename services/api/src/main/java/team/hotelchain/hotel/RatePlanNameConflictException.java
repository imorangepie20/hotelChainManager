package team.hotelchain.hotel;

/**
 * 본사가 이미 같은 객실 유형 안에 있는 이름으로 요금제를 만들거나
 * 바꾸려 할 때 발생한다.
 * <p>
 * 요금제 이름은 유형 안에서 구별되면 충분하므로 다른 유형·다른 지점의
 * 같은 이름은 허용한다. 409로 거부한다.
 */
public class RatePlanNameConflictException extends RuntimeException {

    public RatePlanNameConflictException(String name) {
        super("이미 같은 객실 유형에 '" + name + "' 이름의 요금제가 있습니다.");
    }
}
