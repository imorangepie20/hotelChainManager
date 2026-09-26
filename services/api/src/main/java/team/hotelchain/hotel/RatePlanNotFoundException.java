package team.hotelchain.hotel;

import java.util.UUID;

/**
 * 본사가 금액을 심거나 바꾸려는 요금제가 객실 유형에 속하지 않을 때
 * 발생한다. 다른 유형의 요금제를 바꾸는 것을 막기 위해 404로 거부한다.
 */
public class RatePlanNotFoundException extends RuntimeException {

    public RatePlanNotFoundException(UUID ratePlanId) {
        super("해당 객실 유형에 등록된 요금제가 없습니다. 요금제를 먼저 추가해 주세요.");
    }
}
