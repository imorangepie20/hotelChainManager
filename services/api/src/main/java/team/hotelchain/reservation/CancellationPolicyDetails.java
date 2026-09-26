package team.hotelchain.reservation;

import java.util.List;
import java.util.UUID;

import team.hotelchain.policy.CancellationRefundRule;

public record CancellationPolicyDetails(
        int refundCutoffDaysBefore,
        String refundCutoffLocalTime,
        String timezone,
        List<CancellationRefundRule> refundRules,
        UUID revisionId,
        String scope) {

    public CancellationPolicyDetails(int refundCutoffDaysBefore, String refundCutoffLocalTime, String timezone) {
        this(refundCutoffDaysBefore, refundCutoffLocalTime, timezone,
                List.of(new CancellationRefundRule(refundCutoffDaysBefore, refundCutoffLocalTime, 100)),
                null, "LEGACY");
    }
}
