package team.hotelchain.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.reservation.BusinessConflictException;

@Service
public class AiTelemetryIngestService {

    private static final Set<String> OUTCOMES = Set.of(
            "success", "schema_rejected", "unparsable", "empty_response", "api_error", "no_key");
    private static final double MAX_ELAPSED_MS = 600_000d;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AiTelemetryIngestService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public void ingest(AiTelemetryEventRequest request) {
        validate(request);
        String hash = hash(request);
        Timestamp now = Timestamp.from(clock.instant());
        Timestamp occurredAt = Timestamp.from(request.occurredAt());
        int inserted = jdbc.update("""
                insert into ai_telemetry_event
                    (event_id, event_hash, event_type, model, outcome, elapsed_ms, occurred_at, recorded_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (event_id) do nothing
                """, request.eventId(), hash, request.eventType(), request.model(), request.outcome(),
                request.elapsedMs(), occurredAt, now);
        if (inserted == 1) return;

        String existingHash = jdbc.queryForObject(
                "select event_hash from ai_telemetry_event where event_id = ?",
                String.class, request.eventId());
        if (!hash.equals(existingHash == null ? null : existingHash.trim())) {
            throw new BusinessConflictException(
                    "AI_TELEMETRY_EVENT_CONFLICT",
                    "같은 이벤트 ID에 서로 다른 지표를 기록할 수 없습니다.");
        }
    }

    private void validate(AiTelemetryEventRequest request) {
        if (request == null || request.eventId() == null) {
            throw new IllegalArgumentException("eventId가 필요합니다.");
        }
        Instant now = clock.instant();
        if (request.occurredAt() == null
                || request.occurredAt().isBefore(now.minus(Duration.ofDays(AiOperationsMetricsService.RETENTION_DAYS)))
                || request.occurredAt().isAfter(now.plus(Duration.ofMinutes(1)))) {
            throw new IllegalArgumentException("occurredAt은 보존 기간 안의 UTC 시각이어야 합니다.");
        }
        if ("LLM_CALL".equals(request.eventType())) {
            if (request.model() == null || request.model().isBlank() || request.model().length() > 100) {
                throw new IllegalArgumentException("model은 1자 이상 100자 이하여야 합니다.");
            }
            if (!OUTCOMES.contains(request.outcome())) {
                throw new IllegalArgumentException("지원하지 않는 outcome입니다.");
            }
            if (request.elapsedMs() == null || !Double.isFinite(request.elapsedMs())
                    || request.elapsedMs() < 0 || request.elapsedMs() > MAX_ELAPSED_MS) {
                throw new IllegalArgumentException("elapsedMs 범위가 올바르지 않습니다.");
            }
            return;
        }
        if ("POLICY_VIOLATION".equals(request.eventType())) {
            if (request.model() != null || request.outcome() != null || request.elapsedMs() != null) {
                throw new IllegalArgumentException("정책 위반 이벤트에는 LLM 결과 필드를 넣을 수 없습니다.");
            }
            return;
        }
        throw new IllegalArgumentException("지원하지 않는 eventType입니다.");
    }

    private String hash(AiTelemetryEventRequest request) {
        String canonical = String.join("\u001f",
                request.eventType(),
                request.model() == null ? "" : request.model(),
                request.outcome() == null ? "" : request.outcome(),
                request.elapsedMs() == null ? "" : Double.toString(request.elapsedMs()),
                request.occurredAt().toString());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
