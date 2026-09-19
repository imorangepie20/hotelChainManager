package team.hotelchain.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

@SpringBootTest
class StaffAccountIntegrationTest {

    private static final UUID SOKCHO = UUID.fromString("17000000-0000-0000-0000-000000000001");
    private static final String HQ_EMAIL = "account-hq@example.com";
    private static final String SOKCHO_EMAIL = "account-sokcho@example.com";
    private static final String NEW_EMAIL = "new-editor@example.com";

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

        jdbc.update("insert into hotel values (?, ?, ?, ?)", SOKCHO, "계정 속초", "속초", "Asia/Seoul");

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
    void headquartersListsStaffAccounts() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/staff")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].email").value(HQ_EMAIL))
                .andExpect(jsonPath("$[0].role").value("HQ_ADMIN"))
                .andExpect(jsonPath("$[0].hotelName").isEmpty())
                .andExpect(jsonPath("$[1].email").value(SOKCHO_EMAIL))
                .andExpect(jsonPath("$[1].role").value("BRANCH_STAFF"))
                .andExpect(jsonPath("$[1].hotelName").value("계정 속초"));
    }

    @Test
    void branchStaffCannotListAccounts() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/staff")
                .header("X-Staff-Session", sokchoToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingSessionIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/staff"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void headquartersCreatesBranchStaff() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/staff")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "create-editor")
                .contentType("application/json")
                .content("{\"email\":\"" + NEW_EMAIL + "\",\"displayName\":\"새 편집자\",\"role\":\"HQ_EDITOR\",\"hotelId\":null}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(NEW_EMAIL))
                .andExpect(jsonPath("$.displayName").value("새 편집자"))
                .andExpect(jsonPath("$.role").value("HQ_EDITOR"))
                .andExpect(jsonPath("$.hotelName").isEmpty())
                .andExpect(jsonPath("$.created").value(true));

        String hash = jdbc.queryForObject(
                "select password_hash from staff_member where email = ?", String.class, NEW_EMAIL);
        assertThat(hash).startsWith("$2a$");

        // 임시 비밀번호로 로그인되는지 확인한다.
        String temporaryPassword = mvc.perform(MockMvcRequestBuilders.post("/api/staff/staff")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "create-editor")
                .contentType("application/json")
                .content("{\"email\":\"" + NEW_EMAIL + "\",\"displayName\":\"새 편집자\",\"role\":\"HQ_EDITOR\",\"hotelId\":null}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(temporaryPassword).contains("\"temporaryPassword\"");

        // 비밀번호 원문은 저장하지 않는다.
        Integer plainMatches = jdbc.queryForObject(
                "select count(*) from staff_member where email = ? and password_hash = ?",
                Integer.class, NEW_EMAIL, temporaryPassword);
        assertThat(plainMatches).isZero();
    }

    @Test
    void branchStaffCannotCreateAccount() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/staff")
                .header("X-Staff-Session", sokchoToken)
                .header("Idempotency-Key", "create-editor")
                .contentType("application/json")
                .content("{\"email\":\"" + NEW_EMAIL + "\",\"displayName\":\"새 편집자\",\"role\":\"HQ_EDITOR\",\"hotelId\":null}"))
                .andExpect(status().isForbidden());

        Integer count = jdbc.queryForObject(
                "select count(*) from staff_member where email = ?", Integer.class, NEW_EMAIL);
        assertThat(count).isZero();
    }

    @Test
    void missingSessionCannotCreateAccount() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/staff")
                .header("Idempotency-Key", "create-editor")
                .contentType("application/json")
                .content("{\"email\":\"" + NEW_EMAIL + "\",\"displayName\":\"새 편집자\",\"role\":\"HQ_EDITOR\",\"hotelId\":null}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/staff")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "create-duplicate")
                .contentType("application/json")
                .content("{\"email\":\"" + SOKCHO_EMAIL + "\",\"displayName\":\"중복\",\"role\":\"HQ_ADMIN\",\"hotelId\":null}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAFF_EMAIL_DUPLICATE"));
    }

    @Test
    void invalidEmailIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/staff")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "create-bad-email")
                .contentType("application/json")
                .content("{\"email\":\"not-an-email\",\"displayName\":\"새 편집자\",\"role\":\"HQ_EDITOR\",\"hotelId\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownRoleIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/staff")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "create-bad-role")
                .contentType("application/json")
                .content("{\"email\":\"" + NEW_EMAIL + "\",\"displayName\":\"새 편집자\",\"role\":\"SUPER_ADMIN\",\"hotelId\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void branchStaffWithoutHotelIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/staff")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "create-no-hotel")
                .contentType("application/json")
                .content("{\"email\":\"" + NEW_EMAIL + "\",\"displayName\":\"지점 직원\",\"role\":\"BRANCH_STAFF\",\"hotelId\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void headquartersRoleWithHotelIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/staff")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "create-with-hotel")
                .contentType("application/json")
                .content("{\"email\":\"" + NEW_EMAIL + "\",\"displayName\":\"본사\",\"role\":\"HQ_ADMIN\",\"hotelId\":\"" + SOKCHO + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownHotelIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/staff")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "create-unknown-hotel")
                .contentType("application/json")
                .content("{\"email\":\"" + NEW_EMAIL + "\",\"displayName\":\"지점 직원\",\"role\":\"BRANCH_STAFF\",\"hotelId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/staff")
                .header("X-Staff-Session", hqToken)
                .contentType("application/json")
                .content("{\"email\":\"" + NEW_EMAIL + "\",\"displayName\":\"새 편집자\",\"role\":\"HQ_EDITOR\",\"hotelId\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sameIdempotencyKeyReturnsSameStaff() throws Exception {
        String body = "{\"email\":\"" + NEW_EMAIL + "\",\"displayName\":\"새 편집자\",\"role\":\"HQ_EDITOR\",\"hotelId\":null}";
        String first = mvc.perform(MockMvcRequestBuilders.post("/api/staff/staff")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "create-editor")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(true))
                .andReturn().getResponse().getContentAsString();

        String second = mvc.perform(MockMvcRequestBuilders.post("/api/staff/staff")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "create-editor")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andReturn().getResponse().getContentAsString();

        // 멱원 재호출은 임시 비밀번호를 다시 내보내지 않는다.
        assertThat(extractId(second)).isEqualTo(extractId(first));

        Integer count = jdbc.queryForObject(
                "select count(*) from staff_member where email = ?", Integer.class, NEW_EMAIL);
        assertThat(count).isEqualTo(1);
    }

    private String extractId(String body) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("staffId").asText();
    }

    private String extract(String body, String field) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get(field).asText();
    }

    private String resetPassword(UUID staffId, String idempotencyKey, String token) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/staff/staff/" + staffId + "/password")
                .header("X-Staff-Session", token)
                .header("Idempotency-Key", idempotencyKey))
                .andReturn().getResponse().getContentAsString();
    }

    private UUID staffId(String email) {
        return jdbc.queryForObject(
                "select id from staff_member where email = ?", UUID.class, email);
    }

    @Test
    void headquartersResetsBranchStaffPassword() throws Exception {
        String response = mvc.perform(MockMvcRequestBuilders.post(
                        "/api/staff/staff/" + staffId(SOKCHO_EMAIL) + "/password")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "reset-sokcho"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(SOKCHO_EMAIL))
                .andExpect(jsonPath("$.created").value(true))
                .andReturn().getResponse().getContentAsString();

        String temporaryPassword = extract(response, "temporaryPassword");
        assertThat(temporaryPassword).hasSize(16);

        // 예전 비밀번호로는 더 이상 로그인할 수 없다.
        assertThatThrownBy(() -> staffAccess.login(SOKCHO_EMAIL, "branch-password"))
                .isInstanceOf(Exception.class);
        // 새 임시 비밀번호로 로그인된다.
        staffAccess.login(SOKCHO_EMAIL, temporaryPassword);
    }

    @Test
    void sameIdempotencyKeyDoesNotResetTwice() throws Exception {
        UUID target = staffId(SOKCHO_EMAIL);
        String first = resetPassword(target, "reset-sokcho", hqToken);
        assertThat(extract(first, "created")).isEqualTo("true");
        String firstPassword = extract(first, "temporaryPassword");

        // 같은 멱원 키 재호출은 비밀번호를 내려주지 않고 같은 대상을 돌려준다.
        String second = mvc.perform(MockMvcRequestBuilders.post("/api/staff/staff/" + target + "/password")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "reset-sokcho"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andReturn().getResponse().getContentAsString();

        assertThat(extract(second, "staffId")).isEqualTo(target.toString());
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(second)
                .get("temporaryPassword").isNull()).isTrue();

        // 첫 발급 비밀번호가 그대로 유효하다.
        staffAccess.login(SOKCHO_EMAIL, firstPassword);
    }

    @Test
    void newIdempotencyKeyWithinCooldownIsRejected() throws Exception {
        UUID target = staffId(SOKCHO_EMAIL);
        String first = resetPassword(target, "reset-sokcho", hqToken);
        String firstPassword = extract(first, "temporaryPassword");

        // 응답을 유실한 뒤 새 멱원 키로 같은 대상 재발급을 시도한다.
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/staff/" + target + "/password")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "reset-sokcho-retry"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAFF_PASSWORD_RESET_COOLDOWN"));

        // 비밀번호는 바뀌지 않는다.
        staffAccess.login(SOKCHO_EMAIL, firstPassword);

        Integer resets = jdbc.queryForObject(
                "select count(*) from staff_account_command where kind = 'RESET_PASSWORD'",
                Integer.class);
        assertThat(resets).isEqualTo(1);
    }

    @Test
    void branchStaffCannotResetPassword() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post(
                        "/api/staff/staff/" + staffId(HQ_EMAIL) + "/password")
                .header("X-Staff-Session", sokchoToken)
                .header("Idempotency-Key", "reset-hq"))
                .andExpect(status().isForbidden());

        Integer resets = jdbc.queryForObject(
                "select count(*) from staff_account_command where kind = 'RESET_PASSWORD'",
                Integer.class);
        assertThat(resets).isZero();
    }

    @Test
    void missingSessionCannotResetPassword() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post(
                        "/api/staff/staff/" + staffId(SOKCHO_EMAIL) + "/password")
                .header("Idempotency-Key", "reset-sokcho"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownStaffIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post(
                        "/api/staff/staff/" + UUID.randomUUID() + "/password")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "reset-unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STAFF_ACCOUNT_NOT_FOUND"));
    }

    @Test
    void resetRequiresIdempotencyKey() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post(
                        "/api/staff/staff/" + staffId(SOKCHO_EMAIL) + "/password")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isBadRequest());
    }

    private void clean() {
        jdbc.update("delete from staff_session");
        // staff_account_command가 staff_member를 참조하므로 가장 먼저 지운다.
        jdbc.update("delete from staff_account_command");
        jdbc.update("delete from staff_member where email in (?, ?, ?)", HQ_EMAIL, SOKCHO_EMAIL, NEW_EMAIL);
        jdbc.update("delete from hotel where id = ?", SOKCHO);
    }
}
