package team.hotelchain.hotel;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
class RateCommandIntegrationTest {

    private static final UUID SOKCHO = UUID.fromString("16000000-0000-0000-0000-000000000001");
    private static final UUID JEJU = UUID.fromString("16000000-0000-0000-0000-000000000002");
    private static final UUID STANDARD = UUID.fromString("26000000-0000-0000-0000-000000000001");
    private static final UUID SUITE = UUID.fromString("26000000-0000-0000-0000-000000000002");
    private static final UUID UNSEEDED = UUID.fromString("26000000-0000-0000-0000-000000000003");
    private static final String HQ_EMAIL = "rate-command-hq@example.com";
    private static final String SOKCHO_EMAIL = "rate-command-sokcho@example.com";

    private static final LocalDate DAY = LocalDate.of(2026, 11, 1);

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

        jdbc.update("insert into hotel values (?, ?, ?, ?)", SOKCHO, "요금 쓰기 속초", "속초", "Asia/Seoul");
        jdbc.update("insert into hotel values (?, ?, ?, ?)", JEJU, "요금 쓰기 제주", "제주도", "Asia/Seoul");
        // V53 이후 room_type은 seed_breakfast_included·seed_default_rate_krw 열을 가진다.
        jdbc.update("insert into room_type (id, hotel_id, name, max_occupancy) values (?, ?, ?, ?)",
                STANDARD, SOKCHO, "스탠다드", 2);
        jdbc.update("insert into room_type (id, hotel_id, name, max_occupancy) values (?, ?, ?, ?)",
                SUITE, SOKCHO, "스위트", 4);
        // 요금제가 없는 유형. 조식·기본 요금 변경과 마찬가지로 가격 변경도 거부해야 한다.
        jdbc.update("insert into room_type (id, hotel_id, name, max_occupancy) values (?, ?, ?, ?)",
                UNSEEDED, SOKCHO, "시드 안 된 유형", 2);

