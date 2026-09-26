package team.hotelchain.inventory;

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
 * 본사가 객실 유형의 날짜 구간 판매를 중지·재개하는 동작을 검증한다.
 * <p>
 * 총량과 별개로 동작하는 것이 핵심이다. 중지는 {@code capacity}를
 * 건드리지 않으므로 재개하면 중지 전과 같은 재고가 돌아온다.
 */
@SpringBootTest
class SalesStatusIntegrationTest {

    private static final UUID SOKCHO = UUID.fromString("16000000-0000-0000-0000-000000000001");
    private static final UUID JEJU = UUID.fromString("16000000-0000-0000-0000-000000000002");
    private static final UUID STANDARD = UUID.fromString("26000000-0000-0000-0000-000000000001");
    private static final UUID SUITE = UUID.fromString("26000000-0000-0000-0000-000000000002");
    private static final String HQ_EMAIL = "sales-status-hq@example.com";
    private static final String SOKCHO_EMAIL = "sales-status-sokcho@example.com";

    private static final LocalDate DAY = LocalDate.of(2026, 12, 1);

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

        jdbc.update("insert into hotel values (?, ?, ?, ?)", SOKCHO, "판매 중지 속초", "속초", "Asia/Seoul");
        jdbc.update("insert into hotel values (?, ?, ?, ?)", JEJU, "판매 중지 제주", "제주도", "Asia/Seoul");
        jdbc.update("insert into room_type (id, hotel_id, name, max_occupancy) values (?, ?, ?, ?)", STANDARD, SOKCHO, "스탠다드", 2);
        jdbc.update("insert into room_type (id, hotel_id, name, max_occupancy) values (?, ?, ?, ?)", SUITE, SOKCHO, "스위트", 4);

        // remaining 8: 10 - 0 held - 2 confirmed
        jdbc.update("insert into inventory_day (room_type_id, stay_date, capacity, held, confirmed) values (?, ?, 10, 0, 2)", STANDARD, DAY);
        jdbc.update("insert into inventory_day (room_type_id, stay_date, capacity, held, confirmed) values (?, ?, 10, 0, 2)", STANDARD, DAY.plusDays(1));

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
    void headquartersStopsSalesWithoutTouchingCapacity() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch(
                        "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/sales-status", SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "stop-sales")
                .contentType("application/json")
                .content(statusBody(DAY, DAY, "STOPPED")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("STOPPED"))
                .andExpect(jsonPath("$.stoppedDays").value(1))
                .andExpect(jsonPath("$.created").value(true));

