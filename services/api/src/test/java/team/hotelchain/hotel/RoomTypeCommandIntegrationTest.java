package team.hotelchain.hotel;

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

import com.fasterxml.jackson.databind.ObjectMapper;

import team.hotelchain.staff.StaffAccessService;

@SpringBootTest
class RoomTypeCommandIntegrationTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final UUID SOKCHO = UUID.fromString("16000000-0000-0000-0000-000000000001");
    private static final UUID JEJU = UUID.fromString("16000000-0000-0000-0000-000000000002");
    private static final UUID STANDARD = UUID.fromString("26000000-0000-0000-0000-000000000001");
    private static final String HQ_EMAIL = "room-type-hq@example.com";
    private static final String SOKCHO_EMAIL = "room-type-sokcho@example.com";

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

        jdbc.update("insert into hotel values (?, ?, ?, ?)", SOKCHO, "쓰기 속초", "속초", "Asia/Seoul");
        jdbc.update("insert into hotel values (?, ?, ?, ?)", JEJU, "쓰기 제주", "제주도", "Asia/Seoul");
        jdbc.update("insert into room_type values (?, ?, ?, ?)", STANDARD, SOKCHO, "스탠다드", 2);

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
    void headquartersCreatesRoomType() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "create-deluxe")
                .contentType("application/json")
                .content("{\"name\":\"디럭스\",\"maxOccupancy\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hotelId").value(SOKCHO.toString()))
                .andExpect(jsonPath("$.name").value("디럭스"))
                .andExpect(jsonPath("$.maxOccupancy").value(3))
                .andExpect(jsonPath("$.created").value(true));

        Integer count = jdbc.queryForObject(
                "select count(*) from room_type where hotel_id = ?", Integer.class, SOKCHO);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(2);
    }

    @Test
    void branchStaffCannotCreateRoomType() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types", SOKCHO)
                .header("X-Staff-Session", sokchoToken)
                .header("Idempotency-Key", "create-deluxe")
                .contentType("application/json")
                .content("{\"name\":\"디럭스\",\"maxOccupancy\":3}"))
                .andExpect(status().isForbidden());

        Integer count = jdbc.queryForObject(
                "select count(*) from room_type where hotel_id = ?", Integer.class, SOKCHO);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
    }

    @Test
    void missingSessionIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types", SOKCHO)
                .header("Idempotency-Key", "create-deluxe")
                .contentType("application/json")
                .content("{\"name\":\"디럭스\",\"maxOccupancy\":3}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownHotelIsNotFound() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types", UUID.randomUUID())
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "create-deluxe")
                .contentType("application/json")
                .content("{\"name\":\"디럭스\",\"maxOccupancy\":3}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void blankNameIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "create-blank")
                .contentType("application/json")
                .content("{\"name\":\"  \",\"maxOccupancy\":3}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void longNameIsRejected() throws Exception {
        String name = "객".repeat(101);
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "create-long")
                .contentType("application/json")
                .content("{\"name\":\"" + name + "\",\"maxOccupancy\":3}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void occupancyOutsideRangeIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "create-zero")
                .contentType("application/json")
                .content("{\"name\":\"디럭스\",\"maxOccupancy\":0}"))
                .andExpect(status().isBadRequest());

        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "create-twenty-one")
                .contentType("application/json")
                .content("{\"name\":\"디럭스\",\"maxOccupancy\":21}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .contentType("application/json")
                .content("{\"name\":\"디럭스\",\"maxOccupancy\":3}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sameIdempotencyKeyReturnsSameRoomType() throws Exception {
        String body = "{\"name\":\"디럭스\",\"maxOccupancy\":3}";
        String first = mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "create-deluxe")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(true))
                .andReturn().getResponse().getContentAsString();

        String second = mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "create-deluxe")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andReturn().getResponse().getContentAsString();

        // 같은 멱원 키의 재호출은 created 플래그만 빼고 같은 객실 유형을 돌려준다.
        org.assertj.core.api.Assertions.assertThat(extractRoomTypeId(second)).isEqualTo(extractRoomTypeId(first));

        Integer count = jdbc.queryForObject(
                "select count(*) from room_type where hotel_id = ?", Integer.class, SOKCHO);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(2);
    }

    @Test
    void sameRequestWithNewIdempotencyKeyReturnsSameRoomType() throws Exception {
        String body = "{\"name\":\"디럭스\",\"maxOccupancy\":3}";
        String first = mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "create-deluxe")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // 응답 유실 뒤 클라이언트가 새 멱원 키로 같은 내용을 보낸다.
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "create-deluxe-retry")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("디럭스"))
                .andExpect(jsonPath("$.created").value(false));

        Integer count = jdbc.queryForObject(
                "select count(*) from room_type where hotel_id = ?", Integer.class, SOKCHO);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(2);
    }

    @Test
    void differentStaffCreatesSeparateRoomType() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "create-deluxe")
                .contentType("application/json")
                .content("{\"name\":\"디럭스\",\"maxOccupancy\":3}"))
                .andExpect(status().isCreated());

        // 다른 본사 관리자의 같은 내용 요청은 별도 요청으로 새 객실 유형을 만든다.
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "room-type-hq2@example.com", "본사 관리자 2", encoder.encode("hq-password"), "HQ_ADMIN", null);
        String otherToken = staffAccess.login("room-type-hq2@example.com", "hq-password").token();

        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types", SOKCHO)
                .header("X-Staff-Session", otherToken)
                .header("Idempotency-Key", "create-deluxe")
                .contentType("application/json")
                .content("{\"name\":\"디럭스\",\"maxOccupancy\":3}"))
                .andExpect(status().isCreated());

        Integer count = jdbc.queryForObject(
                "select count(*) from room_type where hotel_id = ?", Integer.class, SOKCHO);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(3);
    }

    private String extractRoomTypeId(String body) throws Exception {
        return mapper.readTree(body).get("roomTypeId").asText();
    }

    private void clean() {
        jdbc.update("delete from staff_session");
        // room_type_command가 staff_member와 room_type를 참조하므로 가장 먼저 지운다.
        jdbc.update("delete from room_type_command where hotel_id in (?, ?)", SOKCHO, JEJU);
        jdbc.update("delete from staff_member");
        jdbc.update("delete from room_type where hotel_id in (?, ?)", SOKCHO, JEJU);
        jdbc.update("delete from hotel where id in (?, ?)", SOKCHO, JEJU);
    }
}
