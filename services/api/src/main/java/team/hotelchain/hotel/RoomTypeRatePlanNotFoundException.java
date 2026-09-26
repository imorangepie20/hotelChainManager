package team.hotelchain.hotel;

import java.util.UUID;

/**
 * 본사가 객실 유형의 요금제를 바꾸려고 했지만 해당 유형에 요금제가 없을 때
 * 발생한다. 가격을 정할 수 없는 상태이므로 404로 거부한다.
 */
public class RoomTypeRatePlanNotFoundException extends RuntimeException {

    public RoomTypeRatePlanNotFoundException(UUID roomTypeId) {
        super("해당 객실 유형에 등록된 요금제가 없습니다. 객실 유형을 먼저 추가해 주세요.");
    }
}
