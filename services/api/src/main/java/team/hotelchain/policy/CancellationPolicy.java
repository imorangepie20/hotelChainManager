package team.hotelchain.policy;

/**
 * 취소 정책의 현재값. 예약 생성 시 {@code policy_snapshot}에 복사된다.
 */
public record CancellationPolicy(int refundCutoffDaysBefore, String refundCutoffLocalTime, String timezone) {
}