        seedRateDays(STANDARD, "스탠다드 기본", 100_000);
        seedRateDays(SUITE, "스위트 기본", 200_000);

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), HQ_EMAIL, "본사 관리자", encoder.encode("hq-password"), "HQ_ADMIN", null);
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), SOKCHO_EMAIL, "속초 직원", encoder.encode("branch-password"), "BRANCH_STAFF", SOKCHO);

        hqToken = staffAccess.login(HQ_EMAIL, "hq-password").token();
        sokchoToken = staffAccess.login(SOKCHO_EMAIL, "branch-password").token();
    }

    private void seedRateDays(UUID roomTypeId, String planName, int amount) {
        UUID ratePlanId = UUID.randomUUID();
        jdbc.update("insert into rate_plan (id, room_type_id, name, breakfast_included, policy_version) values (?, ?, ?, ?, ?)",
                ratePlanId, roomTypeId, planName, false, "2026-01");
        for (int offset = 0; offset < 5; offset++) {
            // 주말 차등이 있는 것처럼 금액을 다르게 심는다.
            int dayAmount = amount + (offset % 2 == 0 ? 0 : 20_000);
            jdbc.update("insert into rate_day (rate_plan_id, stay_date, amount_krw) values (?, ?, ?)",
                    ratePlanId, DAY.plusDays(offset), dayAmount);
        }
    }

    @AfterEach
    void cleanAfter() {
        clean();
    }

    @Test
    void headquartersChangesSingleDayRate() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "change-single-day")
                .contentType("application/json")
                .content(rateBody(STANDARD, DAY, 120_000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomTypeId").value(STANDARD.toString()))
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.days", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.days[0].stayDate").value(DAY.toString()))
                .andExpect(jsonPath("$.days[0].amountKrw").value(120_000));

        Integer amount = jdbc.queryForObject(
                "select amount_krw from rate_day where rate_plan_id = ? and stay_date = ?",
                Integer.class, ratePlanId(STANDARD), DAY);
        org.assertj.core.api.Assertions.assertThat(amount).isEqualTo(120_000);
    }

    @Test
    void headquartersChangesMultipleDaysWithDifferentAmounts() throws Exception {
        // 주말·계절 차등을 본사가 직접 만드는 동작이다.
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "change-multi-day")
                .contentType("application/json")
                .content(rateBody(STANDARD, java.util.List.of(
                        new RateAdjustRequest.DayRate(DAY, 110_000),
                        new RateAdjustRequest.DayRate(DAY.plusDays(1), 150_000)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.days", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.days[0].amountKrw").value(110_000))
                .andExpect(jsonPath("$.days[1].amountKrw").value(150_000));

        Integer first = jdbc.queryForObject(
                "select amount_krw from rate_day where rate_plan_id = ? and stay_date = ?",
                Integer.class, ratePlanId(STANDARD), DAY);
        Integer second = jdbc.queryForObject(
                "select amount_krw from rate_day where rate_plan_id = ? and stay_date = ?",
                Integer.class, ratePlanId(STANDARD), DAY.plusDays(1));
        org.assertj.core.api.Assertions.assertThat(first).isEqualTo(110_000);
        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(150_000);
    }

    @Test
    void sameIdempotencyKeyReturnsSameRateResult() throws Exception {
        String body = rateBody(STANDARD, DAY, 130_000);
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "repeat-rate")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated());

        // 재호출은 200에 created=false로 같은 결과를 돌려준다.
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "repeat-rate")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.days[0].amountKrw").value(130_000));

        // 명령은 한 번만 저장된다.
        Integer commands = jdbc.queryForObject(
                "select count(*) from rate_command where room_type_id = ?",
                Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(commands).isEqualTo(1);
    }

    @Test
    void sameRateWithNewIdempotencyKeyReturnsSameResult() throws Exception {
        String body = rateBody(STANDARD, DAY, 135_000);
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "first-rate")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated());

        // 응답 유실 뒤 클라이언트가 새 멱원 키로 같은 내용을 보낸다.
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "first-rate-retry")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.days[0].amountKrw").value(135_000));
    }

    @Test
    void negativeRateIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "negative-rate")
                .contentType("application/json")
                .content(rateBody(STANDARD, DAY, -1)))
                .andExpect(status().isBadRequest());

        Integer amount = jdbc.queryForObject(
                "select amount_krw from rate_day where rate_plan_id = ? and stay_date = ?",
                Integer.class, ratePlanId(STANDARD), DAY);
        org.assertj.core.api.Assertions.assertThat(amount).isEqualTo(100_000);
    }

    @Test
    void excessiveRateIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "excessive-rate")
                .contentType("application/json")
                .content(rateBody(STANDARD, DAY, 100_000_001)))
                .andExpect(status().isBadRequest());

        Integer amount = jdbc.queryForObject(
                "select amount_krw from rate_day where rate_plan_id = ? and stay_date = ?",
                Integer.class, ratePlanId(STANDARD), DAY);
        org.assertj.core.api.Assertions.assertThat(amount).isEqualTo(100_000);
    }

    @Test
    void dayWithoutRateRowIsRejected() throws Exception {
        // 90일 시드 범위 밖이 아니라, 아예 요금 행이 없는 날짜다.
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "missing-rate-day")
                .contentType("application/json")
                .content(rateBody(STANDARD, DAY.plusDays(30), 90_000)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RATE_DAY_NOT_FOUND"));

        // 가격이 바뀌지 않는다.
        Integer amount = jdbc.queryForObject(
                "select amount_krw from rate_day where rate_plan_id = ? and stay_date = ?",
                Integer.class, ratePlanId(STANDARD), DAY);
        org.assertj.core.api.Assertions.assertThat(amount).isEqualTo(100_000);
    }

    @Test
    void ratePlanWithoutSeedListIsRejected() throws Exception {
        // 요금제가 없는 유형은 가격을 바꿀 대상이 없다.
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates",
                SOKCHO, UNSEEDED)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "unseeded-rate")
                .contentType("application/json")
                .content(rateBody(UNSEEDED, DAY, 90_000)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_TYPE_RATE_PLAN_NOT_FOUND"));
    }

    @Test
    void emptyRateAdjustmentsAreRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "empty-rate-adjustments")
                .contentType("application/json")
                .content(rateBody(STANDARD, java.util.List.of())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rateAdjustRequiresIdempotencyKey() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .contentType("application/json")
                .content(rateBody(STANDARD, DAY, 120_000)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void branchStaffCannotChangeRates() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", sokchoToken)
                .header("Idempotency-Key", "branch-rate")
                .contentType("application/json")
                .content(rateBody(STANDARD, DAY, 120_000)))
                .andExpect(status().isForbidden());

        Integer amount = jdbc.queryForObject(
                "select amount_krw from rate_day where rate_plan_id = ? and stay_date = ?",
                Integer.class, ratePlanId(STANDARD), DAY);
        org.assertj.core.api.Assertions.assertThat(amount).isEqualTo(100_000);
    }

    @Test
    void missingSessionCannotChangeRates() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates",
                SOKCHO, STANDARD)
                .header("Idempotency-Key", "no-session-rate")
                .contentType("application/json")
                .content(rateBody(STANDARD, DAY, 120_000)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownHotelIsNotFoundForRates() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates",
                UUID.randomUUID(), STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "unknown-hotel-rate")
                .contentType("application/json")
                .content(rateBody(STANDARD, DAY, 120_000)))
                .andExpect(status().isNotFound());
    }

    @Test
    void roomTypeFromAnotherHotelIsRejectedForRates() throws Exception {
        // STANDARD는 속초 소속인데 제주 호텔 경로로 호출한다.
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates",
                JEJU, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "other-hotel-rate")
                .contentType("application/json")
                .content(rateBody(STANDARD, DAY, 120_000)))
                .andExpect(status().isNotFound());
    }

    @Test
    void headquartersReadsDailyRates() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomTypeId").value(STANDARD.toString()))
                .andExpect(jsonPath("$.ratePlanName").value("스탠다드 기본"))
                .andExpect(jsonPath("$.days", org.hamcrest.Matchers.hasSize(5)))
                .andExpect(jsonPath("$.days[0].stayDate").value(DAY.toString()))
                .andExpect(jsonPath("$.days[0].amountKrw").value(100_000))
                .andExpect(jsonPath("$.days[1].amountKrw").value(120_000));
    }

    @Test
    void branchStaffCannotReadOtherHotelRates() throws Exception {
        // 지점 직원은 자기 지점의 가격은 읽을 수 있지만, 다른 지점은 403이다.
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates",
                JEJU, STANDARD)
                .header("X-Staff-Session", sokchoToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void branchStaffCanReadOwnHotelRates() throws Exception {
        // 읽기는 카탈로그·재고 조회와 같이 자기 지점까지 허용한다. 쓰기만 본사 전용이다.
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", sokchoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days", org.hamcrest.Matchers.hasSize(5)));
    }

    @Test
    void missingSessionCannotReadDailyRates() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates",
                SOKCHO, STANDARD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rateQueryHonoursDateRange() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates",
                SOKCHO, STANDARD)
                .param("from", DAY.plusDays(1).toString())
                .param("to", DAY.plusDays(2).toString())
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.days[0].stayDate").value(DAY.plusDays(1).toString()));
    }

    @Test
    void rateQueryRejectsExcessiveRange() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates",
                SOKCHO, STANDARD)
                .param("from", DAY.toString())
                .param("to", DAY.plusDays(200).toString())
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isBadRequest());
    }

    private UUID ratePlanId(UUID roomTypeId) {
        return jdbc.queryForObject(
                "select id from rate_plan where room_type_id = ?", UUID.class, roomTypeId);
    }

    private String rateBody(UUID roomTypeId, LocalDate stayDate, int amountKrw) {
        return rateBody(roomTypeId, java.util.List.of(new RateAdjustRequest.DayRate(stayDate, amountKrw)));
    }

    // 테스트 클래스패스에는 jackson-datatype-jsr310이 없고, jackson-databind 2와
    // tools.jackson 3이 함께 있어 new ObjectMapper()가 LocalDate를 직렬화하지 못한다.
    // Spring MVC가 역직렬화할 때 쓰는 형식을 직접 만들어 의존성을 건드리지 않는다.
    private String rateBody(UUID roomTypeId, java.util.List<RateAdjustRequest.DayRate> rates) {
        StringBuilder payload = new StringBuilder();
        payload.append("{\"roomTypeId\":\"").append(roomTypeId).append("\",\"adjustments\":[");
        for (int index = 0; index < rates.size(); index++) {
            RateAdjustRequest.DayRate rate = rates.get(index);
            if (index > 0) payload.append(',');
            payload.append("{\"stayDate\":\"").append(rate.stayDate())
                    .append("\",\"amountKrw\":").append(rate.amountKrw()).append('}');
        }
        return payload.append("]}").toString();
    }

    private void clean() {
        jdbc.update("delete from staff_session");
        jdbc.update("delete from rate_command where hotel_id in (?, ?)", SOKCHO, JEJU);
        jdbc.update("delete from staff_member");
        jdbc.update("delete from rate_day where rate_plan_id in (select id from rate_plan where room_type_id in (?, ?, ?))",
                STANDARD, SUITE, UNSEEDED);
        jdbc.update("delete from rate_plan where room_type_id in (?, ?, ?)", STANDARD, SUITE, UNSEEDED);
        jdbc.update("delete from room_type where id in (?, ?, ?)", STANDARD, SUITE, UNSEEDED);
        jdbc.update("delete from hotel where id in (?, ?)", SOKCHO, JEJU);
    }
}
