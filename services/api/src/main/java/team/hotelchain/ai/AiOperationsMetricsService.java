package team.hotelchain.ai;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.staff.StaffAccessService;

@Service
public class AiOperationsMetricsService {

    static final int RETENTION_DAYS = 90;
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final List<String> OUTCOMES = List.of(
            "success", "schema_rejected", "unparsable", "empty_response", "api_error", "no_key");

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;
    private final Clock clock;

    public AiOperationsMetricsService(JdbcTemplate jdbc, StaffAccessService access, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AiOperationsMetricsView metrics(String token, String requestedPeriod) {
        access.requireHeadquarters(token);
        Period period = Period.parse(requestedPeriod);
        Bounds bounds = bounds(period);

        Map<Instant, BucketTotals> buckets = emptyBuckets(period, bounds.from(), bounds.to());
        Set<String> models = new LinkedHashSet<>();
        BucketTotals overall = new BucketTotals();

        String truncation = period == Period.H24 ? "hour" : "day";
        jdbc.query("""
                select date_trunc(?, occurred_at at time zone ?) at time zone ? as bucket_start,
                       event_type, model, outcome,
                       count(*) as event_count,
                       coalesce(sum(elapsed_ms), 0) as elapsed_total
                  from ai_telemetry_event
                 where occurred_at >= ? and occurred_at < ?
                 group by bucket_start, event_type, model, outcome
                 order by bucket_start
                """, rs -> {
                    Instant start = rs.getTimestamp("bucket_start").toInstant();
                    BucketTotals bucket = buckets.get(start);
                    if (bucket == null) return;
                    long count = rs.getLong("event_count");
                    if ("POLICY_VIOLATION".equals(rs.getString("event_type"))) {
                        bucket.policyViolations += count;
                        overall.policyViolations += count;
                        return;
                    }
                    String model = rs.getString("model");
                    if (model != null) models.add(model);
                    String outcome = rs.getString("outcome");
                    double elapsed = rs.getDouble("elapsed_total");
                    bucket.add(outcome, count, elapsed);
                    overall.add(outcome, count, elapsed);
                }, truncation, ZONE.getId(), ZONE.getId(),
                Timestamp.from(bounds.from()), Timestamp.from(bounds.to()));

        List<AiOperationsMetricsView.SeriesPoint> series = new ArrayList<>();
        for (Map.Entry<Instant, BucketTotals> entry : buckets.entrySet()) {
            Instant end = period == Period.H24
                    ? entry.getKey().plus(1, ChronoUnit.HOURS)
                    : entry.getKey().atZone(ZONE).plusDays(1).toInstant();
            series.add(entry.getValue().view(entry.getKey(), end));
        }
        List<String> sortedModels = models.stream().sorted(Comparator.naturalOrder()).toList();
        return new AiOperationsMetricsView(
                ZONE.getId(), period.apiName, period.bucketName,
                bounds.from().toString(), bounds.to().toString(), RETENTION_DAYS,
                sortedModels,
                new AiOperationsMetricsView.Totals(
                        overall.calls, average(overall.elapsedMs, overall.calls), overall.policyViolations),
                overall.outcomeViews(), series);
    }

    private Bounds bounds(Period period) {
        ZonedDateTime localNow = clock.instant().atZone(ZONE);
        if (period == Period.H24) {
            Instant to = localNow.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant();
            return new Bounds(to.minus(24, ChronoUnit.HOURS), to);
        }
        Instant to = localNow.toLocalDate().plusDays(1).atStartOfDay(ZONE).toInstant();
        return new Bounds(to.minus(period.days, ChronoUnit.DAYS), to);
    }

    private Map<Instant, BucketTotals> emptyBuckets(Period period, Instant from, Instant to) {
        Map<Instant, BucketTotals> buckets = new LinkedHashMap<>();
        Instant cursor = from;
        while (cursor.isBefore(to)) {
            buckets.put(cursor, new BucketTotals());
            cursor = period == Period.H24
                    ? cursor.plus(1, ChronoUnit.HOURS)
                    : cursor.atZone(ZONE).plusDays(1).toInstant();
        }
        return buckets;
    }

    private static double average(double total, long count) {
        return count == 0 ? 0d : round(total / count);
    }

    private static double round(double value) {
        return Math.round(value * 100d) / 100d;
    }

    private enum Period {
        H24("24H", "HOUR", 0),
        D7("7D", "DAY", 7),
        D30("30D", "DAY", 30);

        private final String apiName;
        private final String bucketName;
        private final int days;

        Period(String apiName, String bucketName, int days) {
            this.apiName = apiName;
            this.bucketName = bucketName;
            this.days = days;
        }

        static Period parse(String value) {
            String normalized = value == null || value.isBlank() ? "24H" : value;
            for (Period period : values()) {
                if (period.apiName.equals(normalized)) return period;
            }
            throw new IllegalArgumentException("period는 24H, 7D, 30D 중 하나여야 합니다.");
        }
    }

    private record Bounds(Instant from, Instant to) {
    }

    private static final class BucketTotals {
        private long calls;
        private double elapsedMs;
        private long policyViolations;
        private final Map<String, OutcomeTotals> outcomes = new LinkedHashMap<>();

        private BucketTotals() {
            for (String outcome : OUTCOMES) outcomes.put(outcome, new OutcomeTotals());
        }

        private void add(String outcome, long count, double elapsed) {
            OutcomeTotals totals = outcomes.get(outcome);
            if (totals == null) return;
            calls += count;
            elapsedMs += elapsed;
            totals.count += count;
            totals.elapsedMs += elapsed;
        }

        private Map<String, AiOperationsMetricsView.Outcome> outcomeViews() {
            Map<String, AiOperationsMetricsView.Outcome> views = new LinkedHashMap<>();
            outcomes.forEach((name, totals) -> views.put(name, new AiOperationsMetricsView.Outcome(
                    totals.count, round(totals.elapsedMs), average(totals.elapsedMs, totals.count))));
            return views;
        }

        private AiOperationsMetricsView.SeriesPoint view(Instant start, Instant end) {
            return new AiOperationsMetricsView.SeriesPoint(
                    start.toString(), end.toString(), calls, average(elapsedMs, calls),
                    policyViolations, outcomeViews());
        }
    }

    private static final class OutcomeTotals {
        private long count;
        private double elapsedMs;
    }
}
