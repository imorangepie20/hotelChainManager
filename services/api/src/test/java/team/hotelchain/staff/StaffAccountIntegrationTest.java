package team.hotelchain.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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

    @Test
    void headquartersChangesRoleToAnotherHeadquartersRole() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + staffId(SOKCHO_EMAIL))
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "update-to-publisher")
                .contentType("application/json")
                .content("{\"role\":\"HQ_PUBLISHER\",\"hotelId\":null}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("HQ_PUBLISHER"))
                .andExpect(jsonPath("$.hotelName").isEmpty())
                .andExpect(jsonPath("$.created").value(true));

        // 지점 직원 -> 본사 역할이면 소속 지점이 비워진다.
        UUID hotelId = jdbc.queryForObject(
                "select hotel_id from staff_member where email = ?", UUID.class, SOKCHO_EMAIL);
        assertThat(hotelId).isNull();
    }

    @Test
    void headquartersChangesRoleToBranchStaff() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + staffId(SOKCHO_EMAIL))
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "update-to-branch")
                .contentType("application/json")
                .content("{\"role\":\"BRANCH_STAFF\",\"hotelId\":\"" + SOKCHO + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("BRANCH_STAFF"))
                .andExpect(jsonPath("$.hotelName").value("계정 속초"));

        // 본사 역할 -> 지점 직원이면 소속 지점이 채워진다.
        UUID hotelId = jdbc.queryForObject(
                "select hotel_id from staff_member where email = ?", UUID.class, SOKCHO_EMAIL);
        assertThat(hotelId).isEqualTo(SOKCHO);
    }

    @Test
    void branchRoleWithoutHotelIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + staffId(HQ_EMAIL))
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "branch-without-hotel")
                .contentType("application/json")
                .content("{\"role\":\"BRANCH_STAFF\",\"hotelId\":null}"))
                .andExpect(status().isBadRequest());

        // 값이 바뀌지 않는다.
        String role = jdbc.queryForObject(
                "select role from staff_member where email = ?", String.class, HQ_EMAIL);
        assertThat(role).isEqualTo("HQ_ADMIN");
    }

    @Test
    void headquartersRoleWithHotelIsRejectedOnUpdate() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + staffId(SOKCHO_EMAIL))
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "hq-with-hotel")
                .contentType("application/json")
                .content("{\"role\":\"HQ_ADMIN\",\"hotelId\":\"" + SOKCHO + "\"}"))
                .andExpect(status().isBadRequest());

        String role = jdbc.queryForObject(
                "select role from staff_member where email = ?", String.class, SOKCHO_EMAIL);
        assertThat(role).isEqualTo("BRANCH_STAFF");
    }

    @Test
    void headquartersCannotChangeOwnRole() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + staffId(HQ_EMAIL))
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "self-role")
                .contentType("application/json")
                .content("{\"role\":\"HQ_EDITOR\",\"hotelId\":null}"))
                // 본인 권한을 내리면 본사 메뉴에 다시 들어오지 못한다.
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAFF_SELF_MODIFICATION_FORBIDDEN"));

        String role = jdbc.queryForObject(
                "select role from staff_member where email = ?", String.class, HQ_EMAIL);
        assertThat(role).isEqualTo("HQ_ADMIN");
    }

    @Test
    void unknownRoleIsRejectedOnUpdate() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + staffId(SOKCHO_EMAIL))
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "unknown-role")
                .contentType("application/json")
                .content("{\"role\":\"SUPER_ADMIN\",\"hotelId\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptyUpdateIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + staffId(SOKCHO_EMAIL))
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "empty-update")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownHotelIsRejectedOnUpdate() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + staffId(SOKCHO_EMAIL))
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "unknown-hotel")
                .contentType("application/json")
                .content("{\"role\":\"BRANCH_STAFF\",\"hotelId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void sameIdempotencyKeyReturnsSameUpdateResult() throws Exception {
        UUID target = staffId(SOKCHO_EMAIL);
        String body = "{\"role\":\"HQ_EDITOR\",\"hotelId\":null}";
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + target)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "repeat-update")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated());

        // 재호출은 200에 created=false로 같은 결과를 돌려준다.
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + target)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "repeat-update")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.role").value("HQ_EDITOR"));

        // 명령은 한 번만 저장된다.
        Integer commands = jdbc.queryForObject(
                "select count(*) from staff_account_command where staff_id = ? and kind = 'UPDATE'",
                Integer.class, target);
        assertThat(commands).isEqualTo(1);
    }

    @Test
    void sameUpdateWithNewIdempotencyKeyReturnsSameResult() throws Exception {
        UUID target = staffId(SOKCHO_EMAIL);
        String body = "{\"role\":\"HQ_EDITOR\",\"hotelId\":null}";
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + target)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "first-update")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated());

        // 응답 유실 뒤 클라이언트가 새 멱원 키로 같은 내용을 보낸다.
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + target)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "first-update-retry")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.role").value("HQ_EDITOR"));
    }

    @Test
    void updateRequiresIdempotencyKey() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + staffId(SOKCHO_EMAIL))
                .header("X-Staff-Session", hqToken)
                .contentType("application/json")
                .content("{\"role\":\"HQ_EDITOR\",\"hotelId\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void branchStaffCannotUpdateAccounts() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + staffId(HQ_EMAIL))
                .header("X-Staff-Session", sokchoToken)
                .header("Idempotency-Key", "branch-update")
                .contentType("application/json")
                .content("{\"role\":\"HQ_EDITOR\",\"hotelId\":null}"))
                .andExpect(status().isForbidden());

        // 값이 바뀌지 않는다.
        String role = jdbc.queryForObject(
                "select role from staff_member where email = ?", String.class, HQ_EMAIL);
        assertThat(role).isEqualTo("HQ_ADMIN");
    }

    @Test
    void missingSessionCannotUpdate() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + staffId(SOKCHO_EMAIL))
                .header("Idempotency-Key", "no-session-update")
                .contentType("application/json")
                .content("{\"role\":\"HQ_EDITOR\",\"hotelId\":null}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateUnknownStaffIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + UUID.randomUUID())
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "update-unknown")
                .contentType("application/json")
                .content("{\"role\":\"HQ_EDITOR\",\"hotelId\":null}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STAFF_ACCOUNT_NOT_FOUND"));
    }

    @Test
    void headquartersDeactivatesABranchAccount() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + staffId(SOKCHO_EMAIL) + "/active")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "deactivate-sokcho")
                .contentType("application/json")
                .content("{\"active\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.created").value(true));

        // 목록이 비활성 상태를 내려준다.
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/staff")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == '" + SOKCHO_EMAIL + "')].active")
                        .value(org.hamcrest.Matchers.hasItem(false)));

        // 비활성 직원이 남겨둔 세션이 즉시 끊긴다.
        Integer sessions = jdbc.queryForObject(
                "select count(*) from staff_session where staff_id = ?", Integer.class, staffId(SOKCHO_EMAIL));
        assertThat(sessions).isZero();
    }

    @Test
    void deactivatedAccountCannotUseItsSession() throws Exception {
        UUID target = staffId(SOKCHO_EMAIL);
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + target + "/active")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "deactivate-then-use")
                .contentType("application/json")
                .content("{\"active\":false}"))
                .andExpect(status().isCreated());

        // 세션이 삭제됐으므로 다른 API 호출도 거부된다.
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/staff")
                .header("X-Staff-Session", sokchoToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatedAccountCannotLogIn() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + staffId(SOKCHO_EMAIL) + "/active")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "deactivate-then-login")
                .contentType("application/json")
                .content("{\"active\":false}"))
                .andExpect(status().isCreated());

        // 비밀번호가 맞아도 로그인을 거부한다. 401이 아니라 403이다.
        assertThatThrownBy(() -> staffAccess.login(SOKCHO_EMAIL, "branch-password"))
                .isInstanceOf(StaffAccessDeniedException.class);
    }

    @Test
    void headquartersReactivatesTheAccount() throws Exception {
        UUID target = staffId(SOKCHO_EMAIL);
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + target + "/active")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "deactivate-for-reactivate")
                .contentType("application/json")
                .content("{\"active\":false}"))
                .andExpect(status().isCreated());

        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + target + "/active")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "reactivate")
                .contentType("application/json")
                .content("{\"active\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true));

        // 다시 로그인할 수 있다.
        staffAccess.login(SOKCHO_EMAIL, "branch-password");
    }

    @Test
    void deactivatingAnInactiveAccountIsRejected() throws Exception {
        UUID target = staffId(SOKCHO_EMAIL);
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + target + "/active")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "deactivate-once")
                .contentType("application/json")
                .content("{\"active\":false}"))
                .andExpect(status().isCreated());

        // 같은 상태를 요청해도 여전히 비활성이다.
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + target + "/active")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "deactivate-twice")
                .contentType("application/json")
                .content("{\"active\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true));

        // 상태가 바뀌지 않는 요청은 멱원 재시도와 같은 결과를 돌려준다.
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + target + "/active")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "deactivate-twice-retry")
                .contentType("application/json")
                .content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void sameActivationIdempotencyKeyReturnsSameResult() throws Exception {
        UUID target = staffId(SOKCHO_EMAIL);
        String body = "{\"active\":false}";
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + target + "/active")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "repeat-deactivate")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated());

        // 재호출은 200에 created=false로 같은 결과를 돌려준다.
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + target + "/active")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "repeat-deactivate")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.active").value(false));

        // 명령은 한 번만 저장된다.
        Integer commands = jdbc.queryForObject(
                "select count(*) from staff_account_command where staff_id = ? and kind in ('ACTIVATE','DEACTIVATE')",
                Integer.class, target);
        assertThat(commands).isEqualTo(1);
    }

    @Test
    void sameActivationWithNewIdempotencyKeyReturnsSameResult() throws Exception {
        UUID target = staffId(SOKCHO_EMAIL);
        String body = "{\"active\":false}";
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + target + "/active")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "first-deactivate")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated());

        // 응답 유실 뒤 클라이언트가 새 멱원 키로 같은 내용을 보낸다.
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + target + "/active")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "first-deactivate-retry")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void activationRequiresIdempotencyKey() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + staffId(SOKCHO_EMAIL) + "/active")
                .header("X-Staff-Session", hqToken)
                .contentType("application/json")
                .content("{\"active\":false}"))
                .andExpect(status().isBadRequest());

        // 값이 바뀌지 않는다.
        Boolean active = jdbc.queryForObject(
                "select active from staff_member where email = ?", Boolean.class, SOKCHO_EMAIL);
        assertThat(active).isTrue();
    }

    @Test
    void headquartersCannotDeactivateOwnAccount() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + staffId(HQ_EMAIL) + "/active")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "self-deactivate")
                .contentType("application/json")
                .content("{\"active\":false}"))
                // 본인을 비활성하면 본사 메뉴에 다시 들어오지 못한다.
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAFF_SELF_MODIFICATION_FORBIDDEN"));

        Boolean active = jdbc.queryForObject(
                "select active from staff_member where email = ?", Boolean.class, HQ_EMAIL);
        assertThat(active).isTrue();
    }

    @Test
    void branchStaffCannotActivateAccounts() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + staffId(HQ_EMAIL) + "/active")
                .header("X-Staff-Session", sokchoToken)
                .header("Idempotency-Key", "branch-deactivate")
                .contentType("application/json")
                .content("{\"active\":false}"))
                .andExpect(status().isForbidden());

        Boolean active = jdbc.queryForObject(
                "select active from staff_member where email = ?", Boolean.class, HQ_EMAIL);
        assertThat(active).isTrue();
    }

    @Test
    void missingSessionCannotActivate() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + staffId(SOKCHO_EMAIL) + "/active")
                .header("Idempotency-Key", "no-session-activate")
                .contentType("application/json")
                .content("{\"active\":false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void activationUnknownStaffIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + UUID.randomUUID() + "/active")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "activate-unknown")
                .contentType("application/json")
                .content("{\"active\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STAFF_ACCOUNT_NOT_FOUND"));
    }

    @Test
    void headquartersDeletesABranchAccount() throws Exception {
        UUID target = staffId(SOKCHO_EMAIL);
        mvc.perform(MockMvcRequestBuilders.delete("/api/staff/staff/" + target)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "delete-sokcho"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deleted").value(true));

        // 행은 남고 식별자만 비워진다. 감사 이력 참조가 끊기지 않는다.
        Integer rows = jdbc.queryForObject(
                "select count(*) from staff_member where id = ?", Integer.class, target);
        assertThat(rows).isEqualTo(1);

        String email = jdbc.queryForObject(
                "select email from staff_member where id = ?", String.class, target);
        assertThat(email).startsWith("deleted-");
        assertThat(email).endsWith("@deleted.local");

        String displayName = jdbc.queryForObject(
                "select display_name from staff_member where id = ?", String.class, target);
        assertThat(displayName).isEqualTo("삭제된 직원");

        String role = jdbc.queryForObject(
                "select role from staff_member where id = ?", String.class, target);
        assertThat(role).isEqualTo("REMOVED");

        // 삭제된 계정은 직원 목록에서 빠진다.
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/staff")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void deletedAccountCannotLogIn() throws Exception {
        UUID target = staffId(SOKCHO_EMAIL);
        mvc.perform(MockMvcRequestBuilders.delete("/api/staff/staff/" + target)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "delete-then-login"))
                .andExpect(status().isCreated());

        // 비밀번호 해시가 비워졌으므로 어떤 비밀번호와도 맞지 않는다.
        assertThatThrownBy(() -> staffAccess.login(SOKCHO_EMAIL, "branch-password"))
                .isInstanceOf(StaffAuthenticationException.class);
        // 자리 표시자 이메일로는 로그인 자체가 안 된다.
        assertThatThrownBy(() -> staffAccess.login(
                "deleted-" + target + "@deleted.local", "branch-password"))
                .isInstanceOf(StaffAuthenticationException.class);
    }

    @Test
    void deletedAccountSessionIsRemoved() throws Exception {
        UUID target = staffId(SOKCHO_EMAIL);
        mvc.perform(MockMvcRequestBuilders.delete("/api/staff/staff/" + target)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "delete-session"))
                .andExpect(status().isCreated());

        // 삭제된 직원이 남겨둔 세션은 즉시 지워진다.
        Integer sessions = jdbc.queryForObject(
                "select count(*) from staff_session where staff_id = ?", Integer.class, target);
        assertThat(sessions).isZero();

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/staff")
                .header("X-Staff-Session", sokchoToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletedAccountEmailCanBeReused() throws Exception {
        UUID target = staffId(SOKCHO_EMAIL);
        mvc.perform(MockMvcRequestBuilders.delete("/api/staff/staff/" + target)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "delete-reuse"))
                .andExpect(status().isCreated());

        // 삭제된 직원의 이메일 자리가 비워졌으므로 새 직원이 물려받을 수 있다.
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/staff")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "recreate-sokcho")
                .contentType("application/json")
                .content("{\"email\":\"" + SOKCHO_EMAIL + "\",\"displayName\":\"새 속초\",\"role\":\"BRANCH_STAFF\",\"hotelId\":\"" + SOKCHO + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(SOKCHO_EMAIL));
    }

    @Test
    void sameDeletionIdempotencyKeyReturnsSameResult() throws Exception {
        UUID target = staffId(SOKCHO_EMAIL);
        mvc.perform(MockMvcRequestBuilders.delete("/api/staff/staff/" + target)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "delete-once"))
                .andExpect(status().isCreated());

        // 재호출은 200에 deleted=false로 같은 결과를 돌려준다.
        mvc.perform(MockMvcRequestBuilders.delete("/api/staff/staff/" + target)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "delete-once"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(false));

        // 명령은 한 번만 저장된다.
        Integer commands = jdbc.queryForObject(
                "select count(*) from staff_account_command where staff_id = ? and kind = 'DELETE'",
                Integer.class, target);
        assertThat(commands).isEqualTo(1);
    }

    @Test
    void sameDeletionWithNewIdempotencyKeyReturnsSameResult() throws Exception {
        UUID target = staffId(SOKCHO_EMAIL);
        mvc.perform(MockMvcRequestBuilders.delete("/api/staff/staff/" + target)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "delete-first"))
                .andExpect(status().isCreated());

        // 응답 유실 뒤 클라이언트가 새 멱원 키로 같은 내용을 보낸다.
        mvc.perform(MockMvcRequestBuilders.delete("/api/staff/staff/" + target)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "delete-retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(false));

        Integer commands = jdbc.queryForObject(
                "select count(*) from staff_account_command where staff_id = ? and kind = 'DELETE'",
                Integer.class, target);
        assertThat(commands).isEqualTo(1);
    }

    @Test
    void deletionRequiresIdempotencyKey() throws Exception {
        mvc.perform(MockMvcRequestBuilders.delete("/api/staff/staff/" + staffId(SOKCHO_EMAIL))
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isBadRequest());

        // 값이 바뀌지 않는다.
        String role = jdbc.queryForObject(
                "select role from staff_member where email = ?", String.class, SOKCHO_EMAIL);
        assertThat(role).isEqualTo("BRANCH_STAFF");
    }

    @Test
    void headquartersCannotDeleteOwnAccount() throws Exception {
        mvc.perform(MockMvcRequestBuilders.delete("/api/staff/staff/" + staffId(HQ_EMAIL))
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "self-delete"))
                // 본인을 삭제하면 본사 메뉴에 다시 들어오지 못한다.
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAFF_SELF_MODIFICATION_FORBIDDEN"));

        String role = jdbc.queryForObject(
                "select role from staff_member where email = ?", String.class, HQ_EMAIL);
        assertThat(role).isEqualTo("HQ_ADMIN");
    }

    @Test
    void branchStaffCannotDeleteAccounts() throws Exception {
        mvc.perform(MockMvcRequestBuilders.delete("/api/staff/staff/" + staffId(HQ_EMAIL))
                .header("X-Staff-Session", sokchoToken)
                .header("Idempotency-Key", "branch-delete"))
                .andExpect(status().isForbidden());

        String role = jdbc.queryForObject(
                "select role from staff_member where email = ?", String.class, HQ_EMAIL);
        assertThat(role).isEqualTo("HQ_ADMIN");
    }

    @Test
    void missingSessionCannotDelete() throws Exception {
        mvc.perform(MockMvcRequestBuilders.delete("/api/staff/staff/" + staffId(SOKCHO_EMAIL))
                .header("Idempotency-Key", "no-session-delete"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletionUnknownStaffIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.delete("/api/staff/staff/" + UUID.randomUUID())
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "delete-unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STAFF_ACCOUNT_NOT_FOUND"));
    }

    @Test
    void alreadyDeletedAccountReplaysSameResult() throws Exception {
        UUID target = staffId(SOKCHO_EMAIL);
        mvc.perform(MockMvcRequestBuilders.delete("/api/staff/staff/" + target)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "delete-twice-setup"))
                .andExpect(status().isCreated());

        // 같은 본사가 같은 계정을 새 멱원 키로 다시 지우려 해도 같은 결과를
        // 돌려준다. 이미 비워진 계정을 404로 착각하게 만들지 않는다.
        mvc.perform(MockMvcRequestBuilders.delete("/api/staff/staff/" + target)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "delete-twice-new"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(false));

        // 명령은 한 번만 저장된다.
        Integer commands = jdbc.queryForObject(
                "select count(*) from staff_account_command where staff_id = ? and kind = 'DELETE'",
                Integer.class, target);
        assertThat(commands).isEqualTo(1);
    }

    @Test
    void deletionIsBlockedByOpenChangeRequest() throws Exception {
        UUID target = staffId(SOKCHO_EMAIL);
        // 종료되지 않은 예약 변경 요청이 근거로 남아 있으면 지울 수 없다.
        UUID changeRequest = UUID.randomUUID();
        jdbc.update("""
                insert into reservation_change_request
                    (id, reservation_id, hotel_id, base_operation_revision, status, settlement_direction,
                     requested_by, idempotency_key, request_hash,
                     previous_check_in, previous_check_out, previous_room_type_id, previous_rate_plan_id,
                     target_check_in, target_check_out, target_room_type_id, target_rate_plan_id,
                     rooms, adults, children, approval_expires_at)
                values (?, ?, ?, 0, 'PENDING_APPROVAL', 'NONE', ?, 'k', 'h',
                    date '2027-01-01', date '2027-01-03', ?, ?,
                    date '2027-01-02', date '2027-01-04', ?, ?,
                    1, 2, 0, now() + interval '1 day')
                """, changeRequest, reservationForSokcho(), SOKCHO, target,
                roomTypeId(), ratePlanId(), roomTypeId(), ratePlanId());

        mvc.perform(MockMvcRequestBuilders.delete("/api/staff/staff/" + target)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "delete-with-change-request"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAFF_DELETION_CONFLICT"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("예약 변경 요청")));

        // 계정은 그대로다.
        String role = jdbc.queryForObject(
                "select role from staff_member where id = ?", String.class, target);
        assertThat(role).isEqualTo("BRANCH_STAFF");
    }

    @Test
    void deletionIgnoresClosedChangeRequests() throws Exception {
        UUID target = staffId(SOKCHO_EMAIL);
        jdbc.update("""
                insert into reservation_change_request
                    (id, reservation_id, hotel_id, base_operation_revision, status, settlement_direction,
                     requested_by, idempotency_key, request_hash,
                     previous_check_in, previous_check_out, previous_room_type_id, previous_rate_plan_id,
                     target_check_in, target_check_out, target_room_type_id, target_rate_plan_id,
                     rooms, adults, children, approval_expires_at)
                values (?, ?, ?, 0, 'COMPLETED', 'NONE', ?, 'k2', 'h2',
                    date '2027-01-01', date '2027-01-03', ?, ?,
                    date '2027-01-02', date '2027-01-04', ?, ?,
                    1, 2, 0, now() + interval '1 day')
                """, UUID.randomUUID(), reservationForSokcho(), SOKCHO, target,
                roomTypeId(), ratePlanId(), roomTypeId(), ratePlanId());

        // 완료된 요청은 과거 기록이므로 삭제를 막지 않는다.
        mvc.perform(MockMvcRequestBuilders.delete("/api/staff/staff/" + target)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "delete-after-completed"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deleted").value(true));
    }

    @Test
    void deletionIsBlockedByActiveSettlementRun() throws Exception {
        UUID target = staffId(SOKCHO_EMAIL);
        jdbc.update("""
                insert into toss_settlement_run
                    (id, provider, merchant_account, sold_date_from, sold_date_to, status,
                     current_sold_date, snapshot_count, matched_count, mismatch_count,
                     pending_count, attempt_count, next_attempt_at,
                     requested_by, idempotency_key, request_hash)
                values (?, 'TOSS_LIVE', 'test', date '2027-01-01', date '2027-01-02', 'PROCESSING',
                    date '2027-01-01', 0, 0, 0, 0, 0, now(),
                    ?, 'settle-k', 'settle-h')
                """, UUID.randomUUID(), target);

        mvc.perform(MockMvcRequestBuilders.delete("/api/staff/staff/" + target)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "delete-with-settlement"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAFF_DELETION_CONFLICT"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("정산 실행")));

        String role = jdbc.queryForObject(
                "select role from staff_member where id = ?", String.class, target);
        assertThat(role).isEqualTo("BRANCH_STAFF");
    }

    @Test
    void staffUpdatesOwnDisplayName() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/me")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "self-name")
                .contentType("application/json")
                .content("{\"displayName\":\"바뀐 본사\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("바뀐 본사"))
                .andExpect(jsonPath("$.email").value(HQ_EMAIL))
                .andExpect(jsonPath("$.changed").value(true));

        String displayName = jdbc.queryForObject(
                "select display_name from staff_member where email = ?", String.class, HQ_EMAIL);
        assertThat(displayName).isEqualTo("바뀐 본사");
    }

    @Test
    void staffUpdatesOwnPassword() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/me")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "self-password")
                .contentType("application/json")
                .content("{\"currentPassword\":\"hq-password\",\"newPassword\":\"new-hq-password\"}"))
                .andExpect(status().isCreated());

        // 새 비밀번호로 로그인된다.
        staffAccess.login(HQ_EMAIL, "new-hq-password");
    }

    @Test
    void passwordChangeKeepsCurrentSession() throws Exception {
        // 다른 기기에서 로그인한 세션을 하나 더 만든다.
        String otherSession = staffAccess.login(HQ_EMAIL, "hq-password").token();

        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/me")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "self-password-two-sessions")
                .contentType("application/json")
                .content("{\"currentPassword\":\"hq-password\",\"newPassword\":\"new-hq-password\"}"))
                .andExpect(status().isCreated());

        // 현재 세션은 살아 있다.
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/me")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk());

        // 다른 기기의 세션은 끊겼다.
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/me")
                .header("X-Staff-Session", otherSession))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordChangeWithWrongCurrentPasswordIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/me")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "self-wrong-current")
                .contentType("application/json")
                .content("{\"currentPassword\":\"wrong-password\",\"newPassword\":\"new-hq-password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STAFF_PASSWORD_MISMATCH"));

        // 비밀번호는 바뀌지 않는다.
        staffAccess.login(HQ_EMAIL, "hq-password");
    }

    @Test
    void shortNewPasswordIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/me")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "self-short-password")
                .contentType("application/json")
                .content("{\"currentPassword\":\"hq-password\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());

        staffAccess.login(HQ_EMAIL, "hq-password");
    }

    @Test
    void emptySelfUpdateIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/me")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "self-empty")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isBadRequest());

        String displayName = jdbc.queryForObject(
                "select display_name from staff_member where email = ?", String.class, HQ_EMAIL);
        assertThat(displayName).isEqualTo("본사 관리자");
    }

    @Test
    void newPasswordWithoutCurrentPasswordIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/me")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "self-no-current")
                .contentType("application/json")
                .content("{\"newPassword\":\"new-hq-password\"}"))
                .andExpect(status().isBadRequest());

        staffAccess.login(HQ_EMAIL, "hq-password");
    }

    @Test
    void sameSelfUpdateIdempotencyKeyReturnsSameResult() throws Exception {
        String body = "{\"displayName\":\"멱원 본사\"}";
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/me")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "self-once")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated());

        // 재호출은 200에 changed=false로 같은 결과를 돌려준다.
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/me")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "self-once")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(false));
    }

    @Test
    void sameSelfUpdateWithNewIdempotencyKeyReturnsSameResult() throws Exception {
        String body = "{\"displayName\":\"재시도 본사\"}";
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/me")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "self-first")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated());

        // 응답 유실 뒤 클라이언트가 새 멱원 키로 같은 내용을 보낸다.
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/me")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "self-retry")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(false))
                .andExpect(jsonPath("$.displayName").value("재시도 본사"));
    }

    @Test
    void selfUpdateRequiresIdempotencyKey() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/me")
                .header("X-Staff-Session", hqToken)
                .contentType("application/json")
                .content("{\"displayName\":\"키 없음\"}"))
                .andExpect(status().isBadRequest());

        String displayName = jdbc.queryForObject(
                "select display_name from staff_member where email = ?", String.class, HQ_EMAIL);
        assertThat(displayName).isEqualTo("본사 관리자");
    }

    @Test
    void missingSessionCannotSelfUpdate() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/me")
                .header("Idempotency-Key", "self-no-session")
                .contentType("application/json")
                .content("{\"displayName\":\"세션 없음\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void branchStaffCanUpdateOwnAccount() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/me")
                .header("X-Staff-Session", sokchoToken)
                .header("Idempotency-Key", "branch-self")
                .contentType("application/json")
                .content("{\"displayName\":\"바뀐 속초\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("바뀐 속초"));
    }

    @Test
    void deactivatedAccountCannotSelfUpdate() throws Exception {
        UUID target = staffId(SOKCHO_EMAIL);
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/" + target + "/active")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "deactivate-before-self")
                .contentType("application/json")
                .content("{\"active\":false}"))
                .andExpect(status().isCreated());

        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/staff/me")
                .header("X-Staff-Session", sokchoToken)
                .header("Idempotency-Key", "self-after-deactivate")
                .contentType("application/json")
                .content("{\"displayName\":\"비활성 속초\"}"))
                // 세션이 지워졌으므로 401이다.
                .andExpect(status().isUnauthorized());
    }

    private UUID reservationForSokcho() {
        // 예약 변경 요청은 예약·객실 유형·요금제를 참조해야 한다.
        UUID roomTypeId = roomTypeId();
        UUID ratePlanId = ratePlanIdOf(roomTypeId);
        UUID reservationId = UUID.randomUUID();
        jdbc.update("""
                insert into reservation
                    (id, room_type_id, rate_plan_id, check_in, check_out, rooms,
                     adults, children, status, total_krw, currency, expires_at,
                     guest_name, guest_email, management_token_hash, policy_snapshot)
                values (?, ?, ?, date '2027-01-01', date '2027-01-03', 1,
                     2, 0, 'CONFIRMED', 200000, 'KRW', now() + interval '1 hour',
                     '손님', 'guest@example.com', ?, '{}')
                """, reservationId, roomTypeId, ratePlanId, hashOf("staff-delete-token"));
        return reservationId;
    }

    private static String hashOf(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private UUID roomTypeId() {
        UUID roomTypeId = UUID.randomUUID();
        jdbc.update("insert into room_type (id, hotel_id, name, max_occupancy) values (?, ?, '삭제 검증용', 2)",
                roomTypeId, SOKCHO);
        return roomTypeId;
    }

    private UUID ratePlanId() {
        return ratePlanIdOf(roomTypeId());
    }

    private UUID ratePlanIdOf(UUID roomTypeId) {
        UUID ratePlanId = UUID.randomUUID();
        jdbc.update("""
                insert into rate_plan (id, room_type_id, name, breakfast_included, policy_version)
                values (?, ?, '삭제 검증용 요금제', false, 'FLEX-2026-01')
                """, ratePlanId, roomTypeId);
        return ratePlanId;
    }

    private void clean() {
        jdbc.update("delete from staff_session");
        // staff_account_command가 staff_member를 참조하므로 가장 먼저 지운다.
        jdbc.update("delete from staff_account_command");
        // 삭제 검증용으로 만든 예약 변경 요청·정산 실행·예약·유형도 지운다.
        jdbc.update("delete from toss_settlement_run");
        jdbc.update("delete from reservation_change_request");
        jdbc.update("delete from reservation");
        jdbc.update("delete from rate_plan");
        jdbc.update("delete from room_type where hotel_id = ?", SOKCHO);
        // 삭제된 계정은 이메일이 자리 표시자로 바뀌므로 id로 지운다.
        jdbc.update("delete from staff_member where email in (?, ?, ?) or role = 'REMOVED'", HQ_EMAIL, SOKCHO_EMAIL, NEW_EMAIL);
        jdbc.update("delete from hotel where id = ?", SOKCHO);
    }
}
