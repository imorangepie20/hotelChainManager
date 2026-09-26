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

/**
 * 본사가 객실 유형에 두 번째 요금제를 만들고 이름을 바꾸는 통합 검증.
 * <p>
 * 객실 유형 하나에 요금제가 여러 개일 수 있다. 조식 포함 여부·취소 규정·금액이
 * 다른 요금제를 같은 객실에서 동시에 팔아야 하기 때문이다.
 */
@SpringBootTest
class RatePlanCommandIntegrationTest {

    private static final UUID SOKCHO = UUID.fromString("16000000-0000-0000-0000-000000000101");
    private static final UUID JEJU = UUID.fromString("16000000-0000-0000-0000-000000000102");
    private static final UUID STANDARD = UUID.fromString("26000000-0000-0000-0000-000000000101");
    private static final UUID SUITE = UUID.fromString("26000000-0000-0000-0000-000000000102");
    private static final String HQ_EMAIL = "rate-plan-hq@example.com";
    private static final String SOKCHO_EMAIL = "rate-plan-sokcho@example.com";

    private static final LocalDate DAY = LocalDate.of(2026, 11, 1);

    @Autowired JdbcTemplate jdbc;
    @Autowired StaffAccessService staffAccess;
    @Autowired WebApplicationContext context;

    private MockMvc mvc;
    private String hqToken;
    private String sokchoToken;
    private UUID basePlanId;

    @BeforeEach
    void seed() {
        clean();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();

        jdbc.update("insert into hotel values (?, ?, ?, ?)", SOKCHO, "요금제 속초", "속초", "Asia/Seoul");
        jdbc.update("insert into hotel values (?, ?, ?, ?)", JEJU, "요금제 제주", "제주도", "Asia/Seoul");
        jdbc.update("insert into room_type (id, hotel_id, name, max_occupancy) values (?, ?, ?, ?)",
                STANDARD, SOKCHO, "스탠다드", 2);
        jdbc.update("insert into room_type (id, hotel_id, name, max_occupancy) values (?, ?, ?, ?)",
                SUITE, SOKCHO, "스위트", 4);

        basePlanId = UUID.randomUUID();
        jdbc.update("insert into rate_plan (id, room_type_id, name, breakfast_included, policy_version, created_at) values (?, ?, ?, ?, ?, now())",
                basePlanId, STANDARD, "스탠다드 기본", false, "FLEX-2026-01");
        for (int offset = 0; offset < 5; offset++) {
            LocalDate stayDate = DAY.plusDays(offset);
            jdbc.update("insert into rate_day (rate_plan_id, stay_date, amount_krw) values (?, ?, ?)",
                    basePlanId, stayDate, 100_000);
            // 재고는 객실 유형 단위다. 요금제가 공유한다.
            jdbc.update("insert into inventory_day (room_type_id, stay_date, capacity, held, confirmed) values (?, ?, ?, 0, 0)",
                    STANDARD, stayDate, 8);
            // SUITE도 재고를 가진다. 같은 지점의 다른 유형을 쓰는 검증이
            // 재고 부족으로 실패하지 않게 한다.
            jdbc.update("insert into inventory_day (room_type_id, stay_date, capacity, held, confirmed) values (?, ?, ?, 0, 0)",
                    SUITE, stayDate, 4);
        }
        // 재고가 5일분만 있으므로 90일 요금제는 재고 없는 날짜에서 거부돼야 한다.

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
    void headquartersCreatesSecondRatePlan() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "second-plan")
                .contentType("application/json")
                .content(createBody("조식 포함", true, "FLEX-2026-01", 150_000, DAY, 5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomTypeId").value(STANDARD.toString()))
                .andExpect(jsonPath("$.name").value("조식 포함"))
                .andExpect(jsonPath("$.breakfastIncluded").value(true))
                .andExpect(jsonPath("$.policyVersion").value("FLEX-2026-01"))
                .andExpect(jsonPath("$.defaultRateKrw").value(150_000))
                .andExpect(jsonPath("$.seededDays").value(5))
                .andExpect(jsonPath("$.created").value(true));

        Integer plans = jdbc.queryForObject(
                "select count(*) from rate_plan where room_type_id = ?", Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(plans).isEqualTo(2);

        Integer days = jdbc.queryForObject(
                "select count(*) from rate_day where rate_plan_id <> ? and stay_date between ? and ?",
                Integer.class, basePlanId, DAY, DAY.plusDays(4));
        org.assertj.core.api.Assertions.assertThat(days).isEqualTo(5);
    }

    @Test
    void secondRatePlanAppearsAsSeparateAvailabilityOffer() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "offer-plan")
                .contentType("application/json")
                .content(createBody("조식 포함", true, "FLEX-2026-01", 150_000, DAY, 4)))
                .andExpect(status().isCreated());

