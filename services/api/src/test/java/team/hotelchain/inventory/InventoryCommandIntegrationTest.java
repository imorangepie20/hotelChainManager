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

@SpringBootTest
class InventoryCommandIntegrationTest {

    private static final UUID SOKCHO = UUID.fromString("15000000-0000-0000-0000-000000000001");
    private static final UUID JEJU = UUID.fromString("15000000-0000-0000-0000-000000000002");
    private static final UUID STANDARD = UUID.fromString("25000000-0000-0000-0000-000000000001");
    private static final UUID SUITE = UUID.fromString("25000000-0000-0000-0000-000000000002");
    private static final String HQ_EMAIL = "inventory-command-hq@example.com";
    private static final String SOKCHO_EMAIL = "inventory-command-sokcho@example.com";

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

        jdbc.update("insert into hotel values (?, ?, ?, ?)", SOKCHO, "재고 쓰기 속초", "속초", "Asia/Seoul");
        jdbc.update("insert into hotel values (?, ?, ?, ?)", JEJU, "재고 쓰기 제주", "제주도", "Asia/Seoul");
        // V53 이후 room_type은 seed_breakfast_included·seed_default_rate_krw 열을 가진다.
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
    void headquartersRaisesCapacity() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/inventory", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "raise-capacity")
                .contentType("application/json")
                .content(adjustBody(STANDARD, DAY, 14)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomTypeId").value(STANDARD.toString()))
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.days[0].stayDate").value(DAY.toString()))
                .andExpect(jsonPath("$.days[0].capacity").value(14))
                .andExpect(jsonPath("$.days[0].confirmed").value(2))
                .andExpect(jsonPath("$.days[0].remaining").value(12));

        Integer capacity = jdbc.queryForObject(
                "select capacity from inventory_day where room_type_id = ? and stay_date = ?",
                Integer.class, STANDARD, DAY);
        org.assertj.core.api.Assertions.assertThat(capacity).isEqualTo(14);
    }

    @Test
    void headquartersLowersCapacityAboveConfirmed() throws Exception {
        // 확정 2건보다 큰 값으로 내리는 것은 허용한다.
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/inventory", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "lower-capacity")
                .contentType("application/json")
                .content(adjustBody(STANDARD, DAY, 3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.days[0].capacity").value(3))
                .andExpect(jsonPath("$.days[0].remaining").value(1));
    }

    @Test
    void loweringCapacityBelowConfirmedIsRejected() throws Exception {
        // 확정 2건을 1실로 내릴 수 없다. 재고는 음수가 될 수 없다.
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/inventory", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "lower-below-confirmed")
                .contentType("application/json")
                .content(adjustBody(STANDARD, DAY, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVENTORY_CAPACITY_CONFLICT"));

        Integer capacity = jdbc.queryForObject(
                "select capacity from inventory_day where room_type_id = ? and stay_date = ?",
                Integer.class, STANDARD, DAY);
        org.assertj.core.api.Assertions.assertThat(capacity).isEqualTo(10);
    }

    @Test
    void heldCountAlsoBlocksLowering() throws Exception {
        // 보류 중인 객실이 있어도 새 총량 아래로는 내릴 수 없다.
        jdbc.update("update inventory_day set held = 4 where room_type_id = ? and stay_date = ?", STANDARD, DAY);

        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/inventory", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "lower-below-held")
                .contentType("application/json")
                .content(adjustBody(STANDARD, DAY, 5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVENTORY_CAPACITY_CONFLICT"));
    }

    @Test
    void zeroCapacityStopsSales() throws Exception {
        // 0실은 판매 중지와 같다. 확정·보류가 없어야 한다.
        jdbc.update("update inventory_day set confirmed = 0 where room_type_id = ? and stay_date = ?", STANDARD, DAY);

        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/inventory", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "stop-sales")
                .contentType("application/json")
                .content(adjustBody(STANDARD, DAY, 0)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.days[0].capacity").value(0))
                .andExpect(jsonPath("$.days[0].remaining").value(0));
    }

    @Test
    void capacityOutsideRangeIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/inventory", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "negative-capacity")
                .contentType("application/json")
                .content(adjustBody(STANDARD, DAY, -1)))
                .andExpect(status().isBadRequest());

        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/inventory", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "huge-capacity")
                .contentType("application/json")
                .content(adjustBody(STANDARD, DAY, 1001)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/inventory", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .contentType("application/json")
                .content(adjustBody(STANDARD, DAY, 12)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sameIdempotencyKeyReturnsSameResult() throws Exception {
        String body = adjustBody(STANDARD, DAY, 14);
        String first = mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/inventory", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "raise-capacity")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String replayedBody = mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/inventory", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "raise-capacity")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andReturn().getResponse().getContentAsString();

        // created 플래그는 첫 호출과 재호출에서 의도적으로 다르다(201 → 200).
        // 멱원은 본문이 같은 것만 보장하므로 재고 응답만 비교한다.
        String replayedDays = replayedBody.replaceAll(".*\"days\":", "").replaceAll(",\"created\":.*", "");
        String firstDays = first.replaceAll(".*\"days\":", "").replaceAll(",\"created\":.*", "");
        org.assertj.core.api.Assertions.assertThat(replayedDays).isEqualTo(firstDays);

        // 재고는 한 번만 바뀐다.
        Integer commands = jdbc.queryForObject(
                "select count(*) from inventory_command where room_type_id = ?", Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(commands).isEqualTo(1);
    }

    @Test
    void sameRequestWithNewIdempotencyKeyReturnsSameResult() throws Exception {
        String body = adjustBody(STANDARD, DAY, 14);
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/inventory", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "raise-capacity")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated());

        // 응답 유실 뒤 클라이언트가 새 멱원 키로 같은 내용을 보낸다.
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/inventory", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "raise-capacity-retry")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.days[0].capacity").value(14));

        Integer capacity = jdbc.queryForObject(
                "select capacity from inventory_day where room_type_id = ? and stay_date = ?",
                Integer.class, STANDARD, DAY);
        org.assertj.core.api.Assertions.assertThat(capacity).isEqualTo(14);
    }

    @Test
    void multipleDaysAdjustInOneRequest() throws Exception {
        String body = adjustBody(STANDARD, java.util.List.of(
                new InventoryAdjustRequest.DayAdjustment(DAY, 14),
                new InventoryAdjustRequest.DayAdjustment(DAY.plusDays(1), 16)));

        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/inventory", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "multi-day")
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.days", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.days[0].capacity").value(14))
                .andExpect(jsonPath("$.days[1].capacity").value(16));

        Integer first = jdbc.queryForObject(
                "select capacity from inventory_day where room_type_id = ? and stay_date = ?",
                Integer.class, STANDARD, DAY);
        Integer second = jdbc.queryForObject(
                "select capacity from inventory_day where room_type_id = ? and stay_date = ?",
                Integer.class, STANDARD, DAY.plusDays(1));
        org.assertj.core.api.Assertions.assertThat(first).isEqualTo(14);
        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(16);
    }

    @Test
    void dayWithoutInventoryRowIsRejected() throws Exception {
        // 시드되지 않은 일자는 재고 행이 없다.
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/inventory", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "missing-day")
                .contentType("application/json")
                .content(adjustBody(STANDARD, DAY.plusDays(10), 12)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVENTORY_DAY_NOT_FOUND"));
    }

    @Test
    void branchStaffCannotAdjustInventory() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/inventory", SOKCHO)
                .header("X-Staff-Session", sokchoToken)
                .header("Idempotency-Key", "branch-adjust")
                .contentType("application/json")
                .content(adjustBody(STANDARD, DAY, 12)))
                .andExpect(status().isForbidden());

        Integer capacity = jdbc.queryForObject(
                "select capacity from inventory_day where room_type_id = ? and stay_date = ?",
                Integer.class, STANDARD, DAY);
        org.assertj.core.api.Assertions.assertThat(capacity).isEqualTo(10);
    }

    @Test
    void missingSessionIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/inventory", SOKCHO)
                .header("Idempotency-Key", "no-session")
                .contentType("application/json")
                .content(adjustBody(STANDARD, DAY, 12)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownHotelIsNotFound() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/inventory", UUID.randomUUID())
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "unknown-hotel")
                .contentType("application/json")
                .content(adjustBody(STANDARD, DAY, 12)))
                .andExpect(status().isNotFound());
    }

    @Test
    void roomTypeFromAnotherHotelIsRejected() throws Exception {
        // STANDARD는 속초 소속인데 제주 호텔 경로로 호출한다.
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/inventory", JEJU)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "other-hotel-room-type")
                .contentType("application/json")
                .content(adjustBody(STANDARD, DAY, 12)))
                .andExpect(status().isNotFound());
    }

