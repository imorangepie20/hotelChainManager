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
        boolean created,
        java.util.UUID revisionId,
        String scope,
        java.util.UUID hotelId,
        java.util.List<CancellationRefundRule> rules,
        boolean inherited) {

    public CancellationPolicyUpdateResponse(
            int refundCutoffDaysBefore,
            String refundCutoffLocalTime,
            String timezone,
            int revision,
            boolean created) {
        this(refundCutoffDaysBefore, refundCutoffLocalTime, timezone, revision, created, null, "CHAIN", null,
                java.util.List.of(new CancellationRefundRule(
                        refundCutoffDaysBefore, refundCutoffLocalTime, 100)), false);
    }

    static CancellationPolicyUpdateResponse from(
            ResolvedCancellationPolicy policy, int revision, boolean created) {
        CancellationPolicyView view = CancellationPolicyView.from(policy);
        return new CancellationPolicyUpdateResponse(view.refundCutoffDaysBefore(), view.refundCutoffLocalTime(),
                view.timezone(), revision, created, view.revisionId(), view.scope(), view.hotelId(), view.rules(),
                view.inherited());
    }
}
