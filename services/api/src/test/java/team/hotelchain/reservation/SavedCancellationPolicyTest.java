package team.hotelchain.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SavedCancellationPolicyTest {

    private static final LocalDate CHECK_IN = LocalDate.of(2026, 10, 10);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void legacySnapshotKeepsTheSingleFullRefundCutoff() {
        SavedCancellationPolicy policy = SavedCancellationPolicy.fromJson(objectMapper, """
                {"timezone":"Asia/Seoul","refundCutoffDaysBefore":1,"refundCutoffLocalTime":"18:00"}
                """);

        var before = policy.evaluate(CHECK_IN, 200_000,
                Instant.parse("2026-10-09T08:59:59Z"));
        var atCutoff = policy.evaluate(CHECK_IN, 200_000,
                Instant.parse("2026-10-09T09:00:00Z"));

        assertThat(before.cancellable()).isTrue();
        assertThat(before.refundPercent()).isEqualTo(100);
        assertThat(before.refundAmount()).isEqualTo(200_000);
        assertThat(atCutoff.cancellable()).isFalse();
    }

    @Test
    void appliesTheClosestFutureRefundRuleAndRoundsDownToKrw() {
        SavedCancellationPolicy policy = SavedCancellationPolicy.fromJson(objectMapper, """
                {
                  "timezone":"Asia/Seoul",
                  "policyScope":"HOTEL",
                  "refundRules":[
                    {"daysBefore":7,"cutoffLocalTime":"18:00","refundPercent":100},
                    {"daysBefore":3,"cutoffLocalTime":"18:00","refundPercent":50},
                    {"daysBefore":0,"cutoffLocalTime":"12:00","refundPercent":0}
                  ]
                }
                """);

        var full = policy.evaluate(CHECK_IN, 199_999, Instant.parse("2026-10-01T00:00:00Z"));
        var half = policy.evaluate(CHECK_IN, 199_999, Instant.parse("2026-10-04T12:00:00Z"));
        var zero = policy.evaluate(CHECK_IN, 199_999, Instant.parse("2026-10-09T12:00:00Z"));
        var unavailable = policy.evaluate(CHECK_IN, 199_999, Instant.parse("2026-10-10T03:00:00Z"));

        assertThat(full.refundAmount()).isEqualTo(199_999);
        assertThat(half.refundPercent()).isEqualTo(50);
        assertThat(half.refundAmount()).isEqualTo(99_999);
        assertThat(zero.cancellable()).isTrue();
        assertThat(zero.refundAmount()).isZero();
        assertThat(unavailable.cancellable()).isFalse();
    }
}
