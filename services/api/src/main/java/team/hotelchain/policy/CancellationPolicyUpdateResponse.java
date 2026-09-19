package team.hotelchain.policy;

/**
 * 본사가 취소 정책을 변경한 결과.
 * {@code created}가 false면 멱원 재호출로 저장된 revision을 돌려준 것이다.
 */
public record CancellationPolicyUpdateResponse(
        int refundCutoffDaysBefore,
        String refundCutoffLocalTime,
        String timezone,
        int revision,
        boolean created) {
}
