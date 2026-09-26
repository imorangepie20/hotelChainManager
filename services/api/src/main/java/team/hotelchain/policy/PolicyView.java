package team.hotelchain.policy;

/**
 * 본사가 조회하는 체인 공통 정책의 현재값.
 * <p>
 * 취소 정책은 본사가 변경한다. 예약 변경 승인 한도와 승인 TTL은 진행 중인 승인에 미치는 영향이 커서
 * 읽기 전용으로만 노출한다.
 */
public record PolicyView(
        CancellationPolicy cancellation,
        long changeApprovalDirectLimitKrw,
        long changeApprovalTtlSeconds,
        boolean changeSettlementEnabled,
        int revision) {
}