        // AvailabilityService가 모든 요금제를 읽어서 요금제별로 오퍼를 만든다.
        // 두 번째 요금제를 심기만 하면 고객 검색에 별도 오퍼로 나타난다.
        mvc.perform(MockMvcRequestBuilders.get("/api/availability")
                .param("hotelId", SOKCHO.toString())
                .param("checkIn", DAY.toString())
                .param("checkOut", DAY.plusDays(2).toString())
                .param("adults", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.offers[0].ratePlanName").value("스탠다드 기본"))
                .andExpect(jsonPath("$.offers[0].breakfastIncluded").value(false))
                .andExpect(jsonPath("$.offers[0].total").value(200_000))
                .andExpect(jsonPath("$.offers[1].ratePlanName").value("조식 포함"))
                .andExpect(jsonPath("$.offers[1].breakfastIncluded").value(true))
                .andExpect(jsonPath("$.offers[1].total").value(300_000));
    }

    @Test
    void secondRatePlanSharesInventory() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "shared-inventory-plan")
                .contentType("application/json")
                .content(createBody("조식 포함", true, "FLEX-2026-01", 150_000, DAY, 5)))
                .andExpect(status().isCreated());

        // 재고는 객실 유형 단위이므로 요금제 생성이 재고를 만들지 않는다.
        // 기존 8실이 그대로 유지되고, 두 요금제가 같은 재고를 공유한다.
        Integer capacity = jdbc.queryForObject(
                "select max(capacity) from inventory_day where room_type_id = ?", Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(capacity).isEqualTo(8);
    }

    @Test
    void secondRatePlanDoesNotCreateInventoryDays() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "no-new-inventory-plan")
                .contentType("application/json")
                .content(createBody("조식 포함", true, "FLEX-2026-01", 150_000, DAY, 5)))
                .andExpect(status().isCreated());

        // 요금제 생성이 재고 일자를 추가하지 않는다.
        Integer inventoryRows = jdbc.queryForObject(
                "select count(*) from inventory_day where room_type_id = ?", Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(inventoryRows).isEqualTo(5);
    }

    @Test
    void sameIdempotencyKeyReturnsSameRatePlan() throws Exception {
        String body = createBody("조식 포함", true, "FLEX-2026-01", 150_000, DAY, 5);
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "repeat-plan")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated());

        // 재호출은 200에 created=false로 같은 요금제를 돌려준다.
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "repeat-plan")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.name").value("조식 포함"))
                .andExpect(jsonPath("$.seededDays").value(5));

        Integer plans = jdbc.queryForObject(
                "select count(*) from rate_plan where room_type_id = ?", Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(plans).isEqualTo(2);

        Integer commands = jdbc.queryForObject(
                "select count(*) from rate_plan_command where room_type_id = ?", Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(commands).isEqualTo(1);
    }

    @Test
    void sameRatePlanWithNewIdempotencyKeyReturnsSameResult() throws Exception {
        String body = createBody("조식 포함", true, "FLEX-2026-01", 150_000, DAY, 5);
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "first-plan")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated());

        // 응답 유실 뒤 클라이언트가 새 멱원 키로 같은 내용을 보낸다.
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "first-plan-retry")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.name").value("조식 포함"));

        Integer plans = jdbc.queryForObject(
                "select count(*) from rate_plan where room_type_id = ?", Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(plans).isEqualTo(2);
    }

    @Test
    void duplicateNameInSameRoomTypeIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "dup-1")
                .contentType("application/json")
                .content(createBody("조식 포함", true, "FLEX-2026-01", 150_000, DAY, 5)))
                .andExpect(status().isCreated());

        // 같은 유형 안의 같은 이름은 거부한다.
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "dup-2")
                .contentType("application/json")
                .content(createBody("조식 포함", false, "FLEX-2026-01", 90_000, DAY, 5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RATE_PLAN_NAME_CONFLICT"));

        Integer plans = jdbc.queryForObject(
                "select count(*) from rate_plan where room_type_id = ?", Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(plans).isEqualTo(2);
    }

    @Test
    void sameNameInDifferentRoomTypeIsAllowed() throws Exception {
        // 요금제 이름은 유형 안에서만 구별되면 충분하다.
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "cross-type-1")
                .contentType("application/json")
                .content(createBody("조식 포함", true, "FLEX-2026-01", 150_000, DAY, 5)))
                .andExpect(status().isCreated());

        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans",
                SOKCHO, SUITE)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "cross-type-2")
                .contentType("application/json")
                .content(createBody("조식 포함", true, "FLEX-2026-01", 250_000, DAY, 5)))
                .andExpect(status().isCreated());

        Integer standardPlans = jdbc.queryForObject(
                "select count(*) from rate_plan where room_type_id = ?", Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(standardPlans).isEqualTo(2);

        Integer suitePlans = jdbc.queryForObject(
                "select count(*) from rate_plan where room_type_id = ?", Integer.class, SUITE);
        org.assertj.core.api.Assertions.assertThat(suitePlans).isEqualTo(1);
    }

    @Test
    void dayWithoutInventoryIsRejected() throws Exception {
        // 재고가 5일분만 있다. 90일을 요청하면 재고 없는 날짜에서 거부된다.
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "no-inventory-plan")
                .contentType("application/json")
                .content(createBody("90일 요금제", true, "FLEX-2026-01", 150_000, DAY, 90)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RATE_PLAN_INVENTORY_DAY_NOT_FOUND"));

        // 요금제가 만들어지면 안 된다.
        Integer plans = jdbc.queryForObject(
                "select count(*) from rate_plan where room_type_id = ?", Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(plans).isEqualTo(1);
    }

    @Test
    void negativeRateIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "negative-plan")
                .contentType("application/json")
                .content(createBody("마이너스", true, "FLEX-2026-01", -1, DAY, 5)))
                .andExpect(status().isBadRequest());

        Integer plans = jdbc.queryForObject(
                "select count(*) from rate_plan where room_type_id = ?", Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(plans).isEqualTo(1);
    }

    @Test
    void excessiveRateIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "excessive-plan")
                .contentType("application/json")
                .content(createBody("초과", true, "FLEX-2026-01", 100_000_001, DAY, 5)))
                .andExpect(status().isBadRequest());

        Integer plans = jdbc.queryForObject(
                "select count(*) from rate_plan where room_type_id = ?", Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(plans).isEqualTo(1);
    }

    @Test
    void blankNameIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "blank-name-plan")
                .contentType("application/json")
                .content(createBody("   ", true, "FLEX-2026-01", 150_000, DAY, 5)))
                .andExpect(status().isBadRequest());

        Integer plans = jdbc.queryForObject(
                "select count(*) from rate_plan where room_type_id = ?", Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(plans).isEqualTo(1);
    }

    @Test
    void missingPolicyVersionIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "no-policy-plan")
                .contentType("application/json")
                .content(new org.json.JSONObject()
                        .put("name", "정책 없음")
                        .put("breakfastIncluded", true)
                        .put("defaultRateKrw", 150_000)
                        .put("fromDate", DAY.toString())
                        .put("days", 5)
                        .toString()))
                .andExpect(status().isBadRequest());

        Integer plans = jdbc.queryForObject(
                "select count(*) from rate_plan where room_type_id = ?", Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(plans).isEqualTo(1);
    }

    @Test
    void excessiveDaysIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "excessive-days-plan")
                .contentType("application/json")
                .content(createBody("93일", true, "FLEX-2026-01", 150_000, DAY, 93)))
                .andExpect(status().isBadRequest());

        Integer plans = jdbc.queryForObject(
                "select count(*) from rate_plan where room_type_id = ?", Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(plans).isEqualTo(1);
    }

    @Test
    void createRequiresIdempotencyKey() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .contentType("application/json")
                .content(createBody("키 없음", true, "FLEX-2026-01", 150_000, DAY, 5)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void branchStaffCannotCreateRatePlan() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", sokchoToken)
                .header("Idempotency-Key", "branch-plan")
                .contentType("application/json")
                .content(createBody("지점 요금제", true, "FLEX-2026-01", 150_000, DAY, 5)))
                .andExpect(status().isForbidden());

        Integer plans = jdbc.queryForObject(
                "select count(*) from rate_plan where room_type_id = ?", Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(plans).isEqualTo(1);
    }

    @Test
    void missingSessionCannotCreateRatePlan() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans",
                SOKCHO, STANDARD)
                .header("Idempotency-Key", "no-session-plan")
                .contentType("application/json")
                .content(createBody("세션 없음", true, "FLEX-2026-01", 150_000, DAY, 5)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownHotelIsNotFoundForCreate() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans",
                UUID.randomUUID(), STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "unknown-hotel-plan")
                .contentType("application/json")
                .content(createBody("알 수 없는 지점", true, "FLEX-2026-01", 150_000, DAY, 5)))
                .andExpect(status().isNotFound());
    }

    @Test
    void roomTypeFromAnotherHotelIsRejectedForCreate() throws Exception {
        // STANDARD는 속초 소속인데 제주 호텔 경로로 호출한다.
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans",
                JEJU, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "other-hotel-plan")
                .contentType("application/json")
                .content(createBody("다른 지점", true, "FLEX-2026-01", 150_000, DAY, 5)))
                .andExpect(status().isNotFound());
    }

    @Test
    void headquartersListsRatePlans() throws Exception {
        createRatePlan("조식 포함");

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomTypeId").value(STANDARD.toString()))
                .andExpect(jsonPath("$.ratePlans", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.ratePlans[0].name").value("스탠다드 기본"))
                .andExpect(jsonPath("$.ratePlans[0].pricedDays").value(5))
                .andExpect(jsonPath("$.ratePlans[0].minAmountKrw").value(100_000))
                .andExpect(jsonPath("$.ratePlans[1].name").value("조식 포함"))
                .andExpect(jsonPath("$.ratePlans[1].breakfastIncluded").value(true))
                .andExpect(jsonPath("$.ratePlans[1].minAmountKrw").value(150_000));
    }

    @Test
    void branchStaffCanReadOwnHotelRatePlans() throws Exception {
        // 읽기는 카탈로그·재고 조회와 같이 자기 지점까지 허용한다. 쓰기만 본사 전용이다.
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", sokchoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratePlans", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void branchStaffCannotReadOtherHotelRatePlans() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans",
                JEJU, STANDARD)
                .header("X-Staff-Session", sokchoToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingSessionCannotReadRatePlans() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans",
                SOKCHO, STANDARD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void headquartersRenamesRatePlan() throws Exception {
        UUID created = createRatePlan("이전 이름");

        mvc.perform(MockMvcRequestBuilders.patch(
                "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans/{ratePlanId}",
                SOKCHO, STANDARD, created)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "rename-plan")
                .contentType("application/json")
                .content(renameBody("이후 이름")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratePlanId").value(created.toString()))
                .andExpect(jsonPath("$.name").value("이후 이름"))
                .andExpect(jsonPath("$.changed").value(true));

        String name = jdbc.queryForObject(
                "select name from rate_plan where id = ?", String.class, created);
        org.assertj.core.api.Assertions.assertThat(name).isEqualTo("이후 이름");
    }

    @Test
    void renameIsIdempotent() throws Exception {
        UUID created = createRatePlan("이전 이름");

        mvc.perform(MockMvcRequestBuilders.patch(
                "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans/{ratePlanId}",
                SOKCHO, STANDARD, created)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "rename-repeat")
                .contentType("application/json")
                .content(renameBody("이후 이름")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("이후 이름"));

        // 재호출은 같은 결과를 돌려준다.
        mvc.perform(MockMvcRequestBuilders.patch(
                "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans/{ratePlanId}",
                SOKCHO, STANDARD, created)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "rename-repeat")
                .contentType("application/json")
                .content(renameBody("이후 이름")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("이후 이름"));

        Integer commands = jdbc.queryForObject(
                "select count(*) from rate_plan_command where rate_plan_id = ? and kind = 'RENAME'",
                Integer.class, created);
        org.assertj.core.api.Assertions.assertThat(commands).isEqualTo(1);
    }

    @Test
    void renameToSameNameDoesNotTouchDatabase() throws Exception {
        UUID created = createRatePlan("같은 이름");

        mvc.perform(MockMvcRequestBuilders.patch(
                "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans/{ratePlanId}",
                SOKCHO, STANDARD, created)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "rename-same")
                .contentType("application/json")
                .content(renameBody("같은 이름")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("같은 이름"));

        // RENAME 멱원 기록은 한 번만 남는다. CREATE 기록은 createRatePlan이 남긴 것이다.
        Integer commands = jdbc.queryForObject(
                "select count(*) from rate_plan_command where rate_plan_id = ? and kind = 'RENAME'",
                Integer.class, created);
        org.assertj.core.api.Assertions.assertThat(commands).isEqualTo(1);
    }

    @Test
    void renameToDuplicateNameIsRejected() throws Exception {
        createRatePlan("첫 번째");
        UUID second = createRatePlan("두 번째");

        mvc.perform(MockMvcRequestBuilders.patch(
                "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans/{ratePlanId}",
                SOKCHO, STANDARD, second)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "rename-dup")
                .contentType("application/json")
                .content(renameBody("첫 번째")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RATE_PLAN_NAME_CONFLICT"));

        String name = jdbc.queryForObject(
                "select name from rate_plan where id = ?", String.class, second);
        org.assertj.core.api.Assertions.assertThat(name).isEqualTo("두 번째");
    }

    @Test
    void renameRequiresIdempotencyKey() throws Exception {
        UUID created = createRatePlan("이전 이름");

        mvc.perform(MockMvcRequestBuilders.patch(
                "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans/{ratePlanId}",
                SOKCHO, STANDARD, created)
                .header("X-Staff-Session", hqToken)
                .contentType("application/json")
                .content(renameBody("이후 이름")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void branchStaffCannotRenameRatePlan() throws Exception {
        UUID created = createRatePlan("이전 이름");

        mvc.perform(MockMvcRequestBuilders.patch(
                "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans/{ratePlanId}",
                SOKCHO, STANDARD, created)
                .header("X-Staff-Session", sokchoToken)
                .header("Idempotency-Key", "branch-rename")
                .contentType("application/json")
                .content(renameBody("이후 이름")))
                .andExpect(status().isForbidden());

        String name = jdbc.queryForObject(
                "select name from rate_plan where id = ?", String.class, created);
        org.assertj.core.api.Assertions.assertThat(name).isEqualTo("이전 이름");
    }

    @Test
    void renameUnknownRatePlanIsNotFound() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch(
                "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans/{ratePlanId}",
                SOKCHO, STANDARD, UUID.randomUUID())
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "rename-unknown")
                .contentType("application/json")
                .content(renameBody("이후 이름")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RATE_PLAN_NOT_FOUND"));
    }

    @Test
    void renameRatePlanFromAnotherRoomTypeIsRejected() throws Exception {
        UUID created = createRatePlan("속초 요금제");

        // SUITE는 같은 지점의 다른 유형이다. STANDARD의 요금제를 SUITE 경로로
        // 바꾸려 하면 거부한다.
        mvc.perform(MockMvcRequestBuilders.patch(
                "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans/{ratePlanId}",
                SOKCHO, SUITE, created)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "rename-cross-type")
                .contentType("application/json")
                .content(renameBody("스위트 이름")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RATE_PLAN_NOT_FOUND"));

        String name = jdbc.queryForObject(
                "select name from rate_plan where id = ?", String.class, created);
        org.assertj.core.api.Assertions.assertThat(name).isEqualTo("속초 요금제");
    }

    @Test
    void ratesQuerySelectsExplicitRatePlan() throws Exception {
        UUID created = createRatePlan("조식 포함");

        // 요금제를 명시하지 않으면 기본 요금제를 읽는다.
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates",
                SOKCHO, STANDARD)
                .param("from", DAY.toString())
                .param("to", DAY.plusDays(1).toString())
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratePlanId").value(basePlanId.toString()))
                .andExpect(jsonPath("$.ratePlanName").value("스탠다드 기본"))
                .andExpect(jsonPath("$.days[0].amountKrw").value(100_000));

        // 명시하면 그 요금제를 읽는다.
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates",
                SOKCHO, STANDARD)
                .param("ratePlanId", created.toString())
                .param("from", DAY.toString())
                .param("to", DAY.plusDays(1).toString())
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratePlanId").value(created.toString()))
                .andExpect(jsonPath("$.ratePlanName").value("조식 포함"))
                .andExpect(jsonPath("$.days[0].amountKrw").value(150_000));
    }

    @Test
    void ratesQueryRejectsRatePlanFromOtherRoomType() throws Exception {
        UUID created = createRatePlan("조식 포함");

        // SUITE 경로로 STANDARD의 요금제를 읽으려 한다.
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates",
                SOKCHO, SUITE)
                .param("ratePlanId", created.toString())
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RATE_PLAN_NOT_FOUND"));
    }

    @Test
    void rateAdjustSelectsExplicitRatePlan() throws Exception {
        UUID created = createRatePlan("조식 포함");

        // 명시한 요금제의 금액만 바뀐다.
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates",
                SOKCHO, STANDARD)
                .param("ratePlanId", created.toString())
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "adjust-second-plan")
                .contentType("application/json")
                .content(rateBody(STANDARD, DAY, 160_000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ratePlanId").value(created.toString()))
                .andExpect(jsonPath("$.days[0].amountKrw").value(160_000));

        Integer second = jdbc.queryForObject(
                "select amount_krw from rate_day where rate_plan_id = ? and stay_date = ?",
                Integer.class, created, DAY);
        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(160_000);

        // 기본 요금제는 그대로다.
        Integer base = jdbc.queryForObject(
                "select amount_krw from rate_day where rate_plan_id = ? and stay_date = ?",
                Integer.class, basePlanId, DAY);
        org.assertj.core.api.Assertions.assertThat(base).isEqualTo(100_000);
    }

    @Test
    void rateAdjustWithoutRatePlanUsesDefault() throws Exception {
        // 둘 이상의 요금제가 있어도 요금제를 명시하지 않으면 기본 요금제를 쓴다.
        createRatePlan("조식 포함");

        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates",
                SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "adjust-default-plan")
                .contentType("application/json")
                .content(rateBody(STANDARD, DAY, 120_000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ratePlanId").value(basePlanId.toString()))
                .andExpect(jsonPath("$.days[0].amountKrw").value(120_000));
    }

    /**
     * 두 번째 요금제를 만들고 그 id를 돌려준다. 여러 검증이 같은 출발 상태를
     * 쓸 때 중복을 피하기 위해 쓴다.
     */
    private UUID createRatePlan(String name) throws Exception {
        String body = createBody(name, true, "FLEX-2026-01", 150_000, DAY, 5);
        String response = mvc.perform(MockMvcRequestBuilders.post(
                "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans", SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "seed-" + name + "-" + UUID.randomUUID())
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(new org.json.JSONObject(response).getString("ratePlanId"));
    }

    // 테스트 클래스패스에는 jackson-datatype-jsr310이 없고, jackson-databind 2와
    // tools.jackson 3이 함께 있어 new ObjectMapper()가 LocalDate를 직렬화하지 못한다.
    // Spring MVC가 역직렬화할 때 쓰는 형식을 직접 만들어 의존성을 건드리지 않는다.
    private String createBody(String name, boolean breakfastIncluded, String policyVersion,
            int defaultRateKrw, LocalDate fromDate, int days) {
        return "{\"name\":\"" + escape(name)
                + "\",\"breakfastIncluded\":" + breakfastIncluded
                + ",\"policyVersion\":\"" + escape(policyVersion) + "\""
                + ",\"defaultRateKrw\":" + defaultRateKrw
                + ",\"fromDate\":\"" + fromDate + "\""
                + ",\"days\":" + days + "}";
    }

    private String renameBody(String name) {
        return "{\"name\":\"" + escape(name) + "\"}";
    }

    private String rateBody(UUID roomTypeId, LocalDate stayDate, int amountKrw) {
        return "{\"roomTypeId\":\"" + roomTypeId
                + "\",\"adjustments\":[{\"stayDate\":\"" + stayDate
                + "\",\"amountKrw\":" + amountKrw + "}]}";
    }

    // JSON 문자열에 넣을 수 있게 큰따옴표·백슬래시·줄바꿈을 이스케이프한다.
    private String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }

    private void clean() {
        jdbc.update("delete from staff_session");
        jdbc.update("delete from rate_plan_command where hotel_id in (?, ?)", SOKCHO, JEJU);
        jdbc.update("delete from rate_command where hotel_id in (?, ?)", SOKCHO, JEJU);
        jdbc.update("delete from staff_member");
        jdbc.update("delete from rate_day where rate_plan_id in (select id from rate_plan where room_type_id in (?, ?))",
                STANDARD, SUITE);
        jdbc.update("delete from rate_plan where room_type_id in (?, ?)", STANDARD, SUITE);
        jdbc.update("delete from inventory_day where room_type_id in (?, ?)", STANDARD, SUITE);
        jdbc.update("delete from room_type where id in (?, ?)", STANDARD, SUITE);
        jdbc.update("delete from hotel where id in (?, ?)", SOKCHO, JEJU);
    }
}
