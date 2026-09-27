package team.hotelchain.ai;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
        "ai.telemetry.ingest-token=test-ingest-token",
        "ai.metrics.retention-job-enabled=false"
})
class AiTelemetryIngestIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        clean();
    }

    @AfterEach
    void cleanAfter() {
        clean();
    }

    @Test
    void acceptsAnAuthenticatedEventAndDeduplicatesAnIdenticalRetry() throws Exception {
        UUID id = UUID.randomUUID();
        String body = callBody(id, "success", 123.45);

        ingest(body, "Bearer test-ingest-token").andExpect(status().isAccepted());
        ingest(body, "Bearer test-ingest-token").andExpect(status().isAccepted());

        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("select count(*) from ai_telemetry_event", Integer.class)).isEqualTo(1);
    }

    @Test
    void rejectsMissingCredentialsAndConflictingReuseOfAnEventId() throws Exception {
        UUID id = UUID.randomUUID();
        ingest(callBody(id, "success", 100), null).andExpect(status().isUnauthorized());

        ingest(callBody(id, "success", 100), "Bearer test-ingest-token")
                .andExpect(status().isAccepted());
        ingest(callBody(id, "api_error", 100), "Bearer test-ingest-token")
                .andExpect(status().isConflict());
    }

    @Test
    void validatesTheAllowlistAndPolicyEventShape() throws Exception {
        UUID id = UUID.randomUUID();
        ingest(callBody(id, "invented", 100), "Bearer test-ingest-token")
                .andExpect(status().isBadRequest());
        ingest(callBody(UUID.randomUUID(), "success", -1), "Bearer test-ingest-token")
                .andExpect(status().isBadRequest());

        String policy = """
                {"eventId":"%s","eventType":"POLICY_VIOLATION"}
                """.formatted(UUID.randomUUID());
        ingest(policy, "Bearer test-ingest-token").andExpect(status().isAccepted());
    }

    private org.springframework.test.web.servlet.ResultActions ingest(String body, String authorization) throws Exception {
        var request = MockMvcRequestBuilders.post("/api/internal/concierge/telemetry")
                .contentType("application/json")
                .content(body);
        if (authorization != null) request.header("Authorization", authorization);
        return mvc.perform(request);
    }

    private String callBody(UUID id, String outcome, double elapsedMs) {
        return """
                {"eventId":"%s","eventType":"LLM_CALL","model":"gemini-3.6-flash","outcome":"%s","elapsedMs":%s}
                """.formatted(id, outcome, elapsedMs);
    }

    private void clean() {
        Boolean exists = jdbc.queryForObject(
                "select to_regclass('public.ai_telemetry_event') is not null", Boolean.class);
        if (Boolean.TRUE.equals(exists)) jdbc.update("delete from ai_telemetry_event");
    }
}
