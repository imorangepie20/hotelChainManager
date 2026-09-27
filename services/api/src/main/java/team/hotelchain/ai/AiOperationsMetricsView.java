package team.hotelchain.ai;

import java.util.List;
import java.util.Map;

public record AiOperationsMetricsView(
        String timezone,
        String period,
        String bucket,
        String fromInclusive,
        String toExclusive,
        int retentionDays,
        List<String> models,
        Totals totals,
        Map<String, Outcome> outcomes,
        List<SeriesPoint> series) {

    public record Totals(long calls, double avgElapsedMs, long policyViolations) {
    }

    public record Outcome(long count, double totalElapsedMs, double avgElapsedMs) {
    }

    public record SeriesPoint(
            String startAt,
            String endAt,
            long calls,
            double avgElapsedMs,
            long policyViolations,
            Map<String, Outcome> outcomes) {
    }
}
