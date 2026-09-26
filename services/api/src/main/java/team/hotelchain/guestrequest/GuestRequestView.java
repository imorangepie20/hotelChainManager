package team.hotelchain.guestrequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 본사·지점 직원이 읽는 고객 요청 1건과 처리 이력.
 * <p>
 * 고객이 남긴 연락처는 요청을 처리할 때 필요하므로 노출한다. 화면 공유 시 개인정보가
 * 퍼지는 것은 관리자 화면의 마스킹 옵션이 담당한다(7번 감사 메뉴와 같은 규칙).
 */
public record GuestRequestView(
        UUID id,
        UUID hotelId,
        String hotelName,
        UUID reservationId,
        String requestType,
        String subject,
        String body,
        String guestName,
        String guestEmail,
        String guestPhone,
        String status,
        String priority,
        UUID assignedTo,
        String assignedDisplayName,
        Instant createdAt,
        Instant updatedAt,
        List<EventView> events) {

    public record EventView(
            UUID id,
            String eventType,
            String fromStatus,
            String toStatus,
            String actorDisplayName,
            String note,
            Instant createdAt) {
    }
}
