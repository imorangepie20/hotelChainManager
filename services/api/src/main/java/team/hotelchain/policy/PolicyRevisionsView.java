package team.hotelchain.policy;

import java.util.List;

/**
 * 본사가 읽는 정책 변경 이력. SELECT만 사용하므로 상태를 변경하지 않는다.
 */
public record PolicyRevisionsView(
        List<PolicyRevisionView> revisions,
        int totalCount,
        int limit,
        int offset) {
}
