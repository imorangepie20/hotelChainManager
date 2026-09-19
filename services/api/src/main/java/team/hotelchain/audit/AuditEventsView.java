package team.hotelchain.audit;

import java.util.List;

/**
 * 통합 감사 이력 목록. SELECT만 사용하므로 상태를 변경하지 않는다.
 */
public record AuditEventsView(
        List<AuditEventView> events,
        int totalCount,
        int limit,
        int offset) {
}
