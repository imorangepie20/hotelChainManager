package team.hotelchain.reservation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import team.hotelchain.policy.CancellationRefundRule;

/** 예약 생성 시 고정한 취소·환불 정책을 해석하고 환불액을 계산한다. */
record SavedCancellationPolicy(
        String timezone,
        List<CancellationRefundRule> rules,
        UUID revisionId,
        String scope) {

    static SavedCancellationPolicy fromJson(ObjectMapper objectMapper, String snapshot) {
        try {
            JsonNode root = objectMapper.readTree(snapshot);
            String timezone = text(root, "timezone", "Asia/Seoul");
            List<CancellationRefundRule> rules = new ArrayList<>();
            JsonNode savedRules = root.path("refundRules");
            if (savedRules.isArray()) {
                for (JsonNode rule : savedRules) {
                    rules.add(new CancellationRefundRule(
                            rule.path("daysBefore").asInt(),
                            text(rule, "cutoffLocalTime", "18:00"),
                            rule.path("refundPercent").asInt()));
                }
            }
            if (rules.isEmpty()) {
                rules.add(new CancellationRefundRule(
                        root.path("refundCutoffDaysBefore").asInt(1),
                        text(root, "refundCutoffLocalTime", "18:00"),
                        100));
            }
            UUID revisionId = root.path("cancellationPolicyRevisionId").isTextual()
                    ? UUID.fromString(root.path("cancellationPolicyRevisionId").asText())
                    : null;
            return new SavedCancellationPolicy(
                    timezone,
                    List.copyOf(rules),
                    revisionId,
                    text(root, "policyScope", "LEGACY"));
        } catch (Exception exception) {
            throw new IllegalStateException("저장된 취소 정책을 해석할 수 없습니다.", exception);
        }
    }

    RefundDecision evaluate(LocalDate checkIn, long totalKrw, Instant now) {
        ZoneId zone = ZoneId.of(timezone);
        return rules.stream()
                .map(rule -> new RuleCutoff(rule, LocalDateTime.of(
                        checkIn.minusDays(rule.daysBefore()), LocalTime.parse(rule.cutoffLocalTime()))
                        .atZone(zone).toInstant()))
                .filter(candidate -> now.isBefore(candidate.cutoff()))
                .min(Comparator.comparing(RuleCutoff::cutoff))
                .map(candidate -> new RefundDecision(
                        true,
                        refundAmount(totalKrw, candidate.rule().refundPercent()),
                        candidate.rule().refundPercent(),
                        candidate.cutoff()))
                .orElseGet(() -> new RefundDecision(false, 0, 0, lastCutoff(checkIn, zone)));
    }

    CancellationRefundRule fullRefundRule() {
        return rules.stream()
                .filter(rule -> rule.refundPercent() == 100)
                .min(Comparator.comparingInt(CancellationRefundRule::daysBefore)
                        .thenComparing(CancellationRefundRule::cutoffLocalTime, Comparator.reverseOrder()))
                .orElse(rules.getFirst());
    }

    private Instant lastCutoff(LocalDate checkIn, ZoneId zone) {
        return rules.stream()
                .map(rule -> LocalDateTime.of(checkIn.minusDays(rule.daysBefore()),
                                LocalTime.parse(rule.cutoffLocalTime()))
                        .atZone(zone).toInstant())
                .max(Comparator.naturalOrder())
                .orElse(checkIn.atStartOfDay(zone).toInstant());
    }

    private static long refundAmount(long totalKrw, int percent) {
        return BigDecimal.valueOf(totalKrw)
                .multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                .longValueExact();
    }

    private static String text(JsonNode root, String field, String fallback) {
        JsonNode value = root.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : fallback;
    }

    record RefundDecision(boolean cancellable, long refundAmount, int refundPercent, Instant cutoffAt) {}

    private record RuleCutoff(CancellationRefundRule rule, Instant cutoff) {}
}
