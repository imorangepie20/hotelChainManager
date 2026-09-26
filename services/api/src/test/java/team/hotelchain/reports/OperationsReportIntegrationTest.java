package team.hotelchain.reports;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.hamcrest.Matchers;
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
class OperationsReportIntegrationTest {

    private static final UUID SOKCHO = UUID.fromString("16000000-0000-0000-0000-000000000001");
    private static final UUID JEJU = UUID.fromString("16000000-0000-0000-0000-000000000002");
    private static final UUID SOKCHO_ROOM_TYPE = UUID.fromString("26000000-0000-0000-0000-000000000001");
    private static final UUID JEJU_ROOM_TYPE = UUID.fromString("26000000-0000-0000-0000-000000000002");
    private static final UUID SOKCHO_RATE_PLAN = UUID.fromString("36000000-0000-0000-0000-000000000001");
    private static final UUID JEJU_RATE_PLAN = UUID.fromString("36000000-0000-0000-0000-000000000002");
    private static final String HQ_EMAIL = "report-hq@example.com";
    private static final String SOKCHO_EMAIL = "report-sokcho@example.com";

    @Autowired JdbcTemplate jdbc;
    @Autowired StaffAccessService staffAccess;
    @Autowired WebApplicationContext context;

    private MockMvc mvc;
    private String hqToken;
    private String sokchoToken;
    private UUID hqStaffId;

