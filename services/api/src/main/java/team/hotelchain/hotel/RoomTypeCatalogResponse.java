package team.hotelchain.hotel;

import java.util.List;
import java.util.UUID;

/**
 * 본사가 객실 유형과 요금제 카탈로그를 읽기 전용으로 확인한다.
 * SELECT만 사용하고 가격·재고·예약 상태를 변경하지 않는다.
 */
public record RoomTypeCatalogResponse(
        UUID hotelId,
        int totalCount,
        List<RoomTypeCatalogView> roomTypes) {
}
