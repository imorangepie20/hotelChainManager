package team.hotelchain.policy;

/**
 * 본사가 취소 정책을 변경할 때 받는 요청이다.
 * 마감 일수는 1~30, 마감 시각은 {@code HH:MM} 형식. 검증은 서버가 최종 판단한다.
 */
public record CancellationPolicyUpdateRequest(Integer refundCutoffDaysBefore, String refundCutoffLocalTime) {

    public static final int MIN_CUTOFF_DAYS = 1;
    public static final int MAX_CUTOFF_DAYS = 30;
    private static final java.util.regex.Pattern LOCAL_TIME = java.util.regex.Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    public void validate() {
        if (refundCutoffDaysBefore == null
                || refundCutoffDaysBefore < MIN_CUTOFF_DAYS
                || refundCutoffDaysBefore > MAX_CUTOFF_DAYS) {
            throw new IllegalArgumentException(
                    "취소 마감 일수는 " + MIN_CUTOFF_DAYS + " 이상 " + MAX_CUTOFF_DAYS + " 이하여야 합니다.");
        }
        if (refundCutoffLocalTime == null || !LOCAL_TIME.matcher(refundCutoffLocalTime).matches()) {
            throw new IllegalArgumentException("취소 마감 시각은 HH:MM 형식이어야 합니다.");
        }
    }

    public int cutoffDays() {
        return refundCutoffDaysBefore;
    }

    public String cutoffLocalTime() {
        return refundCutoffLocalTime;
    }
}