    @BeforeEach
    void seed() {
        clean();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();

        jdbc.update("insert into hotel values (?, ?, ?, ?)", SOKCHO, "report-sokcho", "sokcho", "Asia/Seoul");
        jdbc.update("insert into hotel values (?, ?, ?, ?)", JEJU, "report-jeju", "jeju", "Asia/Seoul");
        jdbc.update("insert into room_type values (?, ?, ?, ?)", SOKCHO_ROOM_TYPE, SOKCHO, "standard", 2);
        jdbc.update("insert into room_type values (?, ?, ?, ?)", JEJU_ROOM_TYPE, JEJU, "standard", 2);
        jdbc.update("insert into rate_plan values (?, ?, ?, true, 'FLEX-2026-01')", SOKCHO_RATE_PLAN, SOKCHO_ROOM_TYPE, "room-only");
        jdbc.update("insert into rate_plan values (?, ?, ?, true, 'FLEX-2026-01')", JEJU_RATE_PLAN, JEJU_ROOM_TYPE, "room-only");

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        hqStaffId = UUID.randomUUID();
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, ?, ?)",
                hqStaffId, HQ_EMAIL, "headquarters-admin", encoder.encode("hq-password"), "HQ_ADMIN", null);
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), SOKCHO_EMAIL, "sokcho-staff", encoder.encode("branch-password"), "BRANCH_STAFF", SOKCHO);

        hqToken = staffAccess.login(HQ_EMAIL, "hq-password").token();
        sokchoToken = staffAccess.login(SOKCHO_EMAIL, "branch-password").token();

        // 4 confirmed + 1 cancelled + 1 no-show in Sokcho.
        UUID sokchoChangeSource = null;
        for (int index = 0; index < 4; index++) {
            UUID reservationId = insertReservation(SOKCHO_ROOM_TYPE, SOKCHO_RATE_PLAN, "CONFIRMED", 100_000L);
            if (index == 0) sokchoChangeSource = reservationId;
        }
        insertReservation(SOKCHO_ROOM_TYPE, SOKCHO_RATE_PLAN, "CANCELLED", 100_000L);
        insertReservation(SOKCHO_ROOM_TYPE, SOKCHO_RATE_PLAN, "NO_SHOW", 100_000L);
        // 2 confirmed in Jeju.
        for (int index = 0; index < 2; index++) {
            insertReservation(JEJU_ROOM_TYPE, JEJU_RATE_PLAN, "CONFIRMED", 200_000L);
        }

        // 10 room-nights of capacity with 2 confirmed in Sokcho.
        jdbc.update("insert into inventory_day values (?, ?, 10, 0, 2)", SOKCHO_ROOM_TYPE, LocalDate.now());
        jdbc.update("insert into inventory_day values (?, ?, 10, 0, 0)", JEJU_ROOM_TYPE, LocalDate.now());

        // 1 pending and 1 completed change request in Sokcho. reservation_change_request는
        // reservation(id)와 staff_member(id)를 참조하므로 시드한 행에 묶는다.
        insertChangeRequest(sokchoChangeSource, SOKCHO, "PENDING_APPROVAL", "report-pending-key");
        insertChangeRequest(sokchoChangeSource, SOKCHO, "COMPLETED", "report-completed-key");
    }

    @AfterEach
    void cleanAfter() {
        clean();
    }

    @Test
    void headquartersReadsPerHotelMetrics() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations")
                .header("X-Staff-Session", hqToken)
                .param("from", LocalDate.now().toString())
                .param("to", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(1))
                .andExpect(jsonPath("$.hotels", Matchers.hasSize(2)))
                .andExpect(jsonPath("$.hotels[?(@.hotelName=='report-sokcho')].reservations").value(Matchers.contains(6)))
                .andExpect(jsonPath("$.hotels[?(@.hotelName=='report-sokcho')].cancelled").value(Matchers.contains(1)))
                .andExpect(jsonPath("$.hotels[?(@.hotelName=='report-sokcho')].noShow").value(Matchers.contains(1)))
                .andExpect(jsonPath("$.hotels[?(@.hotelName=='report-sokcho')].revenueKrw").value(Matchers.contains(400_000)))
                .andExpect(jsonPath("$.hotels[?(@.hotelName=='report-sokcho')].changeRequestsPending").value(Matchers.contains(1)))
                .andExpect(jsonPath("$.hotels[?(@.hotelName=='report-sokcho')].changeRequestsCompleted").value(Matchers.contains(1)))
                .andExpect(jsonPath("$.hotels[?(@.hotelName=='report-sokcho')].occupancyRate").value(Matchers.contains(0.2d)))
                .andExpect(jsonPath("$.hotels[?(@.hotelName=='report-jeju')].reservations").value(Matchers.contains(2)))
                .andExpect(jsonPath("$.hotels[?(@.hotelName=='report-jeju')].revenueKrw").value(Matchers.contains(400_000)));
    }

    @Test
    void totalsAggregateAllHotels() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations")
                .header("X-Staff-Session", hqToken)
                .param("from", LocalDate.now().toString())
                .param("to", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.reservations").value(8))
                .andExpect(jsonPath("$.totals.cancelled").value(1))
                .andExpect(jsonPath("$.totals.noShow").value(1))
                .andExpect(jsonPath("$.totals.revenueKrw").value(800_000))
                .andExpect(jsonPath("$.totals.changeRequestsPending").value(1))
                .andExpect(jsonPath("$.totals.changeRequestsCompleted").value(1));
    }

    @Test
    void comparesTheImmediatelyPrecedingPeriodUsingTheHotelLocalDate() throws Exception {
        LocalDate todayInSeoul = LocalDate.now(ZoneId.of("Asia/Seoul"));
        // 서울 자정 직전(UTC로는 같은 날짜의 전날 오후)에 생성된 예약은 현재 기간이
        // 아니라 바로 앞 1일 비교 기간에 포함돼야 한다.
        Timestamp previousLocalDay = Timestamp.from(todayInSeoul
                .atStartOfDay(ZoneId.of("Asia/Seoul"))
                .minusMinutes(30)
                .toInstant());
        insertReservationAt(SOKCHO_ROOM_TYPE, SOKCHO_RATE_PLAN, "CONFIRMED", 50_000L, previousLocalDay);
        insertReservationAt(SOKCHO_ROOM_TYPE, SOKCHO_RATE_PLAN, "CANCELLED", 70_000L, previousLocalDay);

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations")
                .header("X-Staff-Session", hqToken)
                .param("from", todayInSeoul.toString())
                .param("to", todayInSeoul.toString()))
                .andExpect(status().isOk())
                // 기존 현재 기간 계약과 숫자는 그대로 유지한다.
                .andExpect(jsonPath("$.from").value(todayInSeoul.toString()))
                .andExpect(jsonPath("$.to").value(todayInSeoul.toString()))
                .andExpect(jsonPath("$.days").value(1))
                .andExpect(jsonPath("$.totals.reservations").value(8))
                .andExpect(jsonPath("$.totals.revenueKrw").value(800_000))
                // 비교 기간은 같은 길이로 현재 기간 바로 앞에 붙는다.
                .andExpect(jsonPath("$.previousPeriod.from").value(todayInSeoul.minusDays(1).toString()))
                .andExpect(jsonPath("$.previousPeriod.to").value(todayInSeoul.minusDays(1).toString()))
                .andExpect(jsonPath("$.previousPeriod.days").value(1))
                .andExpect(jsonPath("$.previousPeriod.totals.reservations").value(2))
                .andExpect(jsonPath("$.previousPeriod.totals.cancelled").value(1))
                .andExpect(jsonPath("$.previousPeriod.totals.noShow").value(0))
                // 취소 예약 금액은 현재 기간과 같은 규칙으로 이전 매출에서도 제외한다.
                .andExpect(jsonPath("$.previousPeriod.totals.revenueKrw").value(50_000));
    }

    @Test
    void comparisonPeriodHasTheSameInclusiveLengthForASevenDayRange() throws Exception {
        LocalDate to = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDate from = to.minusDays(6);

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations")
                .header("X-Staff-Session", hqToken)
                .param("from", from.toString())
                .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousPeriod.from").value(from.minusDays(7).toString()))
                .andExpect(jsonPath("$.previousPeriod.to").value(from.minusDays(1).toString()))
                .andExpect(jsonPath("$.previousPeriod.days").value(7));
    }

    @Test
    void comparesChangeRequestsUsingTheSameSeoulDateBoundary() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        UUID reservationId = insertReservation(
                SOKCHO_ROOM_TYPE, SOKCHO_RATE_PLAN, "CONFIRMED", 100_000L);
        String idempotencyKey = "report-previous-change-key";
        insertChangeRequest(reservationId, SOKCHO, "PENDING_APPROVAL", idempotencyKey);
        Timestamp previousLocalDay = Timestamp.from(today
                .atStartOfDay(ZoneId.of("Asia/Seoul"))
                .minusMinutes(30)
                .toInstant());
        jdbc.update("update reservation_change_request set created_at = ? where idempotency_key = ?",
                previousLocalDay, idempotencyKey);

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations")
                .header("X-Staff-Session", hqToken)
                .param("from", today.toString())
                .param("to", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.changeRequestsPending").value(1))
                .andExpect(jsonPath("$.previousPeriod.totals.changeRequestsPending").value(1))
                .andExpect(jsonPath("$.previousPeriod.totals.changeRequestsCompleted").value(0));
    }

    @Test
    void exportsARealKoreanXlsxWithCurrentPreviousAndCsvCompatibleColumns() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        Timestamp previousLocalDay = Timestamp.from(today
                .atStartOfDay(ZoneId.of("Asia/Seoul"))
                .minusHours(1)
                .toInstant());
        insertReservationAt(SOKCHO_ROOM_TYPE, SOKCHO_RATE_PLAN, "CONFIRMED", 50_000L, previousLocalDay);

        byte[] workbook = mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations/export.xlsx")
                .header("X-Staff-Session", hqToken)
                .param("from", today.toString())
                .param("to", today.toString())
                .param("locale", "ko"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")))
                .andExpect(header().string("Content-Disposition",
                        Matchers.containsString("operations-report-" + today + "-" + today + ".xlsx")))
                .andReturn().getResponse().getContentAsByteArray();

        org.assertj.core.api.Assertions.assertThat(workbook)
                .startsWith(new byte[] { 'P', 'K' });
        String xml = unzipText(workbook);
        // 기존 CSV의 열 이름과 값은 XLSX에서도 그대로 찾을 수 있어야 한다.
        org.assertj.core.api.Assertions.assertThat(xml)
                .contains("지점", "지역", "예약", "취소", "노쇼", "만료", "매출", "점유율", "변경 대기", "변경 완료")
                .contains("현재 기간", "이전 기간", "report-sokcho")
                .contains("800000", "50000");
    }

    @Test
    void exportsAnEnglishXlsxAndRejectsUnsupportedLocales() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        byte[] workbook = mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations/export.xlsx")
                .header("X-Staff-Session", hqToken)
                .param("from", today.toString())
                .param("to", today.toString())
                .param("locale", "en"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        org.assertj.core.api.Assertions.assertThat(unzipText(workbook))
                .contains("Hotel", "Region", "Reservations", "Revenue", "Current period", "Previous period");

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations/export.xlsx")
                .header("X-Staff-Session", hqToken)
                .param("from", today.toString())
                .param("to", today.toString())
                .param("locale", "ja"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void defaultsToLastSevenDays() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(7));
    }

    @Test
    void filtersByHotel() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        Timestamp previousLocalDay = Timestamp.from(today.minusDays(1)
                .atStartOfDay(ZoneId.of("Asia/Seoul"))
                .plusHours(12)
                .toInstant());
        insertReservationAt(SOKCHO_ROOM_TYPE, SOKCHO_RATE_PLAN, "CONFIRMED", 50_000L, previousLocalDay);
        insertReservationAt(JEJU_ROOM_TYPE, JEJU_RATE_PLAN, "CONFIRMED", 70_000L, previousLocalDay);

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations")
                .header("X-Staff-Session", hqToken)
                .param("from", today.toString())
                .param("to", today.toString())
                .param("hotelId", JEJU.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hotels", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.hotels[0].hotelName").value("report-jeju"))
                .andExpect(jsonPath("$.totals.reservations").value(2))
                .andExpect(jsonPath("$.previousPeriod.totals.reservations").value(1))
                .andExpect(jsonPath("$.previousPeriod.totals.revenueKrw").value(70_000));
    }

    @Test
    void emptyRangeReportsZeros() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations")
                .header("X-Staff-Session", hqToken)
                .param("from", LocalDate.now().minusDays(30).toString())
                .param("to", LocalDate.now().minusDays(20).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.reservations").value(0))
                .andExpect(jsonPath("$.totals.revenueKrw").value(0))
                .andExpect(jsonPath("$.previousPeriod.totals.reservations").value(0))
                .andExpect(jsonPath("$.previousPeriod.totals.cancelled").value(0))
                .andExpect(jsonPath("$.previousPeriod.totals.noShow").value(0))
                .andExpect(jsonPath("$.previousPeriod.totals.expired").value(0))
                .andExpect(jsonPath("$.previousPeriod.totals.revenueKrw").value(0))
                .andExpect(jsonPath("$.previousPeriod.totals.changeRequestsPending").value(0))
                .andExpect(jsonPath("$.previousPeriod.totals.changeRequestsCompleted").value(0));
    }

    @Test
    void branchStaffCannotReadReport() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations")
                .header("X-Staff-Session", sokchoToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingSessionIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reversedRangeIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations")
                .header("X-Staff-Session", hqToken)
                .param("from", LocalDate.now().toString())
                .param("to", LocalDate.now().minusDays(1).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rangeBeyondLimitIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations")
                .header("X-Staff-Session", hqToken)
                .param("from", LocalDate.now().minusDays(100).toString())
                .param("to", LocalDate.now().toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inclusiveNinetyTwoDayRangeIsAcceptedAndNinetyThreeDaysIsRejected() throws Exception {
        LocalDate to = LocalDate.now(ZoneId.of("Asia/Seoul"));

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations")
                .header("X-Staff-Session", hqToken)
                .param("from", to.minusDays(91).toString())
                .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(92));

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations")
                .header("X-Staff-Session", hqToken)
                .param("from", to.minusDays(92).toString())
                .param("to", to.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void xlsxExportKeepsReportAuthorizationAndHotelValidation() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations/export.xlsx"))
                .andExpect(status().isUnauthorized());

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations/export.xlsx")
                .header("X-Staff-Session", sokchoToken))
                .andExpect(status().isForbidden());

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations/export.xlsx")
                .header("X-Staff-Session", hqToken)
                .param("hotelId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void xlsxExportAppliesAValidHotelFilter() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        byte[] workbookBytes = mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations/export.xlsx")
                .header("X-Staff-Session", hqToken)
                .param("from", today.toString())
                .param("to", today.toString())
                .param("hotelId", JEJU.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook =
                new org.apache.poi.xssf.usermodel.XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            org.apache.poi.ss.usermodel.Sheet hotels = workbook.getSheet("지점");
            org.assertj.core.api.Assertions.assertThat(hotels.getLastRowNum()).isEqualTo(1);
            org.assertj.core.api.Assertions.assertThat(hotels.getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo("report-jeju");
        }
    }

    @Test
    void unknownHotelIsNotFound() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations")
                .header("X-Staff-Session", hqToken)
                .param("hotelId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void headquartersReadsRoomTypeRevenue() throws Exception {
        // 속초의 4건은 전부 standard 유형이므로 한 유형이 100%를 차지한다.
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations/room-types")
                .header("X-Staff-Session", hqToken)
                .param("hotelId", SOKCHO.toString())
                .param("from", LocalDate.now().toString())
                .param("to", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(1))
                .andExpect(jsonPath("$.hotelName").value("report-sokcho"))
                .andExpect(jsonPath("$.roomTypes", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.roomTypes[0].roomTypeName").value("standard"))
                .andExpect(jsonPath("$.roomTypes[0].reservations").value(6))
                .andExpect(jsonPath("$.roomTypes[0].cancelled").value(1))
                .andExpect(jsonPath("$.roomTypes[0].noShow").value(1))
                // 4건의 확정 예약만 매출로 인정한다.
                .andExpect(jsonPath("$.roomTypes[0].revenueKrw").value(400_000))
                .andExpect(jsonPath("$.roomTypes[0].revenueShare").value(Matchers.closeTo(1.0d, 0.000001d)))
                .andExpect(jsonPath("$.totals.reservations").value(6))
                .andExpect(jsonPath("$.totals.revenueKrw").value(400_000));
    }

    @Test
    void roomTypeRevenueDefaultsToLastSevenDays() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations/room-types")
                .header("X-Staff-Session", hqToken)
                .param("hotelId", SOKCHO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(7))
                .andExpect(jsonPath("$.from").value(LocalDate.now().minusDays(6).toString()))
                .andExpect(jsonPath("$.to").value(LocalDate.now().toString()));
    }

    @Test
    void roomTypeRevenueOfEmptyRangeIsZero() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations/room-types")
                .header("X-Staff-Session", hqToken)
                .param("hotelId", SOKCHO.toString())
                .param("from", LocalDate.now().minusDays(30).toString())
                .param("to", LocalDate.now().minusDays(20).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomTypes", Matchers.hasSize(0)))
                .andExpect(jsonPath("$.totals.reservations").value(0))
                // 매출이 0원이면 비중을 계산하지 않는다.
                .andExpect(jsonPath("$.totals.revenueKrw").value(0));
    }

    @Test
    void roomTypeRevenueRequiresHotel() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations/room-types")
                .header("X-Staff-Session", hqToken)
                .param("from", LocalDate.now().toString())
                .param("to", LocalDate.now().toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void roomTypeRevenueOfUnknownHotelIsNotFound() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations/room-types")
                .header("X-Staff-Session", hqToken)
                .param("hotelId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void roomTypeRevenueReversedRangeIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations/room-types")
                .header("X-Staff-Session", hqToken)
                .param("hotelId", SOKCHO.toString())
                .param("from", LocalDate.now().toString())
                .param("to", LocalDate.now().minusDays(1).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void branchStaffCannotReadRoomTypeRevenue() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations/room-types")
                .header("X-Staff-Session", sokchoToken)
                .param("hotelId", SOKCHO.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void roomTypeRevenueRequiresSession() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/reports/operations/room-types")
                .param("hotelId", SOKCHO.toString()))
                .andExpect(status().isUnauthorized());
    }

    private UUID insertReservation(UUID roomTypeId, UUID ratePlanId, String status, long total) {
        UUID reservationId = UUID.randomUUID();
        jdbc.update("""
                insert into reservation
                    (id, room_type_id, rate_plan_id, check_in, check_out, adults, children, rooms,
                     status, total_krw, currency, expires_at, guest_name, guest_email,
                     management_token_hash, policy_snapshot)
                values (?, ?, ?, ?, ?, 2, 0, 1, ?, ?, 'KRW', now() + interval '1 hour',
                        'report-guest', 'guest@example.com', 'report-token-hash', '{}'::jsonb)
                """, reservationId, roomTypeId, ratePlanId,
                LocalDate.now().plusDays(5), LocalDate.now().plusDays(7), status, total);
        return reservationId;
    }

    private UUID insertReservationAt(
            UUID roomTypeId,
            UUID ratePlanId,
            String status,
            long total,
            Timestamp createdAt) {
        UUID reservationId = UUID.randomUUID();
        jdbc.update("""
                insert into reservation
                    (id, room_type_id, rate_plan_id, check_in, check_out, adults, children, rooms,
                     status, total_krw, currency, expires_at, guest_name, guest_email,
                     management_token_hash, policy_snapshot, created_at)
                values (?, ?, ?, ?, ?, 2, 0, 1, ?, ?, 'KRW', now() + interval '1 hour',
                        'report-guest', 'guest@example.com', 'report-token-hash', '{}'::jsonb, ?)
                """, reservationId, roomTypeId, ratePlanId,
                LocalDate.now().plusDays(5), LocalDate.now().plusDays(7), status, total, createdAt);
        return reservationId;
    }

    private String unzipText(byte[] workbook) throws Exception {
        ByteArrayOutputStream text = new ByteArrayOutputStream();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(workbook))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().endsWith(".xml") || entry.getName().endsWith(".rels")) {
                    zip.transferTo(text);
                    text.write('\n');
                }
            }
        }
        return text.toString(StandardCharsets.UTF_8);
    }

    private void insertChangeRequest(UUID reservationId, UUID hotelId, String status, String idempotencyKey) {
        jdbc.update("""
                insert into reservation_change_request
                    (id, reservation_id, hotel_id, base_operation_revision, status, settlement_direction,
                     requested_by, idempotency_key, request_hash, previous_check_in, previous_check_out,
                     previous_room_type_id, previous_rate_plan_id, target_check_in, target_check_out,
                     target_room_type_id, target_rate_plan_id, rooms, adults, children, approval_expires_at, version)
                values (?, ?, ?, 0, ?, 'NONE', ?, ?, 'hash', ?, ?, ?, ?, ?, ?, ?, ?, 1, 2, 0, now() + interval '1 hour', 1)
                """, UUID.randomUUID(), reservationId, hotelId, status, hqStaffId, idempotencyKey,
                LocalDate.now().plusDays(5), LocalDate.now().plusDays(7),
                SOKCHO_ROOM_TYPE, SOKCHO_RATE_PLAN,
                LocalDate.now().plusDays(6), LocalDate.now().plusDays(8),
                SOKCHO_ROOM_TYPE, SOKCHO_RATE_PLAN);
    }

    private void clean() {
        jdbc.update("delete from staff_session");
        jdbc.update("delete from reservation_change_request");
        jdbc.update("delete from reservation_night");
        jdbc.update("delete from reservation");
        jdbc.update("delete from inventory_day");
        jdbc.update("delete from rate_plan");
        jdbc.update("delete from room_type");
        jdbc.update("delete from staff_member");
        jdbc.update("delete from hotel where id in (?, ?)", SOKCHO, JEJU);
    }
}
