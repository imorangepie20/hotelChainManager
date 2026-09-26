package team.hotelchain.policy;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** 기존 단일 마감 필드를 보존하면서 규칙 전체를 함께 내리는 API 표현이다. */
public record CancellationPolicyView(
        int refundCutoffDaysBefore,
        String refundCutoffLocalTime,
        String timezone,
        UUID revisionId,
        String scope,
        UUID hotelId,
        List<CancellationRefundRule> rules,
        boolean inherited) {

    static CancellationPolicyView from(ResolvedCancellationPolicy policy) {
        CancellationRefundRule fullRefund = policy.rules().stream()
                .filter(rule -> rule.refundPercent() == 100)
                .min(Comparator.comparingInt(CancellationRefundRule::daysBefore)
                        .thenComparing(CancellationRefundRule::cutoffLocalTime, Comparator.reverseOrder()))
                .orElse(policy.rules().getFirst());
        return new CancellationPolicyView(fullRefund.daysBefore(), fullRefund.cutoffLocalTime(), policy.timezone(),
                policy.revisionId(), policy.scope(), policy.hotelId(), policy.rules(), policy.inherited());
    }
}
