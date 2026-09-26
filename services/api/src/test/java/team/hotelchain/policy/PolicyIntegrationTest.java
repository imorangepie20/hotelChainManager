package team.hotelchain.policy;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import team.hotelchain.reservationchange.ReservationChangePolicy;
import team.hotelchain.staff.StaffAccessService;

@SpringBootTest
class PolicyIntegrationTest {

    private static final UUID SOKCHO = UUID.fromString("16000000-0000-0000-0000-000000000001");
    private static final String HQ_EMAIL = "policy-hq@example.com";
    private static final String SOKCHO_EMAIL = "policy-sokcho@example.com";

    @Autowired JdbcTemplate jdbc;
    @Autowired StaffAccessService staffAccess;
    @Autowired ReservationChangePolicy changePolicy;
    @Autowired WebApplicationContext context;

    private MockMvc mvc;
    private String hqToken;
    private String sokchoToken;

    @BeforeEach
    void seed() {
        clean();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();

        jdbc.update("insert into hotel values (?, ?, ?, ?)", SOKCHO, "정책 속초", "속초", "Asia/Seoul");
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), HQ_EMAIL, "본사 관리자", encoder.encode("hq-password"), "HQ_ADMIN", null);
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), SOKCHO_EMAIL, "속초 직원", encoder.encode("branch-password"), "BRANCH_STAFF", SOKCHO);

        hqToken = staffAccess.login(HQ_EMAIL, "hq-password").token();
        sokchoToken = staffAccess.login(SOKCHO_EMAIL, "branch-password").token();
    }

    @AfterEach
    void cleanAfter() {
        clean();
    }

    @Test
    void headquartersReadsCurrentPolicy() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/policies")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellation.refundCutoffDaysBefore").value(1))
                .andExpect(jsonPath("$.cancellation.refundCutoffLocalTime").value("18:00"))
                .andExpect(jsonPath("$.cancellation.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.changeApprovalDirectLimitKrw").value(100000))
                .andExpect(jsonPath("$.changeSettlementEnabled").value(false));
    }

    @Test
    void headquartersUpdatesAndReadsChainRefundRules() throws Exception {
        String body = """
                {"rules":[
                  {"daysBefore":3,"cutoffLocalTime":"18:00","refundPercent":100},
                  {"daysBefore":1,"cutoffLocalTime":"18:00","refundPercent":50},
                  {"daysBefore":0,"cutoffLocalTime":"18:00","refundPercent":0}
                ]}
                """;

        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/cancellation")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "chain-rules")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scope").value("CHAIN"))
                .andExpect(jsonPath("$.revisionId").isNotEmpty())
                .andExpect(jsonPath("$.rules", org.hamcrest.Matchers.hasSize(3)))
                .andExpect(jsonPath("$.rules[1].refundPercent").value(50));

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/policies")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                // 기존 단일 마감 필드는 첫 100% 규칙으로 계속 제공한다.
                .andExpect(jsonPath("$.cancellation.refundCutoffDaysBefore").value(3))
                .andExpect(jsonPath("$.cancellation.refundCutoffLocalTime").value("18:00"))
                .andExpect(jsonPath("$.cancellation.scope").value("CHAIN"))
                .andExpect(jsonPath("$.cancellation.rules", org.hamcrest.Matchers.hasSize(3)));
    }

    @Test
    void legacyCompatibleUpdateKeepsTheScopedRevisionIdentityWhenReadBack() throws Exception {
        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/cancellation")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "legacy-compatible-revision")
                .contentType("application/json")
                .content("{\"refundCutoffDaysBefore\":2,\"refundCutoffLocalTime\":\"19:00\"}"))
                .andExpect(status().isCreated());

        UUID scopedRevisionId = jdbc.queryForObject(
                "select id from cancellation_policy_revision where idempotency_key = ?",
                UUID.class,
                "legacy-compatible-revision");

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/policies")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellation.revisionId").value(scopedRevisionId.toString()));
    }

    @Test
    void hotelOverrideAndInheritExposeEffectivePolicy() throws Exception {
        updateChainRules("chain-default", 4, 100, 1, 40);

        String overrideBody = """
                {"rules":[
                  {"daysBefore":2,"cutoffLocalTime":"17:00","refundPercent":100},
                  {"daysBefore":0,"cutoffLocalTime":"12:00","refundPercent":20}
                ]}
                """;
        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/hotels/{hotelId}/cancellation", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "hotel-override")
                .contentType("application/json")
                .content(overrideBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scope").value("HOTEL"))
                .andExpect(jsonPath("$.hotelId").value(SOKCHO.toString()))
                .andExpect(jsonPath("$.inherited").value(false));

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/policies?hotelId={hotelId}", SOKCHO)
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellation.scope").value("HOTEL"))
                .andExpect(jsonPath("$.cancellation.inherited").value(false))
                .andExpect(jsonPath("$.cancellation.rules[1].refundPercent").value(20))
                .andExpect(jsonPath("$.chainCancellation.rules[1].refundPercent").value(40))
                .andExpect(jsonPath("$.hotelCancellationRevision.action").value("SET"));

        mvc.perform(MockMvcRequestBuilders.put(
                        "/api/staff/policies/hotels/{hotelId}/cancellation/inherit", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "hotel-inherit"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scope").value("CHAIN"))
                .andExpect(jsonPath("$.inherited").value(true));

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/policies?hotelId={hotelId}", SOKCHO)
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellation.scope").value("CHAIN"))
                .andExpect(jsonPath("$.cancellation.inherited").value(true))
                .andExpect(jsonPath("$.cancellation.rules[1].refundPercent").value(40))
                .andExpect(jsonPath("$.hotelCancellationRevision.action").value("INHERIT"));
    }

    @Test
    void sameScopeIdempotencyKeyRejectsDifferentRequestButScopesAreIndependent() throws Exception {
        updateChainRules("shared-key", 3, 100, 1, 50);

        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/cancellation")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "shared-key")
                .contentType("application/json")
                .content(rulesBody(3, 100, 1, 30)))
                .andExpect(status().isConflict());

        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/hotels/{hotelId}/cancellation", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "shared-key")
                .contentType("application/json")
                .content(rulesBody(2, 100, 0, 0)))
                .andExpect(status().isCreated());
    }

    @Test
    void sameRulesWithNewKeyAreANoOpInTheSameScope() throws Exception {
        String body = rulesBody(3, 100, 1, 50);
        updateChainRules("first-key", 3, 100, 1, 50);

        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/cancellation")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "second-key")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.revision").value(1));
    }

    @Test
    void noOpKeyRemainsBoundAfterThePolicyChanges() throws Exception {
        String first = rulesBody(3, 100, 1, 50);
        String second = rulesBody(5, 100, 2, 30);
        updateChainRules("first-key", 3, 100, 1, 50);

        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/cancellation")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "noop-key")
                .contentType("application/json")
                .content(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false));

        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/cancellation")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "second-policy")
                .contentType("application/json")
                .content(second))
                .andExpect(status().isCreated());

        // 응답 유실 뒤 같은 no-op 키를 재전송해도 과거 정책을 새 revision으로 되돌리지 않는다.
        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/cancellation")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "noop-key")
                .contentType("application/json")
                .content(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.rules[1].refundPercent").value(50));

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/policies")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellation.refundCutoffDaysBefore").value(5));

        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/cancellation")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "noop-key")
                .contentType("application/json")
                .content(second))
                .andExpect(status().isConflict());
    }

    @Test
    void revisionNumberWinsWhenRevisionTimestampsAreEqual() throws Exception {
        updateChainRules("same-time-first", 3, 100, 1, 50);
        updateChainRules("same-time-second", 5, 100, 2, 30);
        jdbc.update("update cancellation_policy_revision set created_at = now()");

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/policies")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellation.refundCutoffDaysBefore").value(5));
    }

    @Test
    void preMigrationLegacyIdempotencyKeyKeepsItsMeaning() throws Exception {
        UUID staffId = jdbc.queryForObject(
                "select id from staff_member where email = ?", UUID.class, HQ_EMAIL);
        jdbc.update("""
                insert into policy_revision
                    (id, key, refund_cutoff_days_before, refund_cutoff_local_time, timezone,
                     staff_id, idempotency_key, request_hash)
                values (?, 'cancellation', 2, '19:00', 'Asia/Seoul', ?, 'legacy-key', ?)
                """, UUID.randomUUID(), staffId, "b".repeat(64));

        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/cancellation")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "legacy-key")
                .contentType("application/json")
                .content("{\"refundCutoffDaysBefore\":2,\"refundCutoffLocalTime\":\"19:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.revision").value(1));

        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/cancellation")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "legacy-key")
                .contentType("application/json")
                .content(rulesBody(3, 100, 1, 50)))
                .andExpect(status().isConflict());

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from cancellation_policy_revision", Integer.class)).isZero();
    }

    @Test
    void invalidRefundRuleDocumentsAreRejected() throws Exception {
        String duplicateThreshold = """
                {"rules":[
                  {"daysBefore":2,"cutoffLocalTime":"18:00","refundPercent":100},
                  {"daysBefore":2,"cutoffLocalTime":"18:00","refundPercent":50}
                ]}
                """;
        String refundIncreasesNearCheckIn = rulesBody(3, 50, 1, 100);
        String noFullRefund = rulesBody(3, 80, 1, 20);

        for (String body : java.util.List.of(duplicateThreshold, refundIncreasesNearCheckIn, noFullRefund)) {
            mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/cancellation")
                    .header("X-Staff-Session", hqToken)
                    .header("Idempotency-Key", "invalid-" + UUID.randomUUID())
                    .contentType("application/json")
                    .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void hotelPolicyEndpointsRequireHeadquartersAndExistingHotel() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/policies?hotelId={hotelId}", SOKCHO)
                .header("X-Staff-Session", sokchoToken))
                .andExpect(status().isForbidden());

        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/hotels/{hotelId}/cancellation", SOKCHO)
                .header("X-Staff-Session", sokchoToken)
                .header("Idempotency-Key", "branch-denied")
                .contentType("application/json")
                .content(rulesBody(2, 100, 0, 0)))
                .andExpect(status().isForbidden());

        UUID unknown = UUID.randomUUID();
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/policies?hotelId={hotelId}", unknown)
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isNotFound());

        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/hotels/{hotelId}/cancellation", unknown)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "unknown-hotel")
                .contentType("application/json")
                .content(rulesBody(2, 100, 0, 0)))
                .andExpect(status().isNotFound());
    }

    @Test
    void branchStaffCannotReadPolicy() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/policies")
                .header("X-Staff-Session", sokchoToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingSessionIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/policies"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void headquartersUpdatesCancellationPolicy() throws Exception {
        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/cancellation")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "update-cancellation")
                .contentType("application/json")
                .content("{\"refundCutoffDaysBefore\":3,\"refundCutoffLocalTime\":\"20:30\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundCutoffDaysBefore").value(3))
                .andExpect(jsonPath("$.refundCutoffLocalTime").value("20:30"))
                .andExpect(jsonPath("$.created").value(true));

        // 변경 직후 조회에 반영된다.
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/policies")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellation.refundCutoffDaysBefore").value(3))
                .andExpect(jsonPath("$.cancellation.refundCutoffLocalTime").value("20:30"));
    }

    @Test
    void sameIdempotencyKeyReturnsSameRevision() throws Exception {
        String body = "{\"refundCutoffDaysBefore\":2,\"refundCutoffLocalTime\":\"19:00\"}";
        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/cancellation")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "update-cancellation")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(true));

        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/cancellation")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "update-cancellation")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.refundCutoffDaysBefore").value(2));

        Integer count = jdbc.queryForObject(
                "select count(*) from policy_revision where key = 'cancellation'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
    }

    @Test
    void sameRequestWithNewIdempotencyKeyReturnsSameRevision() throws Exception {
        String body = "{\"refundCutoffDaysBefore\":2,\"refundCutoffLocalTime\":\"19:00\"}";
        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/cancellation")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "update-cancellation")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated());

        // 응답 유실 뒤 클라이언트가 새 멱원 키로 같은 내용을 보낸다.
        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/cancellation")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "update-cancellation-retry")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.refundCutoffDaysBefore").value(2));

        Integer count = jdbc.queryForObject(
                "select count(*) from policy_revision where key = 'cancellation'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
    }

    @Test
    void differentContentCreatesNewRevision() throws Exception {
        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/cancellation")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "update-cancellation")
                .contentType("application/json")
                .content("{\"refundCutoffDaysBefore\":2,\"refundCutoffLocalTime\":\"19:00\"}"))
                .andExpect(status().isCreated());

        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/cancellation")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "update-cancellation-2")
                .contentType("application/json")
                .content("{\"refundCutoffDaysBefore\":5,\"refundCutoffLocalTime\":\"20:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundCutoffDaysBefore").value(5));

        Integer count = jdbc.queryForObject(
                "select count(*) from policy_revision where key = 'cancellation'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(2);
    }

    @Test
    void cutoffDaysOutsideRangeIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/cancellation")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "update-zero")
                .contentType("application/json")
                .content("{\"refundCutoffDaysBefore\":0,\"refundCutoffLocalTime\":\"18:00\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/cancellation")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "update-thirty-one")
                .contentType("application/json")
                .content("{\"refundCutoffDaysBefore\":31,\"refundCutoffLocalTime\":\"18:00\"}"))
                .andExpect(status().isBadRequest());

        // 거부된 요청은 revision을 남기지 않는다.
        Integer count = jdbc.queryForObject(
                "select count(*) from policy_revision where key = 'cancellation'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(0);
    }

    @Test
    void malformedCutoffTimeIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/cancellation")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "update-time")
                .contentType("application/json")
                .content("{\"refundCutoffDaysBefore\":2,\"refundCutoffLocalTime\":\"25:00\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/cancellation")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "update-time-2")
                .contentType("application/json")
                .content("{\"refundCutoffDaysBefore\":2,\"refundCutoffLocalTime\":\"6시\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/cancellation")
                .header("X-Staff-Session", hqToken)
                .contentType("application/json")
                .content("{\"refundCutoffDaysBefore\":2,\"refundCutoffLocalTime\":\"19:00\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void branchStaffCannotUpdatePolicy() throws Exception {
        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/cancellation")
                .header("X-Staff-Session", sokchoToken)
                .header("Idempotency-Key", "update-cancellation")
                .contentType("application/json")
                .content("{\"refundCutoffDaysBefore\":2,\"refundCutoffLocalTime\":\"19:00\"}"))
                .andExpect(status().isForbidden());

        Integer count = jdbc.queryForObject(
                "select count(*) from policy_revision where key = 'cancellation'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(0);
    }

    @Test
    void headquartersReadsConfiguredChangeApprovalLimit() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/policies")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changeApprovalDirectLimitKrw").value(100000));
    }

    @Test
    void headquartersUpdatesChangeApprovalLimit() throws Exception {
        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/change-limit")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "update-limit")
                .contentType("application/json")
                .content("{\"directLimitKrw\":300000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.directLimitKrw").value(300000))
                .andExpect(jsonPath("$.created").value(true));

        // 본사가 바꾼 한도가 즉시 현재값에 반영된다.
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/policies")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changeApprovalDirectLimitKrw").value(300000));
    }

    @Test
    void sameIdempotencyKeyReturnsSameLimitRevision() throws Exception {
        String body = "{\"directLimitKrw\":250000}";
        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/change-limit")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "update-limit")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated());

        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/change-limit")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "update-limit")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.directLimitKrw").value(250000));

        Integer count = jdbc.queryForObject(
                "select count(*) from policy_revision where key = 'change-approval'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
    }

    @Test
    void sameLimitWithNewIdempotencyKeyReturnsSameRevision() throws Exception {
        String body = "{\"directLimitKrw\":250000}";
        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/change-limit")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "update-limit")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated());

        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/change-limit")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "update-limit-retry")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.directLimitKrw").value(250000));

        Integer count = jdbc.queryForObject(
                "select count(*) from policy_revision where key = 'change-approval'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
    }

    @Test
    void limitOutsideRangeIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/change-limit")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "limit-negative")
                .contentType("application/json")
                .content("{\"directLimitKrw\":-1}"))
                .andExpect(status().isBadRequest());

        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/change-limit")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "limit-too-big")
                .contentType("application/json")
                .content("{\"directLimitKrw\":10000001}"))
                .andExpect(status().isBadRequest());

        // 거부된 요청은 revision을 남기지 않는다.
        Integer count = jdbc.queryForObject(
                "select count(*) from policy_revision where key = 'change-approval'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(0);
    }

    @Test
    void limitUpdateRequiresIdempotencyKey() throws Exception {
        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/change-limit")
                .header("X-Staff-Session", hqToken)
                .contentType("application/json")
                .content("{\"directLimitKrw\":200000}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void branchStaffCannotUpdateChangeApprovalLimit() throws Exception {
        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/change-limit")
                .header("X-Staff-Session", sokchoToken)
                .header("Idempotency-Key", "update-limit")
                .contentType("application/json")
                .content("{\"directLimitKrw\":200000}"))
                .andExpect(status().isForbidden());

        Integer count = jdbc.queryForObject(
                "select count(*) from policy_revision where key = 'change-approval'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(0);
    }

    @Test
    void headquartersReadsConfiguredApprovalTtl() throws Exception {
        // 본사가 변경하지 않았으면 application.yml의 24h가 내려온다.
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/policies")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changeApprovalTtlSeconds").value(86400));
    }

    @Test
    void headquartersUpdatesChangeApprovalTtl() throws Exception {
        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/change-approval-ttl")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "update-ttl")
                .contentType("application/json")
                .content("{\"approvalTtlSeconds\":3600}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.approvalTtlSeconds").value(3600))
                .andExpect(jsonPath("$.created").value(true));

        // 본사가 바꾼 TTL이 즉시 현재값에 반영된다.
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/policies")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changeApprovalTtlSeconds").value(3600));
    }

    @Test
    void approvalTtlReachesTheChangeRequestExpiry() throws Exception {
        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/change-approval-ttl")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "update-ttl")
                .contentType("application/json")
                .content("{\"approvalTtlSeconds\":120}"))
                .andExpect(status().isCreated());

        // 예약 변경 요청이 approval_expires_at을 계산할 때 거치는 경로다.
        // 체크인이 충분히 멀면 TTL이 그대로 만료 시각이 된다.
        java.time.Instant created = java.time.Instant.now();
        java.time.LocalDate farCheckIn = java.time.LocalDate.now().plusDays(30);
        java.time.Instant expiry = changePolicy.approvalExpiresAt(created, farCheckIn, "Asia/Seoul");

        org.assertj.core.api.Assertions.assertThat(expiry)
                .isAfter(created.plus(java.time.Duration.ofSeconds(115)))
                .isBefore(created.plus(java.time.Duration.ofSeconds(125)));
    }

    @Test
    void sameIdempotencyKeyReturnsSameTtlRevision() throws Exception {
        String body = "{\"approvalTtlSeconds\":7200}";
        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/change-approval-ttl")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "update-ttl")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated());

        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/change-approval-ttl")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "update-ttl")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.approvalTtlSeconds").value(7200));

        Integer count = jdbc.queryForObject(
                "select count(*) from policy_revision where key = 'change-approval-ttl'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
    }

    @Test
    void sameTtlWithNewIdempotencyKeyReturnsSameRevision() throws Exception {
        String body = "{\"approvalTtlSeconds\":7200}";
        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/change-approval-ttl")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "update-ttl")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated());

        // 응답 유실 뒤 클라이언트가 새 멱원 키로 같은 내용을 보낸다.
        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/change-approval-ttl")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "update-ttl-retry")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.approvalTtlSeconds").value(7200));

        Integer count = jdbc.queryForObject(
                "select count(*) from policy_revision where key = 'change-approval-ttl'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
    }

    @Test
    void ttlOutsideRangeIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/change-approval-ttl")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "ttl-too-short")
                .contentType("application/json")
                .content("{\"approvalTtlSeconds\":59}"))
                .andExpect(status().isBadRequest());

        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/change-approval-ttl")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "ttl-too-long")
                .contentType("application/json")
                .content("{\"approvalTtlSeconds\":604801}"))
                .andExpect(status().isBadRequest());

        // 거부된 요청은 revision을 남기지 않는다.
        Integer count = jdbc.queryForObject(
                "select count(*) from policy_revision where key = 'change-approval-ttl'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(0);
    }

    @Test
    void ttlUpdateRequiresIdempotencyKey() throws Exception {
        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/change-approval-ttl")
                .header("X-Staff-Session", hqToken)
                .contentType("application/json")
                .content("{\"approvalTtlSeconds\":3600}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void branchStaffCannotUpdateApprovalTtl() throws Exception {
        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/change-approval-ttl")
                .header("X-Staff-Session", sokchoToken)
                .header("Idempotency-Key", "update-ttl")
                .contentType("application/json")
                .content("{\"approvalTtlSeconds\":3600}"))
                .andExpect(status().isForbidden());

        Integer count = jdbc.queryForObject(
                "select count(*) from policy_revision where key = 'change-approval-ttl'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(0);
    }

    @Test
    void headquartersReadsPolicyRevisions() throws Exception {
        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/cancellation")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "revision-cancellation")
                .contentType("application/json")
                .content("{\"refundCutoffDaysBefore\":3,\"refundCutoffLocalTime\":\"20:30\"}"))
                .andExpect(status().isCreated());

        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/change-limit")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "revision-limit")
                .contentType("application/json")
                .content("{\"directLimitKrw\":400000}"))
                .andExpect(status().isCreated());

        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/change-approval-ttl")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "revision-ttl")
                .contentType("application/json")
                .content("{\"approvalTtlSeconds\":3600}"))
                .andExpect(status().isCreated());

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/policies/revisions")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.revisions", org.hamcrest.Matchers.hasSize(3)))
                .andExpect(jsonPath("$.revisions[0].key").value("change-approval-ttl"))
                .andExpect(jsonPath("$.revisions[0].summary").value("예약 변경 승인 TTL 1시간 3,600초"))
                .andExpect(jsonPath("$.revisions[0].staffEmail").value(HQ_EMAIL))
                .andExpect(jsonPath("$.revisions[1].key").value("change-approval"))
                .andExpect(jsonPath("$.revisions[1].summary").value("예약 변경 승인 한도 400,000원"))
                .andExpect(jsonPath("$.revisions[2].key").value("cancellation"))
                .andExpect(jsonPath("$.revisions[2].summary").value("취소 정책 체크인 3일 전 20:30 마감"));
    }

    @Test
    void revisionsPagingIsValidated() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/policies/revisions?limit=0")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isBadRequest());

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/policies/revisions?limit=101")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isBadRequest());

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/policies/revisions?offset=-1")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void branchStaffCannotReadRevisions() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/policies/revisions")
                .header("X-Staff-Session", sokchoToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void revisionsRequireSession() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/policies/revisions"))
                .andExpect(status().isUnauthorized());
    }

    private void clean() {
        jdbc.update("delete from staff_session");
        jdbc.update("delete from cancellation_policy_noop_command");
        jdbc.update("delete from cancellation_refund_rule");
        jdbc.update("delete from cancellation_policy_revision");
        jdbc.update("delete from policy_revision");
        jdbc.update("delete from staff_member");
        jdbc.update("delete from hotel where id = ?", SOKCHO);
    }

    private void updateChainRules(String idempotencyKey, int firstDays, int firstPercent,
            int secondDays, int secondPercent) throws Exception {
        mvc.perform(MockMvcRequestBuilders.put("/api/staff/policies/cancellation")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType("application/json")
                .content(rulesBody(firstDays, firstPercent, secondDays, secondPercent)))
                .andExpect(status().isCreated());
    }

    private String rulesBody(int firstDays, int firstPercent, int secondDays, int secondPercent) {
        return """
                {"rules":[
                  {"daysBefore":%d,"cutoffLocalTime":"18:00","refundPercent":%d},
                  {"daysBefore":%d,"cutoffLocalTime":"18:00","refundPercent":%d}
                ]}
                """.formatted(firstDays, firstPercent, secondDays, secondPercent);
    }
}
