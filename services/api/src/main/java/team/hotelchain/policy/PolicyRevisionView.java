package team.hotelchain.policy;

/**
 * 본사가 조회하는 정책 변경 이력의 한 건.
 * 원본 요청은 노출하지 않고 본사가 파악할 요약만 만든다.
 */
public record PolicyRevisionView(
        String key,
        String summary,
        String staffEmail,
        String staffDisplayName,
        String staffRole,
        String createdAt) {
}
