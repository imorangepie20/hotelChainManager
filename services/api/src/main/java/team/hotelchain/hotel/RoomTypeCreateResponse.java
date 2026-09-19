package team.hotelchain.hotel;

import java.util.UUID;

/**
 * 객실 유형 생성 결과. 멱원 재호출은 여기에 저장된 roomTypeId를 그대로 돌려준다.
 * created가 false면 이미 처리된 요청의 결과를 다시 보낸 것이다.
 */
public record RoomTypeCreateResponse(
        UUID roomTypeId,
        UUID hotelId,
        String name,
        int maxOccupancy,
        boolean created) {
}
