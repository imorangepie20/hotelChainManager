package team.hotelchain.policy;

/**
 * 본사가 예약 변경 승인 한도를 변경할 때 받는 요청이다.
 * 0원 이상 10,000,000원 이하. 검증은 서버가 최종 판단한다.
 */
public record ChangeApprovalLimitUpdateRequest(Long directLimitKrw) {

    public static final long MIN_LIMIT_KRW = 0L;
    public static final long MAX_LIMIT_KRW = 10_000_000L;

    public void validate() {
        if (directLimitKrw == null || directLimitKrw < MIN_LIMIT_KRW || directLimitKrw > MAX_LIMIT_KRW) {
            throw new IllegalArgumentException(
                    "예약 변경 승인 한도는 " + MIN_LIMIT_KRW + "원 이상 "
                            + String.format("%,d", MAX_LIMIT_KRW) + "원 이하여야 합니다.");
        }
    }

    public long limitKrw() {
        return directLimitKrw;
    }
}
