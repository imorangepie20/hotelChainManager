package team.hotelchain.hotel;

import java.util.UUID;

/**
 * 지점 판매 상태 전환 결과. {@code changed}가 {@code false}면 멱원 재호출로
 * 같은 결과를 돌려주는 것이다.
 */
public record HotelActivationResponse(
        UUID hotelId,
        String name,
        String region,
        String timezone,
        boolean active,
        boolean changed) {
}
