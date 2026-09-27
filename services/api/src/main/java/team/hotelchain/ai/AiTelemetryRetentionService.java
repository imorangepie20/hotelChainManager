package team.hotelchain.ai;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AiTelemetryRetentionService {

    private static final int BATCH_SIZE = 10_000;
    private static final int MAX_BATCHES_PER_RUN = 10;

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final boolean scheduledEnabled;

    public AiTelemetryRetentionService(JdbcTemplate jdbc, Clock clock,
            @Value("${ai.metrics.retention-job-enabled:true}") boolean scheduledEnabled) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.scheduledEnabled = scheduledEnabled;
    }

    public int purgeExpired() {
        Timestamp cutoff = Timestamp.from(clock.instant().minus(Duration.ofDays(
                AiOperationsMetricsService.RETENTION_DAYS)));
        int total = 0;
        int deleted;
        int batches = 0;
        do {
            deleted = jdbc.update("""
                    with expired as (
                        select event_id
                          from ai_telemetry_event
                         where occurred_at < ?
                         order by occurred_at
                         for update skip locked
                         limit ?
                    )
                    delete from ai_telemetry_event event
                     using expired
                     where event.event_id = expired.event_id
                    """, cutoff, BATCH_SIZE);
            total += deleted;
            batches += 1;
        } while (deleted == BATCH_SIZE && batches < MAX_BATCHES_PER_RUN);
        return total;
    }

    @Scheduled(cron = "${ai.metrics.retention-cron:0 15 3 * * *}", zone = "UTC")
    public void scheduledPurge() {
        if (scheduledEnabled) purgeExpired();
    }
}
