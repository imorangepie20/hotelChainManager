package team.hotelchain.policy;

import java.util.List;
import java.util.UUID;

/** 예약 생성과 취소 계산이 사용하는 최종 유효 취소 정책이다. */
public record ResolvedCancellationPolicy(
        UUID revisionId,
        String scope,
        UUID hotelId,
        String timezone,
        List<CancellationRefundRule> rules,
        boolean inherited) {

    public ResolvedCancellationPolicy {
        rules = List.copyOf(rules);
    }
}
