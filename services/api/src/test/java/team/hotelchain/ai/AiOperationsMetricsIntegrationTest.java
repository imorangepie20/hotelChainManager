package team.hotelchain.ai;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import team.hotelchain.staff.StaffAccessService;

@SpringBootTest(properties = "ai.metrics.retention-job-enabled=false")
@Import(AiOperationsMetricsIntegrationTest.ClockConfiguration.class)
class AiOperationsMetricsIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-27T00:30:00Z");
    private static final UUID HOTEL_ID = UUID.fromString("17000000-0000-0000-0000-000000000001");
    private static final String HQ_EMAIL = "ai-metrics-hq@example.com";
    private static final String BRANCH_EMAIL = "ai-metrics-branch@example.com";

    @Autowired JdbcTemplate jdbc;
    @Autowired StaffAccessService access;
    @Autowired WebApplicationContext context;
    @Autowired AiTelemetryRetentionService retention;

    private MockMvc mvc;
    private String hqToken;
    private String branchToken;

    @BeforeEach
    void seedStaff() {
        clean();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbc.update("insert into hotel values (?, ?, ?, ?)", HOTEL_ID, "ai-metrics-hotel", "seoul", "Asia/Seoul");
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), HQ_EMAIL, "ai-metrics-hq", encoder.encode("hq-password"), "HQ_ADMIN", null);
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), BRANCH_EMAIL, "ai-metrics-branch", encoder.encode("branch-password"), "BRANCH_STAFF", HOTEL_ID);
        hqToken = access.login(HQ_EMAIL, "hq-password").token();
        branchToken = access.login(BRANCH_EMAIL, "branch-password").token();
    }

    @AfterEach
    void cleanAfter() {
        clean();
    }

    @Test
    void headquartersReadsPersistentHourlyMetricsOnSeoulBoundaries() throws Exception {
        insertCall("success", 100, Instant.parse("2026-09-27T00:05:00Z"));
        insertCall("api_error", 300, Instant.parse("2026-09-27T00:20:00Z"));
        insertCall("success", 200, Instant.parse("2026-09-26T23:40:00Z"));
        insertPolicyViolation(Instant.parse("2026-09-27T00:25:00Z"));

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/ai-operations/metrics")
                .header("X-Staff-Session", hqToken)
                .param("period", "24H"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.period").value("24H"))
                .andExpect(jsonPath("$.bucket").value("HOUR"))
                .andExpect(jsonPath("$.fromInclusive").value("2026-09-26T01:00:00Z"))
                .andExpect(jsonPath("$.toExclusive").value("2026-09-27T01:00:00Z"))
                .andExpect(jsonPath("$.retentionDays").value(90))
                .andExpect(jsonPath("$.totals.calls").value(3))
                .andExpect(jsonPath("$.totals.avgElapsedMs").value(200.0))
                .andExpect(jsonPath("$.totals.policyViolations").value(1))
                .andExpect(jsonPath("$.outcomes.success.count").value(2))
                .andExpect(jsonPath("$.outcomes.api_error.count").value(1))
                .andExpect(jsonPath("$.series", Matchers.hasSize(24)))
                .andExpect(jsonPath("$.series[?(@.startAt=='2026-09-27T00:00:00Z')].calls")
                        .value(Matchers.contains(2)))
                .andExpect(jsonPath("$.series[?(@.startAt=='2026-09-27T00:00:00Z')].avgElapsedMs")
                        .value(Matchers.contains(200.0)))
                .andExpect(jsonPath("$.series[?(@.startAt=='2026-09-27T00:00:00Z')].policyViolations")
                        .value(Matchers.contains(1)));
    }

    @Test
    void duplicateEventIdIsCountedOnlyOnceAndRowsSurviveServiceReads() throws Exception {
        UUID eventId = UUID.randomUUID();
        insertCall(eventId, "success", 120, Instant.parse("2026-09-27T00:10:00Z"));
        insertCall(eventId, "success", 120, Instant.parse("2026-09-27T00:10:00Z"));

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/ai-operations/metrics")
                .header("X-Staff-Session", hqToken)
                .param("period", "24H"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.calls").value(1));

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/ai-operations/metrics")
                .header("X-Staff-Session", hqToken)
                .param("period", "24H"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.calls").value(1));
        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("select count(*) from ai_telemetry_event", Integer.class)).isEqualTo(1);
    }

    @Test
    void dailyPeriodUsesSeoulMidnightsAndReturnsEmptyOutcomes() throws Exception {
        insertCall("schema_rejected", 80, Instant.parse("2026-09-20T15:00:00Z"));
        insertCall("success", 100, Instant.parse("2026-09-27T15:00:00Z"));

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/ai-operations/metrics")
                .header("X-Staff-Session", hqToken)
                .param("period", "7D"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucket").value("DAY"))
                .andExpect(jsonPath("$.fromInclusive").value("2026-09-20T15:00:00Z"))
                .andExpect(jsonPath("$.toExclusive").value("2026-09-27T15:00:00Z"))
                .andExpect(jsonPath("$.totals.calls").value(1))
                .andExpect(jsonPath("$.outcomes.schema_rejected.count").value(1))
                .andExpect(jsonPath("$.outcomes.no_key.count").value(0))
                .andExpect(jsonPath("$.series", Matchers.hasSize(7)));
    }

    @Test
    void metricsRequireHeadquartersAndValidatePeriod() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/ai-operations/metrics"))
                .andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/ai-operations/metrics")
                .header("X-Staff-Session", branchToken))
                .andExpect(status().isForbidden());
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/ai-operations/metrics")
                .header("X-Staff-Session", hqToken)
                .param("period", "YEAR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retentionDeletesOnlyEventsOlderThanNinetyDays() {
        Instant cutoff = NOW.minus(Duration.ofDays(90));
        insertCall("success", 10, cutoff.minusMillis(1));
        insertCall("success", 20, cutoff);

        int deleted = retention.purgeExpired();

        org.assertj.core.api.Assertions.assertThat(deleted).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("select count(*) from ai_telemetry_event", Integer.class)).isEqualTo(1);
    }

    private void insertCall(String outcome, long elapsedMs, Instant occurredAt) {
        insertCall(UUID.randomUUID(), outcome, elapsedMs, occurredAt);
    }

    private void insertCall(UUID id, String outcome, long elapsedMs, Instant occurredAt) {
        jdbc.update("""
                insert into ai_telemetry_event
                    (event_id, event_hash, event_type, model, outcome, elapsed_ms, occurred_at, recorded_at)
                values (?, repeat('a', 64), 'LLM_CALL', 'gemini-3.6-flash', ?, ?, ?, ?)
                on conflict (event_id) do nothing
                """, id, outcome, elapsedMs, Timestamp.from(occurredAt), Timestamp.from(occurredAt));
    }

    private void insertPolicyViolation(Instant occurredAt) {
        jdbc.update("""
                insert into ai_telemetry_event
                    (event_id, event_hash, event_type, occurred_at, recorded_at)
                values (?, repeat('b', 64), 'POLICY_VIOLATION', ?, ?)
                """, UUID.randomUUID(), Timestamp.from(occurredAt), Timestamp.from(occurredAt));
    }

    private void clean() {
        Boolean telemetryExists = jdbc.queryForObject(
                "select to_regclass('public.ai_telemetry_event') is not null", Boolean.class);
        if (Boolean.TRUE.equals(telemetryExists)) jdbc.update("delete from ai_telemetry_event");
        jdbc.update("delete from staff_session");
        jdbc.update("delete from staff_member where email in (?, ?)", HQ_EMAIL, BRANCH_EMAIL);
        jdbc.update("delete from hotel where id = ?", HOTEL_ID);
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock(NOW);
        }
    }

    static final class TestClock extends Clock {
        private final AtomicReference<Instant> instant;

        TestClock(Instant initial) {
            instant = new AtomicReference<>(initial);
        }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
    }
}
