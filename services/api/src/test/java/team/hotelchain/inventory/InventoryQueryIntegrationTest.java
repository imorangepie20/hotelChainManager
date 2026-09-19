package team.hotelchain.inventory;

import static org.assertj.core.api.Assertions.assertThat;
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
class InventoryQueryIntegrationTest {

    private static final UUID SOKCHO = UUID.fromString("14000000-0000-0000-0000-000000000001");
    private static final UUID JEJU = UUID.fromString("14000000-0000-0000-0000-000000000002");
    private static final UUID STANDARD = UUID.fromString("24000000-0000-0000-0000-000000000001");
    private static final UUID SUITE = UUID.fromString("24000000-0000-0000-0000-000000000002");

    @Autowired JdbcTemplate jdbc;
    @Autowired StaffAccessService staffAccess;
    @Autowired InventoryQueryService inventory;
    @Autowired WebApplicationContext context;

    private MockMvc mvc;
    private String hqToken;
    private String sokchoToken;

    @BeforeEach
    void seed() {
        clean();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();

        jdbc.update("insert into hotel values (?, ?, ?, ?)", SOKCHO, "재고 속초", "속초", "Asia/Seoul");
        jdbc.update("insert into hotel values (?, ?, ?, ?)", JEJU, "재고 제주", "제주도", "Asia/Seoul");
        jdbc.update("insert into room_type values (?, ?, ?, ?)", STANDARD, SOKCHO, "스탠다드", 2);
        jdbc.update("insert into room_type values (?, ?, ?, ?)", SUITE, SOKCHO, "스위트", 4);

        LocalDate day = LocalDate.of(2026, 11, 1);
        // remaining 1: 10 - 2 held - 7 confirmed
        jdbc.update("insert into inventory_day values (?, ?, 10, 2, 7)", STANDARD, day);
        // remaining 0: 매진
        jdbc.update("insert into inventory_day values (?, ?, 5, 0, 5)", STANDARD, day.plusDays(1));
        jdbc.update("insert into inventory_day values (?, ?, 8, 1, 2)", SUITE, day);

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "inventory-hq@example.com", "본사 관리자", encoder.encode("hq-password"), "HQ_ADMIN", null);
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "inventory-sokcho@example.com", "속초 직원", encoder.encode("branch-password"), "BRANCH_STAFF", SOKCHO);
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "inventory-jeju@example.com", "제주 직원", encoder.encode("branch-password"), "BRANCH_STAFF", JEJU);

        hqToken = staffAccess.login("inventory-hq@example.com", "hq-password").token();
        sokchoToken = staffAccess.login("inventory-sokcho@example.com", "branch-password").token();
    }

    @AfterEach
    void cleanAfter() {
        clean();
    }

    @Test
    void headquartersSeesDailyInventoryWithRemaining() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/inventory", SOKCHO)
                .param("from", "2026-11-01").param("to", "2026-11-02")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hotelId").value(SOKCHO.toString()))
                .andExpect(jsonPath("$.roomTypes", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.roomTypes[0].name").value("스탠다드"))
                .andExpect(jsonPath("$.roomTypes[0].days", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.roomTypes[0].days[0].stayDate").value("2026-11-01"))
                .andExpect(jsonPath("$.roomTypes[0].days[0].capacity").value(10))
                .andExpect(jsonPath("$.roomTypes[0].days[0].held").value(2))
                .andExpect(jsonPath("$.roomTypes[0].days[0].confirmed").value(7))
                .andExpect(jsonPath("$.roomTypes[0].days[0].remaining").value(1))
                .andExpect(jsonPath("$.roomTypes[0].days[1].remaining").value(0))
                .andExpect(jsonPath("$.roomTypes[1].name").value("스위트"))
                .andExpect(jsonPath("$.roomTypes[1].days[0].remaining").value(5));
    }

    @Test
    void branchStaffCannotReadAnotherHotelsInventory() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/inventory", JEJU)
                .header("X-Staff-Session", sokchoToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void branchStaffCanReadOwnHotelInventory() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/inventory", SOKCHO)
                .param("from", "2026-11-01").param("to", "2026-11-01")
                .header("X-Staff-Session", sokchoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomTypes", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void missingSessionIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/inventory", SOKCHO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownHotelIsNotFound() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/inventory", UUID.randomUUID())
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void reversedRangeIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/inventory", SOKCHO)
                .param("from", "2026-11-05").param("to", "2026-11-01")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rangeLongerThanNinetyTwoDaysIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/inventory", SOKCHO)
                .param("from", "2026-11-01").param("to", "2027-02-15")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void daysWithoutInventoryRowsAreOmitted() {
        InventoryView view = inventory.list(hqToken, SOKCHO, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5));

        assertThat(view.roomTypes()).isEmpty();
    }

    @Test
    void defaultRangeStartsTodayWhenOmitted() {
        InventoryView view = inventory.list(hqToken, SOKCHO, null, null);

        // 시드 데이터는 2026-11 고정이므로 기본 오늘 기준에는 해당 일자가 없다.
        assertThat(view.roomTypes()).isEmpty();
    }

    private void clean() {
        jdbc.update("delete from staff_session");
        jdbc.update("delete from staff_member");
        jdbc.update("delete from inventory_day where room_type_id in (?, ?)", STANDARD, SUITE);
        jdbc.update("delete from room_type where id in (?, ?)", STANDARD, SUITE);
        jdbc.update("delete from hotel where id in (?, ?)", SOKCHO, JEJU);
    }
}
