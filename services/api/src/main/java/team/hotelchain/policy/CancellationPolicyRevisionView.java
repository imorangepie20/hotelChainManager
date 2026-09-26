package team.hotelchain.policy;

import java.util.List;
import java.util.UUID;

/** 지점에 마지막으로 기록된 SET 또는 INHERIT revision이다. */
public record CancellationPolicyRevisionView(
        UUID revisionId,
        UUID hotelId,
        String action,
        int revision,
        List<CancellationRefundRule> rules) {

    public CancellationPolicyRevisionView {
        rules = List.copyOf(rules);
    }
}
