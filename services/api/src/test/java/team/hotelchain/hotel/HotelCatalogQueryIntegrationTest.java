package team.hotelchain.hotel;

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
import team.hotelchain.staff.StaffSessionView;

@SpringBootTest
class HotelCatalogQueryIntegrationTest {

    private static final UUID SOKCHO = UUID.fromString("13000000-0000-0000-0000-000000000001");
    private static final UUID JEJU = UUID.fromString("13000000-0000-0000-0000-000000000002");
    private static final UUID STANDARD = UUID.fromString("23000000-0000-0000-0000-000000000001");
    private static final UUID SUITE = UUID.fromString("23000000-0000-0000-0000-000000000002");
    private static final UUID RATE_WITHOUT_BREAKFAST = UUID.fromString("33000000-0000-0000-0000-000000000001");
    private static final UUID RATE_WITH_BREAKFAST = UUID.fromString("33000000-0000-0000-0000-000000000002");

    @Autowired JdbcTemplate jdbc;
    @Autowired StaffAccessService staffAccess;
    @Autowired HotelCatalogQueryService catalog;
    @Autowired WebApplicationContext context;

    private MockMvc mvc;
    private String hqToken;
    private String sokchoToken;

    @BeforeEach
    void seed() {
        clean();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();

        jdbc.update("insert into hotel values (?, ?, ?, ?)", SOKCHO, "카탈로그 속초", "속초", "Asia/Seoul");
        jdbc.update("insert into hotel values (?, ?, ?, ?)", JEJU, "카탈로그 제주", "제주도", "Asia/Seoul");
        jdbc.update("insert into room_type values (?, ?, ?, ?)", STANDARD, SOKCHO, "스탠다드", 2);
        jdbc.update("insert into room_type values (?, ?, ?, ?)", SUITE, SOKCHO, "스위트", 4);

        jdbc.update("insert into rate_plan values (?, ?, ?, true, ?)", RATE_WITH_BREAKFAST, STANDARD, "조식 포함", "FLEX-2026-01");
        jdbc.update("insert into rate_plan values (?, ?, ?, false, ?)", RATE_WITHOUT_BREAKFAST, STANDARD, "객실만", "FLEX-2026-01");
        jdbc.update("insert into rate_day values (?, ?, ?)", RATE_WITH_BREAKFAST, LocalDate.of(2026, 10, 1), 100000);
        jdbc.update("insert into rate_day values (?, ?, ?)", RATE_WITH_BREAKFAST, LocalDate.of(2026, 10, 2), 150000);
        jdbc.update("insert into rate_day values (?, ?, ?)", RATE_WITHOUT_BREAKFAST, LocalDate.of(2026, 10, 1), 90000);

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "catalog-hq@example.com", "본사 관리자", encoder.encode("hq-password"), "HQ_ADMIN", null);
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "catalog-sokcho@example.com", "속초 직원", encoder.encode("branch-password"), "BRANCH_STAFF", SOKCHO);
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "catalog-jeju@example.com", "제주 직원", encoder.encode("branch-password"), "BRANCH_STAFF", JEJU);

        hqToken = staffAccess.login("catalog-hq@example.com", "hq-password").token();
        sokchoToken = staffAccess.login("catalog-sokcho@example.com", "branch-password").token();
    }

    @AfterEach
    void cleanAfter() {
        clean();
    }

    @Test
    void headquartersSeesRoomTypesWithRatePlansAndPriceRanges() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/room-types", SOKCHO)
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hotelId").value(SOKCHO.toString()))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.roomTypes", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.roomTypes[0].ratePlans[0].name").value("객실만"))
                .andExpect(jsonPath("$.roomTypes[0].ratePlans[0].breakfastIncluded").value(false))
                .andExpect(jsonPath("$.roomTypes[0].ratePlans[0].minAmountKrw").value(90000))
                .andExpect(jsonPath("$.roomTypes[0].ratePlans[0].maxAmountKrw").value(90000))
                .andExpect(jsonPath("$.roomTypes[0].ratePlans[1].name").value("조식 포함"))
                .andExpect(jsonPath("$.roomTypes[0].ratePlans[1].breakfastIncluded").value(true))
                .andExpect(jsonPath("$.roomTypes[0].ratePlans[1].pricedDays").value(2))
                .andExpect(jsonPath("$.roomTypes[0].ratePlans[1].minAmountKrw").value(100000))
                .andExpect(jsonPath("$.roomTypes[0].ratePlans[1].maxAmountKrw").value(150000))
                .andExpect(jsonPath("$.roomTypes[0].ratePlans[1].avgAmountKrw").value(125000.0))
                .andExpect(jsonPath("$.roomTypes[1].ratePlans").isEmpty());
    }

    @Test
    void branchStaffCannotReadAnotherHotelsCatalog() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/room-types", JEJU)
                .header("X-Staff-Session", sokchoToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingSessionIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/room-types", SOKCHO))
                .andExpect(status().isUnauthorized());
    }
    @Test
    void unknownHotelIsNotFound() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/room-types", UUID.randomUUID())
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void limitIsClampedToServerRange() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/room-types", SOKCHO)
                .param("limit", "99999")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2));

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/room-types", SOKCHO)
                .param("limit", "1")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomTypes", org.hamcrest.Matchers.hasSize(1)));

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/room-types", SOKCHO)
                .param("limit", "1").param("offset", "1")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomTypes", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void ratePlansWithoutPricesReportEmptyRanges() {
        RoomTypeCatalogResponse response = catalog.listRoomTypes(hqToken, SOKCHO, null, null);

        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.roomTypes()).extracting(RoomTypeCatalogView::name)
                .containsExactlyInAnyOrder("스탠다드", "스위트");

        RoomTypeCatalogView standard = response.roomTypes().stream()
                .filter(roomType -> "스탠다드".equals(roomType.name()))
                .findFirst().orElseThrow();
        assertThat(standard.maxOccupancy()).isEqualTo(2);
        assertThat(standard.ratePlans()).extracting(RoomTypeCatalogView.RatePlanSummary::name)
                .containsExactlyInAnyOrder("객실만", "조식 포함");

        RoomTypeCatalogView suite = response.roomTypes().stream()
                .filter(roomType -> "스위트".equals(roomType.name()))
                .findFirst().orElseThrow();
        assertThat(suite.maxOccupancy()).isEqualTo(4);
        assertThat(suite.ratePlans()).isEmpty();
    }

    @Test
    void branchStaffCanReadTheirOwnHotelsCatalog() {
        RoomTypeCatalogResponse response = catalog.listRoomTypes(sokchoToken, SOKCHO, null, null);

        assertThat(response.hotelId()).isEqualTo(SOKCHO);
        assertThat(response.totalCount()).isEqualTo(2);
    }

    private void clean() {
        jdbc.update("delete from staff_session");
        jdbc.update("delete from staff_member");
        jdbc.update("delete from rate_day where rate_plan_id in (?, ?)", RATE_WITH_BREAKFAST, RATE_WITHOUT_BREAKFAST);
        jdbc.update("delete from rate_plan where room_type_id in (?, ?)", STANDARD, SUITE);
        jdbc.update("delete from room_type where id in (?, ?)", STANDARD, SUITE);
        jdbc.update("delete from hotel where id in (?, ?)", SOKCHO, JEJU);
    }
}