        // 총량은 그대로다. 이것이 재고 0으로 내리는 것과의 차이다.
        Integer capacity = jdbc.queryForObject(
                "select capacity from inventory_day where room_type_id = ? and stay_date = ?",
                Integer.class, STANDARD, DAY);
        org.assertj.core.api.Assertions.assertThat(capacity).isEqualTo(10);
    }

    @Test
    void stoppedDaysAreHiddenFromCustomerAvailability() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch(
                        "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/sales-status", SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "stop-sales-availability")
                .contentType("application/json")
                .content(statusBody(DAY, DAY, "STOPPED")))
                .andExpect(status().isCreated());

        Integer stopped = jdbc.queryForObject(
                "select count(*) from room_type_sales_status where room_type_id = ? and status = 'STOPPED'",
                Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(stopped).isEqualTo(1);

        // 고객 가용성 조회에 쓰는 쿼리가 NOT EXISTS 조건으로 중지 일자를 뺀다.
        Integer visible = jdbc.queryForObject("""
                select count(*)
                  from room_type rt
                  join rate_plan rp on rp.room_type_id = rt.id
                  join rate_day rd on rd.rate_plan_id = rp.id
                  join inventory_day i on i.room_type_id = rt.id and i.stay_date = rd.stay_date
                 where rt.id = ?
                   and not exists (
                       select 1 from room_type_sales_status s
                        where s.room_type_id = rt.id and s.stay_date = rd.stay_date
                          and s.status = 'STOPPED')
                """, Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(visible).isEqualTo(0);
    }

    @Test
    void stoppingKeepsConfirmedReservations() throws Exception {
        // 확정 예약 2건이 있는 날짜도 중지할 수 있다.
        // 총량 0으로 내리면 409로 거부되는 경우지만, 중지는 신규 판매만 막는다.
        mvc.perform(MockMvcRequestBuilders.patch(
                        "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/sales-status", SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "stop-with-reservations")
                .contentType("application/json")
                .content(statusBody(DAY, DAY, "STOPPED")))
                .andExpect(status().isCreated());

        Integer confirmed = jdbc.queryForObject(
                "select confirmed from inventory_day where room_type_id = ? and stay_date = ?",
                Integer.class, STANDARD, DAY);
        org.assertj.core.api.Assertions.assertThat(confirmed).isEqualTo(2);
    }

    @Test
    void sameIdempotencyKeyReturnsSameResult() throws Exception {
        String body = statusBody(DAY, DAY.plusDays(1), "STOPPED");
        String first = mvc.perform(MockMvcRequestBuilders.patch(
                        "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/sales-status", SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "stop-sales")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String replayed = mvc.perform(MockMvcRequestBuilders.patch(
                        "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/sales-status", SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "stop-sales")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andReturn().getResponse().getContentAsString();

        // created 플래그는 첫 호출과 재호출에서 의도적으로 다르다(201 → 200).
        // 멱원은 본문이 같은 것만 보장한다.
        org.assertj.core.api.Assertions.assertThat(replayed.replaceAll(",\"created\":.*", ""))
                .isEqualTo(first.replaceAll(",\"created\":.*", ""));

        // 상태는 한 번만 저장된다.
        Integer commands = jdbc.queryForObject(
                "select count(*) from room_type_sales_status_command where room_type_id = ?",
                Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(commands).isEqualTo(1);
    }

    @Test
    void sameRequestWithNewIdempotencyKeyReturnsSameResult() throws Exception {
        String body = statusBody(DAY, DAY, "STOPPED");
        mvc.perform(MockMvcRequestBuilders.patch(
                        "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/sales-status", SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "stop-sales")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated());

        // 응답 유실 뒤 클라이언트가 새 멱원 키로 같은 내용을 보낸다.
        mvc.perform(MockMvcRequestBuilders.patch(
                        "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/sales-status", SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "stop-sales-retry")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.status").value("STOPPED"))
                .andExpect(jsonPath("$.stoppedDays").value(1));

        Integer commands = jdbc.queryForObject(
                "select count(*) from room_type_sales_status_command where room_type_id = ?",
                Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(commands).isEqualTo(1);
    }

    @Test
    void headquartersResumesSales() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch(
                        "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/sales-status", SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "stop-first")
                .contentType("application/json")
                .content(statusBody(DAY, DAY, "STOPPED")))
                .andExpect(status().isCreated());

        mvc.perform(MockMvcRequestBuilders.patch(
                        "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/sales-status", SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "resume-after")
                .contentType("application/json")
                .content(statusBody(DAY, DAY, "OPEN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.stoppedDays").value(0));

        // 재개하면 중지 전과 같은 재고가 돌아온다.
        Integer capacity = jdbc.queryForObject(
                "select capacity from inventory_day where room_type_id = ? and stay_date = ?",
                Integer.class, STANDARD, DAY);
        org.assertj.core.api.Assertions.assertThat(capacity).isEqualTo(10);

        Integer stopped = jdbc.queryForObject(
                "select count(*) from room_type_sales_status where room_type_id = ? and status = 'STOPPED'",
                Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(stopped).isEqualTo(0);
    }

    @Test
    void headquartersListsStoppedRanges() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch(
                        "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/sales-status", SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "stop-for-list")
                .contentType("application/json")
                .content(statusBody(DAY, DAY.plusDays(2), "STOPPED")))
                .andExpect(status().isCreated());

        mvc.perform(MockMvcRequestBuilders.get(
                        "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/sales-status", SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stoppedRanges[0].fromDate").value(DAY.toString()))
                .andExpect(jsonPath("$.stoppedRanges[0].toDate").value(DAY.plusDays(2).toString()));
    }

    @Test
    void inventoryViewExposesSalesStatus() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch(
                        "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/sales-status", SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "stop-for-view")
                .contentType("application/json")
                .content(statusBody(DAY, DAY, "STOPPED")))
                .andExpect(status().isCreated());

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/inventory", SOKCHO)
                .param("from", DAY.toString())
                .param("to", DAY.plusDays(1).toString())
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomTypes[0].days[0].salesStatus").value("STOPPED"))
                .andExpect(jsonPath("$.roomTypes[0].days[1].salesStatus").value("OPEN"))
                // 총량은 그대로다.
                .andExpect(jsonPath("$.roomTypes[0].days[0].capacity").value(10));
    }

    @Test
    void branchStaffCanReadOwnHotelStatus() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch(
                        "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/sales-status", SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "stop-then-read")
                .contentType("application/json")
                .content(statusBody(DAY, DAY, "STOPPED")))
                .andExpect(status().isCreated());

        mvc.perform(MockMvcRequestBuilders.get(
                        "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/sales-status", SOKCHO, STANDARD)
                .header("X-Staff-Session", sokchoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stoppedRanges[0].fromDate").value(DAY.toString()));
    }

    @Test
    void branchStaffCannotStopSales() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch(
                        "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/sales-status", SOKCHO, STANDARD)
                .header("X-Staff-Session", sokchoToken)
                .header("Idempotency-Key", "branch-stop")
                .contentType("application/json")
                .content(statusBody(DAY, DAY, "STOPPED")))
                .andExpect(status().isForbidden());

        Integer stopped = jdbc.queryForObject(
                "select count(*) from room_type_sales_status where room_type_id = ?",
                Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(stopped).isEqualTo(0);
    }

    @Test
    void missingSessionIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch(
                        "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/sales-status", SOKCHO, STANDARD)
                .header("Idempotency-Key", "no-session")
                .contentType("application/json")
                .content(statusBody(DAY, DAY, "STOPPED")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch(
                        "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/sales-status", SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .contentType("application/json")
                .content(statusBody(DAY, DAY, "STOPPED")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownHotelIsNotFound() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch(
                        "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/sales-status", UUID.randomUUID(), STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "unknown-hotel")
                .contentType("application/json")
                .content(statusBody(DAY, DAY, "STOPPED")))
                .andExpect(status().isNotFound());
    }

    @Test
    void roomTypeFromAnotherHotelIsNotFound() throws Exception {
        // STANDARD는 속초 소속인데 제주 호텔 경로로 호출한다.
        mvc.perform(MockMvcRequestBuilders.patch(
                        "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/sales-status", JEJU, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "other-hotel-room-type")
                .contentType("application/json")
                .content(statusBody(DAY, DAY, "STOPPED")))
                .andExpect(status().isNotFound());
    }

    @Test
    void reversedRangeIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch(
                        "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/sales-status", SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "reversed-range")
                .contentType("application/json")
                .content(statusBody(DAY.plusDays(1), DAY, "STOPPED")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void excessiveRangeIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch(
                        "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/sales-status", SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "excessive-range")
                .contentType("application/json")
                .content(statusBody(DAY, DAY.plusDays(100), "STOPPED")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownStatusIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch(
                        "/api/staff/hotels/{hotelId}/room-types/{roomTypeId}/sales-status", SOKCHO, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "unknown-status")
                .contentType("application/json")
                .content(statusBody(DAY, DAY, "PAUSED")))
                .andExpect(status().isBadRequest());
    }

    // 테스트 클래스패스에는 jackson-datatype-jsr310이 없고, jackson-databind 2와
    // tools.jackson 3이 함께 있어 new ObjectMapper()가 LocalDate를 직렬화하지 못한다.
    // Spring MVC가 역직렬화할 때 쓰는 형식을 직접 만들어 의존성을 건드리지 않는다.
    private String statusBody(LocalDate fromDate, LocalDate toDate, String status) {
        return "{\"fromDate\":\"" + fromDate + "\",\"toDate\":\"" + toDate
                + "\",\"status\":\"" + status + "\"}";
    }

    private void clean() {
        jdbc.update("delete from staff_session");
        jdbc.update("delete from room_type_sales_status_command where hotel_id in (?, ?)", SOKCHO, JEJU);
        jdbc.update("delete from room_type_sales_status where room_type_id in (?, ?)", STANDARD, SUITE);
        jdbc.update("delete from staff_member");
        jdbc.update("delete from inventory_day where room_type_id in (?, ?)", STANDARD, SUITE);
        jdbc.update("delete from room_type where id in (?, ?)", STANDARD, SUITE);
        jdbc.update("delete from hotel where id in (?, ?)", SOKCHO, JEJU);
    }
}
