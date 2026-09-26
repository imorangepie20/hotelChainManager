package team.hotelchain.hotel;

import java.util.UUID;

/**
 * 본사가 객실 유형을 지웠을 때의 결과.
 * <p>
 * 멱원 재호출은 200에 {@code deleted=false}로 같은 결과를 돌려준다.
 * {@code remainingRoomTypes}는 삭제 뒤 남은 객실 유형 수다.
 * 0이면 지점이 빈 상태가 돼서 고객 검색에 나타나지 않는다.
 */
public record RoomTypeDeletionResponse(
        UUID hotelId,
        UUID roomTypeId,
        String name,
        boolean deleted,
        int remainingRoomTypes) {
}
