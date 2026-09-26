package team.hotelchain.policy;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;

/** 체크인 현지 시각을 기준으로 적용하는 환불 규칙 한 단계다. */
public record CancellationRefundRule(int daysBefore, String cutoffLocalTime, int refundPercent) {

    public static final Comparator<CancellationRefundRule> EARLIEST_FIRST = Comparator
            .comparingInt(CancellationRefundRule::daysBefore).reversed()
            .thenComparing(CancellationRefundRule::cutoffLocalTime);

    public void validate() {
        if (daysBefore < 0 || daysBefore > 30) {
            throw new IllegalArgumentException("환불 규칙의 기준 일수는 0 이상 30 이하여야 합니다.");
        }
        try {
            if (cutoffLocalTime == null || cutoffLocalTime.length() != 5) throw new DateTimeParseException("", "", 0);
            LocalTime.parse(cutoffLocalTime);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("환불 규칙의 마감 시각은 HH:MM 형식이어야 합니다.");
        }
        if (refundPercent < 0 || refundPercent > 100) {
            throw new IllegalArgumentException("환불률은 0 이상 100 이하여야 합니다.");
        }
    }
}
