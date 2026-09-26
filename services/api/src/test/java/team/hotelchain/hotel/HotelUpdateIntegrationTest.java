package team.hotelchain.hotel;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;

import team.hotelchain.staff.StaffAccessService;

/**
 * 본사가 지점의 이름·지역·시간대를 바꾸는 동작을 검증한다.
 * <p>
 * 멱원은 두 갈래로 동작한다. 같은 키 재호출은 200 changed=false, 응답 유실 뒤 새 키로
 * 같은 내용을 보내도 같은 결과가 내려와야 한다.
 */
@SpringBootTest
class HotelUpdateIntegrationTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final UUID SOKCHO = UUID.fromString("18000000-0000-0000-0000-000000000001");
    private static final UUID JEJU = UUID.fromString("18000000-0000-0000-0000-000000000002");
    private static final String HQ_EMAIL = "hotel-update-hq@example.com";
    private static final String SOKCHO_EMAIL = "hotel-update-sokcho@example.com";

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

        jdbc.update("insert into hotel values (?, ?, ?, ?)", SOKCHO, "수정 속초", "속초", "Asia/Seoul");
        // 대소문자 중복 검증을 위해 영문 이름을 섞어 둔다.
        jdbc.update("insert into hotel values (?, ?, ?, ?)", JEJU, "Jeju Hotel", "제주도", "Asia/Seoul");

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
    void headquartersUpdatesName() throws Exception {
        updateHotel(SOKCHO, "update-name", "{\"name\":\"속초 오션\"}", status().isOk())
                .andExpect(jsonPath("$.changed").value(true))
                .andExpect(jsonPath("$.name").value("속초 오션"))
                .andExpect(jsonPath("$.region").value("속초"))
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"));

        String stored = jdbc.queryForObject(
                "select name from hotel where id = ?", String.class, SOKCHO);
        org.assertj.core.api.Assertions.assertThat(stored).isEqualTo("속초 오션");

        // 바꾸기 전 값이 이력에 남아야 한다.
        String previous = jdbc.queryForObject("""
                select previous_name from hotel_command
                 where hotel_id = ? and kind = 'UPDATE' and idempotency_key = 'update-name'
                """, String.class, SOKCHO);
        org.assertj.core.api.Assertions.assertThat(previous).isEqualTo("수정 속초");
    }

    @Test
    void headquartersUpdatesRegionAndTimezone() throws Exception {
        updateHotel(SOKCHO, "update-region", "{\"region\":\"강원\"}", status().isOk())
                .andExpect(jsonPath("$.changed").value(true))
                .andExpect(jsonPath("$.region").value("강원"));

        updateHotel(SOKCHO, "update-timezone", "{\"timezone\":\"Asia/Tokyo\"}", status().isOk())
                .andExpect(jsonPath("$.changed").value(true))
                .andExpect(jsonPath("$.timezone").value("Asia/Tokyo"));

        String storedTimezone = jdbc.queryForObject(
                "select timezone from hotel where id = ?", String.class, SOKCHO);
        org.assertj.core.api.Assertions.assertThat(storedTimezone).isEqualTo("Asia/Tokyo");
    }

    @Test
    void idempotencyReplayReturnsSameResult() throws Exception {
        // 같은 키 재호출은 200 changed=false로 같은 결과를 돌려준다.
        updateHotel(SOKCHO, "replay", "{\"name\":\"속초 오션\"}", status().isOk())
                .andExpect(jsonPath("$.changed").value(true));

        updateHotel(SOKCHO, "replay", "{\"name\":\"속초 오션\"}", status().isOk())
                .andExpect(jsonPath("$.changed").value(false))
                .andExpect(jsonPath("$.name").value("속초 오션"));

        // 이름이 한 번만 바뀌어야 한다.
        Integer commandCount = jdbc.queryForObject("""
                select count(*) from hotel_command
                 where hotel_id = ? and kind = 'UPDATE' and idempotency_key = 'replay'
                """, Integer.class, SOKCHO);
        org.assertj.core.api.Assertions.assertThat(commandCount).isEqualTo(1);
    }

    @Test
    void newKeyWithSameContentReturnsSameResult() throws Exception {
        // 응답을 잃은 뒤 새 키로 같은 내용을 보내도 SHA-256 지문이 같아 같은 결과를 돌려준다.
        updateHotel(SOKCHO, "first-key", "{\"name\":\"속초 오션\"}", status().isOk())
                .andExpect(jsonPath("$.changed").value(true));

        updateHotel(SOKCHO, "second-key", "{\"name\":\"속초 오션\"}", status().isOk())
                .andExpect(jsonPath("$.changed").value(false))
                .andExpect(jsonPath("$.name").value("속초 오션"));

        Integer nameCount = jdbc.queryForObject(
                "select count(*) from hotel where name = '속초 오션'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(nameCount).isEqualTo(1);
    }

    @Test
    void unchangedValuesDoNotTouchDatabase() throws Exception {
        // 현재 값과 같은 내용을 보내면 DB를 건드리지 않고 멱원 기록만 남긴다.
        updateHotel(SOKCHO, "no-op", "{\"name\":\"수정 속초\"}", status().isOk())
                .andExpect(jsonPath("$.changed").value(false));

        // 행은 그대로다.
        String stored = jdbc.queryForObject(
                "select name from hotel where id = ?", String.class, SOKCHO);
        org.assertj.core.api.Assertions.assertThat(stored).isEqualTo("수정 속초");

        // 멱원 기록은 있어야 재호출이 같은 결과를 찾을 수 있다.
        Integer commandCount = jdbc.queryForObject("""
                select count(*) from hotel_command
                 where hotel_id = ? and kind = 'UPDATE' and idempotency_key = 'no-op'
                """, Integer.class, SOKCHO);
        org.assertj.core.api.Assertions.assertThat(commandCount).isEqualTo(1);
    }

    @Test
    void nameFromAnotherHotelIsRejected() throws Exception {
        // 다른 지점이 쓰고 있는 이름으로 바꾸면 409로 거부한다.
        updateHotel(SOKCHO, "dup-name", "{\"name\":\"Jeju Hotel\"}", status().isConflict())
                .andExpect(jsonPath("$.code").value("HOTEL_NAME_CONFLICT"));

        String stored = jdbc.queryForObject(
                "select name from hotel where id = ?", String.class, SOKCHO);
        org.assertj.core.api.Assertions.assertThat(stored).isEqualTo("수정 속초");
    }

    @Test
    void keepingOwnNameIsAllowed() throws Exception {
        // 자기 자신의 이름을 그대로 보내는 것은 중복이 아니다.
        updateHotel(SOKCHO, "own-name", "{\"name\":\"수정 속초\",\"region\":\"강원\"}", status().isOk())
                .andExpect(jsonPath("$.changed").value(true))
                .andExpect(jsonPath("$.name").value("수정 속초"))
                .andExpect(jsonPath("$.region").value("강원"));
    }

    @Test
    void caseInsensitiveNameConflictIsRejected() throws Exception {
        // 대소문자만 다른 이름도 같은 지점으로 본다. 충돌하는 이름으로는 바꿀 수 없다.
        updateHotel(SOKCHO, "dup-upper", "{\"name\":\"JEJU HOTEL\"}", status().isConflict());
        updateHotel(SOKCHO, "dup-lower", "{\"name\":\"jeju hotel\",\"region\":\"강원\"}", status().isConflict());

        // 충돌로 취소됐으므로 이름·지역은 그대로다.
        String storedName = jdbc.queryForObject(
                "select name from hotel where id = ?", String.class, SOKCHO);
        org.assertj.core.api.Assertions.assertThat(storedName).isEqualTo("수정 속초");

        String storedRegion = jdbc.queryForObject(
                "select region from hotel where id = ?", String.class, SOKCHO);
        org.assertj.core.api.Assertions.assertThat(storedRegion).isEqualTo("속초");
    }

    @Test
    void unknownTimezoneIsRejected() throws Exception {
        // 잘못된 시간대는 재고 시드와 취소 마감 시각이 엉뚱한 날짜를 가리킨다.
        updateHotel(SOKCHO, "bad-timezone", "{\"timezone\":\"Mars/Olympus\"}", status().isBadRequest());

        String stored = jdbc.queryForObject(
                "select timezone from hotel where id = ?", String.class, SOKCHO);
        org.assertj.core.api.Assertions.assertThat(stored).isEqualTo("Asia/Seoul");
    }

    @Test
    void emptyBodyIsRejected() throws Exception {
        // 본문이 비어 있으면 아무것도 바꾸지 않는다. 빈 PATCH가 지점을 초기화하지 않는다.
        updateHotel(SOKCHO, "empty", "{}", status().isBadRequest());

        String stored = jdbc.queryForObject(
                "select name from hotel where id = ?", String.class, SOKCHO);
        org.assertj.core.api.Assertions.assertThat(stored).isEqualTo("수정 속초");
    }

    @Test
    void unknownHotelIsNotFound() throws Exception {
        UUID missing = UUID.fromString("19000000-0000-0000-0000-000000000099");
        updateHotel(missing, "missing", "{\"name\":\"없는 지점\"}", status().isNotFound());
    }

    @Test
    void branchStaffCannotUpdateHotel() throws Exception {
        // 지점 수정은 본사 전용이다. 지점 직원은 403이다.
        mvc.perform(patch("/api/staff/hotels/{hotelId}", SOKCHO)
                .header("X-Staff-Session", sokchoToken)
                .header("Idempotency-Key", "branch-update")
                .contentType("application/json")
                .content("{\"name\":\"지점 직원 이름\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingSessionIsRejected() throws Exception {
        mvc.perform(patch("/api/staff/hotels/{hotelId}", SOKCHO)
                .header("Idempotency-Key", "no-session")
                .contentType("application/json")
                .content("{\"name\":\"이름\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        mvc.perform(patch("/api/staff/hotels/{hotelId}", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .contentType("application/json")
                .content("{\"name\":\"이름\"}"))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions updateHotel(
            UUID hotelId, String idempotencyKey, String body, ResultMatcher expected) throws Exception {
        return mvc.perform(patch("/api/staff/hotels/{hotelId}", hotelId)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType("application/json")
                .content(body))
                .andExpect(expected);
    }

    private void clean() {
        // staff_session이 staff_member를 참조하고 staff_member가 hotel을 참조하므로
        // 세션 → 직원 → 지점 순으로 지운다. 이전 실행이 도중에 실패해 남은 행도 지운다.
        jdbc.update("delete from staff_session");
        jdbc.update("delete from hotel_command");
        jdbc.update("delete from staff_member where email in (?, ?) or hotel_id in (?, ?)",
                HQ_EMAIL, SOKCHO_EMAIL, SOKCHO, JEJU);
        jdbc.update("delete from hotel where id in (?, ?)", SOKCHO, JEJU);
        // 충돌 검증이 지점을 만들 수 있으므로 이름으로 남은 행도 지운다.
        jdbc.update("delete from hotel where name in ('속초 오션','없는 지점','지점 직원 이름','Jeju Hotel')");
    }
}
