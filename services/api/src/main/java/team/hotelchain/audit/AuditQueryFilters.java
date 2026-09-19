package team.hotelchain.audit;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 통합 감사 이력 조회 필터. 모두 선택이고, 없으면 전체 범위를 읽는다.
 */
public record AuditQueryFilters(
        UUID reservationId,
        UUID hotelId,
        LocalDate from,
        LocalDate to,
        int limit,
        int offset) {

    public static AuditQueryFilters of(UUID reservationId, UUID hotelId, LocalDate from, LocalDate to,
            Integer limit, Integer offset) {
        return new AuditQueryFilters(
                reservationId,
                hotelId,
                from,
                to,
                limit == null ? DEFAULT_LIMIT : limit,
                offset == null ? 0 : offset);
    }

    static final int DEFAULT_LIMIT = 50;
}
