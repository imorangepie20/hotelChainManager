package team.hotelchain.guestrequest;

import java.time.Instant;
import java.util.UUID;

/**
 * 고객에게 돌려주는 접수 결과. 처리 결과는 이 API가 아닌 호텔의 연락으로 안내한다.
 * created가 false면 같은 멱원 키의 재호출이다.
 */
public record GuestRequestCreateResponse(
        UUID requestId,
        String requestType,
        String subject,
        String status,
        Instant createdAt,
        boolean created) {
}
