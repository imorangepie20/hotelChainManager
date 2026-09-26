package team.hotelchain.policy;

/**
 * 본사가 예약 변경 승인 TTL을 변경할 때 받는 요청이다.
 * <p>
 * 승인 TTL은 본사 승인을 기다릴 수 있는 최대 시간이다. 60초 이상 7일(604,800초) 이하.
 * 검증은 서버가 최종 판단한다.
 */
public record ChangeApprovalTtlUpdateRequest(Integer approvalTtlSeconds) {

    public static final int MIN_TTL_SECONDS = CurrentPolicy.MIN_APPROVAL_TTL_SECONDS;
    public static final int MAX_TTL_SECONDS = CurrentPolicy.MAX_APPROVAL_TTL_SECONDS;

    public void validate() {
        if (approvalTtlSeconds == null
                || approvalTtlSeconds < MIN_TTL_SECONDS
                || approvalTtlSeconds > MAX_TTL_SECONDS) {
            throw new IllegalArgumentException(
                    "예약 변경 승인 TTL은 " + MIN_TTL_SECONDS + "초 이상 "
                            + String.format("%,d", MAX_TTL_SECONDS) + "초 이하여야 합니다.");
        }
    }

    public int ttlSeconds() {
        return approvalTtlSeconds;
    }
}