    @Test
    void emptyAdjustmentsAreRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/staff/hotels/{hotelId}/inventory", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "empty-adjustments")
                .contentType("application/json")
                .content(adjustBody(STANDARD, java.util.List.of())))
                .andExpect(status().isBadRequest());
    }

    private String adjustBody(UUID roomTypeId, LocalDate stayDate, int capacity) {
        return adjustBody(roomTypeId, java.util.List.of(new InventoryAdjustRequest.DayAdjustment(stayDate, capacity)));
    }

    // 테스트 클래스패스에는 jackson-datatype-jsr310이 없고, jackson-databind 2와
    // tools.jackson 3이 함께 있어 new ObjectMapper()가 LocalDate를 직렬화하지 못한다.
    // Spring MVC가 역직렬화할 때 쓰는 형식을 직접 만들어 의존성을 건드리지 않는다.
    private String adjustBody(UUID roomTypeId, java.util.List<InventoryAdjustRequest.DayAdjustment> adjustments) {
        StringBuilder payload = new StringBuilder();
        payload.append("{\"roomTypeId\":\"").append(roomTypeId).append("\",\"adjustments\":[");
        for (int index = 0; index < adjustments.size(); index++) {
            InventoryAdjustRequest.DayAdjustment adjustment = adjustments.get(index);
            if (index > 0) payload.append(',');
            payload.append("{\"stayDate\":\"").append(adjustment.stayDate())
                    .append("\",\"capacity\":").append(adjustment.capacity()).append('}');
        }
        return payload.append("]}").toString();
    }

    private void clean() {
        jdbc.update("delete from staff_session");
        jdbc.update("delete from inventory_command where hotel_id in (?, ?)", SOKCHO, JEJU);
        jdbc.update("delete from staff_member");
        jdbc.update("delete from inventory_day where room_type_id in (?, ?)", STANDARD, SUITE);
        jdbc.update("delete from room_type where id in (?, ?)", STANDARD, SUITE);
        jdbc.update("delete from hotel where id in (?, ?)", SOKCHO, JEJU);
    }
}
