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

import team.hotelchain.staff.StaffAccessService;

@SpringBootTest
class PolicyIntegrationTest {

    private static final UUID SOKCHO = UUID.fromString("16000000-0000-0000-0000-000000000001");
    private static final String HQ_EMAIL = "policy-hq@example.com";
    private static final String SOKCHO_EMAIL = "policy-sokcho@example.com";

    @Autowired JdbcTemplate jdbc;
    @Autowired StaffAccessService staffAccess;
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

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/policies/revisions")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.revisions", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.revisions[0].key").value("change-approval"))
                .andExpect(jsonPath("$.revisions[0].summary").value("예약 변경 승인 한도 400,000원"))
                .andExpect(jsonPath("$.revisions[0].staffEmail").value(HQ_EMAIL))
                .andExpect(jsonPath("$.revisions[1].key").value("cancellation"))
                .andExpect(jsonPath("$.revisions[1].summary").value("취소 정책 체크인 3일 전 20:30 마감"));
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
        jdbc.update("delete from policy_revision");
        jdbc.update("delete from staff_member");
        jdbc.update("delete from hotel where id = ?", SOKCHO);
    }
}
