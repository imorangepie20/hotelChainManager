package team.hotelchain.audit;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 통합 감사 이력 조회 필터. 모두 선택이고, 없으면 전체 범위를 읽는다.
 * <p>
 * {@code masked}가 true면 고객 이름·이메일·전화번호의 일부를 가려서 내보낸다.
 * 본사가 화면을 공유하거나 화면을 캡처할 때 개인정보가 퍼지는 것을 막기 위해서다.
 */
public record AuditQueryFilters(
        UUID reservationId,
        UUID hotelId,
        LocalDate from,
        LocalDate to,
        boolean masked,
        int limit,
        int offset) {

    public static AuditQueryFilters of(UUID reservationId, UUID hotelId, LocalDate from, LocalDate to,
            Boolean masked, Integer limit, Integer offset) {
        return new AuditQueryFilters(
                reservationId,
                hotelId,
                from,
                to,
                masked != null && masked,
                limit == null ? DEFAULT_LIMIT : limit,
                offset == null ? 0 : offset);
    }

    static final int DEFAULT_LIMIT = 50;
}
