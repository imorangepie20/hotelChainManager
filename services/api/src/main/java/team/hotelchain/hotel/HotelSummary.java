package team.hotelchain.hotel;

import java.util.UUID;

/**
 * 지점 요약. {@code active}는 직원 화면에서만 쓴다.
 * <p>
 * 고객 {@code GET /api/hotels}는 중지한 지점을 아예 빼므로 이 레코드에 도달하지 않는다.
 * 직원 {@code GET /api/staff/hotels}는 중지한 지점도 같이 내보내서 다시 판매할 수 있게 한다.
 */
public record HotelSummary(UUID id, String name, String region, String timezone, boolean active) {
}
