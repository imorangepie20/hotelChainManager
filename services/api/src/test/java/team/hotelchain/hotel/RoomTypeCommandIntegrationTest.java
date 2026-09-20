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
    void headquartersCreatesRoomTypeWithSeed() throws Exception {
        // 시드가 없으면 본사가 만든 유형은 고객 검색에 나타나지 않는다.
        // rate_plan·rate_day·inventory_day가 함께 만들어져야 즉시 예약 가능하다.
        String body = mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "create-seeded")
                .contentType("application/json")
                .content("{\"name\":\"디럭스\",\"maxOccupancy\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.seed.ratePlanName").value("기본 요금제"))
                .andExpect(jsonPath("$.seed.pricedDays").value(90))
                .andExpect(jsonPath("$.seed.defaultRateKrw").value(100000))
                .andExpect(jsonPath("$.seed.inventoryCapacity").value(8))
                .andExpect(jsonPath("$.seed.created").value(true))
                .andReturn().getResponse().getContentAsString();
        UUID roomTypeId = UUID.fromString(mapper.readTree(body).get("roomTypeId").asText());

        Integer rateDays = jdbc.queryForObject(
                "select count(*) from rate_day rd join rate_plan rp on rp.id = rd.rate_plan_id where rp.room_type_id = ?",
                Integer.class, roomTypeId);
        org.assertj.core.api.Assertions.assertThat(rateDays).isEqualTo(90);

        Integer inventoryDays = jdbc.queryForObject(
                "select count(*) from inventory_day where room_type_id = ?", Integer.class, roomTypeId);
        org.assertj.core.api.Assertions.assertThat(inventoryDays).isEqualTo(90);

        Integer amount = jdbc.queryForObject(
                "select min(amount_krw) from rate_day rd join rate_plan rp on rp.id = rd.rate_plan_id where rp.room_type_id = ?",
                Integer.class, roomTypeId);
        org.assertj.core.api.Assertions.assertThat(amount).isEqualTo(100000);

        Integer capacity = jdbc.queryForObject(
                "select min(capacity) from inventory_day where room_type_id = ?", Integer.class, roomTypeId);
        org.assertj.core.api.Assertions.assertThat(capacity).isEqualTo(8);
    }

    @Test
    void sameIdempotencyKeyDoesNotDuplicateSeedDays() throws Exception {
        // 멱원 재시도가 같은 일자를 두 번 만들면 고객이 같은 날짜의 객실을
        // 중복으로 보게 된다. ON CONFLICT DO NOTHING으로 막는다.
        String body = "{\"name\":\"디럭스\",\"maxOccupancy\":3}";
        String first = mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "create-seeded")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID roomTypeId = UUID.fromString(mapper.readTree(first).get("roomTypeId").asText());
        UUID firstRatePlan = UUID.fromString(mapper.readTree(first).get("seed").get("ratePlanId").asText());

        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "create-seeded")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.seed.ratePlanId").value(firstRatePlan.toString()))
                .andExpect(jsonPath("$.seed.pricedDays").value(90));

        Integer ratePlans = jdbc.queryForObject(
                "select count(*) from rate_plan where room_type_id = ?", Integer.class, roomTypeId);
        org.assertj.core.api.Assertions.assertThat(ratePlans).isEqualTo(1);

        Integer rateDays = jdbc.queryForObject(
                "select count(*) from rate_day rd join rate_plan rp on rp.id = rd.rate_plan_id where rp.room_type_id = ?",
                Integer.class, roomTypeId);
        org.assertj.core.api.Assertions.assertThat(rateDays).isEqualTo(90);

        Integer inventoryDays = jdbc.queryForObject(
                "select count(*) from inventory_day where room_type_id = ?", Integer.class, roomTypeId);
        org.assertj.core.api.Assertions.assertThat(inventoryDays).isEqualTo(90);
    }

    @Test
    void seedDaysStartAtClockMidnight() throws Exception {
        // 고객 검색이 체크인일을 오늘부터 잡으므로 시드 시작일은 Clock이 가리키는
        // 오늘이어야 한다. Clock이 UTC면 한국 시간 09:00에 어제 자정을 가리키므로
        // plusDays(1)로 하루를 미루면 오늘 도착 검색이 빈 결과를 돌려받는다.
        // 시작일은 시드가 만든 첫 일자와 Clock의 오늘 자정이 같은 날인지로 확인한다.
        String body = mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "create-seeded")
                .contentType("application/json")
                .content("{\"name\":\"디럭스\",\"maxOccupancy\":3}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID roomTypeId = UUID.fromString(mapper.readTree(body).get("roomTypeId").asText());

        java.time.LocalDate firstRateDay = jdbc.queryForObject(
                "select min(rd.stay_date) from rate_day rd join rate_plan rp on rp.id = rd.rate_plan_id where rp.room_type_id = ?",
                java.time.LocalDate.class, roomTypeId);
        java.time.LocalDate firstInventoryDay = jdbc.queryForObject(
                "select min(stay_date) from inventory_day where room_type_id = ?",
                java.time.LocalDate.class, roomTypeId);
        java.time.LocalDate lastRateDay = jdbc.queryForObject(
                "select max(rd.stay_date) from rate_day rd join rate_plan rp on rp.id = rd.rate_plan_id where rp.room_type_id = ?",
                java.time.LocalDate.class, roomTypeId);
        org.assertj.core.api.Assertions.assertThat(firstRateDay).isEqualTo(firstInventoryDay);
        // Period.getDays()는 달력 필드 차이(2개월 28일)만 돌려주므로 일수 차이는
        // ChronoUnit으로 재야 한다.
        org.assertj.core.api.Assertions.assertThat(
                java.time.temporal.ChronoUnit.DAYS.between(firstRateDay, lastRateDay))
                .isEqualTo(89);
    }

    @Test
    void seedUsesConfiguredAmountsAndDays() throws Exception {
        // 시드 금액·일수·재고는 환경 변수로 조정한다. 이미 만든 유형에는
        // 영향을 주지 않으므로 여기서는 만들어지는 값만 확인한다.
        String body = mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "create-seeded")
                .contentType("application/json")
                .content("{\"name\":\"디럭스\",\"maxOccupancy\":3}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID roomTypeId = UUID.fromString(mapper.readTree(body).get("roomTypeId").asText());

        Integer distinctAmounts = jdbc.queryForObject("""
                select count(distinct amount_krw) from rate_day rd
                 join rate_plan rp on rp.id = rd.rate_plan_id
                where rp.room_type_id = ?
                """, Integer.class, roomTypeId);
        org.assertj.core.api.Assertions.assertThat(distinctAmounts).isEqualTo(1);

        Integer inventoryCapacity = jdbc.queryForObject(
                "select min(capacity) from inventory_day where room_type_id = ?", Integer.class, roomTypeId);
        org.assertj.core.api.Assertions.assertThat(inventoryCapacity).isEqualTo(8);

        Integer heldOrConfirmed = jdbc.queryForObject(
                "select count(*) from inventory_day where room_type_id = ? and (held > 0 or confirmed > 0)",
                Integer.class, roomTypeId);
        org.assertj.core.api.Assertions.assertThat(heldOrConfirmed).isZero();
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
        // 시드가 rate_plan·inventory_day를 만들었으므로 room_type보다 먼저 지워야 한다.
        jdbc.update("delete from rate_day where rate_plan_id in (select id from rate_plan where room_type_id in (select id from room_type where hotel_id in (?, ?)))", SOKCHO, JEJU);
        jdbc.update("delete from rate_plan where room_type_id in (select id from room_type where hotel_id in (?, ?))", SOKCHO, JEJU);
        jdbc.update("delete from inventory_day where room_type_id in (select id from room_type where hotel_id in (?, ?))", SOKCHO, JEJU);
        jdbc.update("delete from room_type where hotel_id in (?, ?)", SOKCHO, JEJU);
        jdbc.update("delete from hotel where id in (?, ?)", SOKCHO, JEJU);
    }
}
