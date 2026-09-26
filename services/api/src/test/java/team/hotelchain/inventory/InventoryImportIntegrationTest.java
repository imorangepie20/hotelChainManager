package team.hotelchain.inventory;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import team.hotelchain.staff.StaffAccessService;

/**
 * 본사가 CSV로 여러 객실 유형의 여러 날짜 재고를 한 번에 바꾸는 동작을
 * 검증한다.
 * <p>
 * 내보낸 파일을 그대로 다시 올리면 같은 재고가 유지되어야 하고, 한 행이라도
 * 거부되면 전체를 롤백해야 한다.
 */
@SpringBootTest
class InventoryImportIntegrationTest {

    private static final UUID SOKCHO = UUID.fromString("17000000-0000-0000-0000-000000000001");
    private static final UUID JEJU = UUID.fromString("17000000-0000-0000-0000-000000000002");
    private static final UUID STANDARD = UUID.fromString("27000000-0000-0000-0000-000000000001");
    private static final UUID SUITE = UUID.fromString("27000000-0000-0000-0000-000000000002");
    private static final String HQ_EMAIL = "inventory-import-hq@example.com";
    private static final String SOKCHO_EMAIL = "inventory-import-sokcho@example.com";

    private static final LocalDate DAY = LocalDate.of(2027, 1, 1);

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

        jdbc.update("insert into hotel values (?, ?, ?, ?)", SOKCHO, "재고 가져오기 속초", "속초", "Asia/Seoul");
        jdbc.update("insert into hotel values (?, ?, ?, ?)", JEJU, "재고 가져오기 제주", "제주도", "Asia/Seoul");
        jdbc.update("insert into room_type (id, hotel_id, name, max_occupancy) values (?, ?, ?, ?)", STANDARD, SOKCHO, "스탠다드", 2);
        jdbc.update("insert into room_type (id, hotel_id, name, max_occupancy) values (?, ?, ?, ?)", SUITE, SOKCHO, "스위트", 4);

        jdbc.update("insert into inventory_day (room_type_id, stay_date, capacity, held, confirmed) values (?, ?, 10, 0, 2)", STANDARD, DAY);
        jdbc.update("insert into inventory_day (room_type_id, stay_date, capacity, held, confirmed) values (?, ?, 10, 0, 2)", STANDARD, DAY.plusDays(1));
        jdbc.update("insert into inventory_day (room_type_id, stay_date, capacity, held, confirmed) values (?, ?, 6, 0, 0)", SUITE, DAY);

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
    void headquartersExportsInventoryAsCsv() throws Exception {
        MvcResult result = mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/inventory/export", SOKCHO)
                .param("from", DAY.toString())
                .param("to", DAY.plusDays(1).toString())
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andReturn();

        String csv = result.getResponse().getContentAsString();
        // 헤더가 있고 객실 유형·날짜·총량이 들어 있다.
        org.assertj.core.api.Assertions.assertThat(csv).contains("객실 유형 ID");
        org.assertj.core.api.Assertions.assertThat(csv).contains("스탠다드");
        org.assertj.core.api.Assertions.assertThat(csv).contains(DAY.toString());
    }

    @Test
    void exportedCsvRoundTripsToSameCapacity() throws Exception {
        String exported = exportCsv();
        // 내려받은 파일을 고치지 않고 그대로 다시 올린다.
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/inventory/import", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "round-trip")
                .contentType("text/csv")
                .content(exported.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(true));

        // 총량이 그대로 유지된다.
        Integer capacity = jdbc.queryForObject(
                "select capacity from inventory_day where room_type_id = ? and stay_date = ?",
                Integer.class, STANDARD, DAY);
        org.assertj.core.api.Assertions.assertThat(capacity).isEqualTo(10);
    }

    @Test
    void headquartersSetsDifferentCapacityPerDayInOneFile() throws Exception {
        String csv = csvWithTwoDays(STANDARD, 8, 12);

        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/inventory/import", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "different-per-day")
                .contentType("text/csv")
                .content(csv.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.appliedRows").value(2));

        // 날짜마다 다른 총량이 한 파일로 반영된다. 이것이 대화상자로는
        // 할 수 없었던 동작이다.
        Integer first = jdbc.queryForObject(
                "select capacity from inventory_day where room_type_id = ? and stay_date = ?",
                Integer.class, STANDARD, DAY);
        Integer second = jdbc.queryForObject(
                "select capacity from inventory_day where room_type_id = ? and stay_date = ?",
                Integer.class, STANDARD, DAY.plusDays(1));
        org.assertj.core.api.Assertions.assertThat(first).isEqualTo(8);
        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(12);
    }

    @Test
    void headquartersUpdatesMultipleRoomTypesInOneFile() throws Exception {
        String csv = csvForTwoRoomTypes();

        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/inventory/import", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "two-room-types")
                .contentType("text/csv")
                .content(csv.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalRows").value(2));

        Integer standard = jdbc.queryForObject(
                "select capacity from inventory_day where room_type_id = ? and stay_date = ?",
                Integer.class, STANDARD, DAY);
        Integer suite = jdbc.queryForObject(
                "select capacity from inventory_day where room_type_id = ? and stay_date = ?",
                Integer.class, SUITE, DAY);
        org.assertj.core.api.Assertions.assertThat(standard).isEqualTo(9);
        org.assertj.core.api.Assertions.assertThat(suite).isEqualTo(4);
    }

    @Test
    void conflictingRowRejectsWholeFile() throws Exception {
        // 첫째 날은 올바르고 둘째 날은 확정 2건 아래로 내린다.
        String csv = csvWithTwoDays(STANDARD, 8, 1);

        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/inventory/import", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "conflict-row")
                .contentType("text/csv")
                .content(csv.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVENTORY_CAPACITY_CONFLICT"));

        // 한 트랜잭션이므로 첫째 날도 바뀌지 않는다.
        Integer first = jdbc.queryForObject(
                "select capacity from inventory_day where room_type_id = ? and stay_date = ?",
                Integer.class, STANDARD, DAY);
        Integer second = jdbc.queryForObject(
                "select capacity from inventory_day where room_type_id = ? and stay_date = ?",
                Integer.class, STANDARD, DAY.plusDays(1));
        org.assertj.core.api.Assertions.assertThat(first).isEqualTo(10);
        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(10);
    }

    @Test
    void readOnlyColumnsAreIgnored() throws Exception {
        // 보류·확정·잔여·판매 상태 열을 본사가 바꾸더라도 총량만 반영한다.
        String csv = HEADER + "\r\n"
                + STANDARD + ",스탠다드," + DAY + ",7,9,9,-5,STOPPED,2\r\n";

        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/inventory/import", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "read-only-columns")
                .contentType("text/csv")
                .content(csv.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated());

        Integer held = jdbc.queryForObject(
                "select held from inventory_day where room_type_id = ? and stay_date = ?",
                Integer.class, STANDARD, DAY);
        Integer confirmed = jdbc.queryForObject(
                "select confirmed from inventory_day where room_type_id = ? and stay_date = ?",
                Integer.class, STANDARD, DAY);
        Integer capacity = jdbc.queryForObject(
                "select capacity from inventory_day where room_type_id = ? and stay_date = ?",
                Integer.class, STANDARD, DAY);
        org.assertj.core.api.Assertions.assertThat(held).isEqualTo(0);
        org.assertj.core.api.Assertions.assertThat(confirmed).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(capacity).isEqualTo(7);
    }

    @Test
    void sameIdempotencyKeyReturnsSameResult() throws Exception {
        byte[] csv = csvWithTwoDays(STANDARD, 8, 12).getBytes(StandardCharsets.UTF_8);
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/inventory/import", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "import-once")
                .contentType("text/csv")
                .content(csv))
                .andExpect(status().isCreated());

        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/inventory/import", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "import-once")
                .contentType("text/csv")
                .content(csv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false));

        // 재고는 한 번만 바뀐다.
        Integer commands = jdbc.queryForObject(
                "select count(*) from inventory_command where idempotency_key = ?",
                Integer.class, "import-once");
        org.assertj.core.api.Assertions.assertThat(commands).isEqualTo(1);
    }

    @Test
    void sameFileWithNewIdempotencyKeyReturnsSameResult() throws Exception {
        byte[] csv = csvWithTwoDays(STANDARD, 8, 12).getBytes(StandardCharsets.UTF_8);
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/inventory/import", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "import-first")
                .contentType("text/csv")
                .content(csv))
                .andExpect(status().isCreated());

        // 응답 유실 뒤 클라이언트가 새 멱원 키로 같은 파일을 보낸다.
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/inventory/import", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "import-retry")
                .contentType("text/csv")
                .content(csv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false));

        Integer commands = jdbc.queryForObject(
                "select count(*) from inventory_command where staff_id in (select id from staff_member where email = ?)",
                Integer.class, HQ_EMAIL);
        org.assertj.core.api.Assertions.assertThat(commands).isEqualTo(1);
    }

    @Test
    void missingHeaderIsRejected() throws Exception {
        String csv = STANDARD + ",스탠다드," + DAY + ",8\r\n";

        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/inventory/import", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "no-header")
                .contentType("text/csv")
                .content(csv.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVENTORY_IMPORT_FORMAT"));
    }

    @Test
    void nonNumericCapacityIsRejected() throws Exception {
        String csv = HEADER + "\r\n"
                + STANDARD + ",스탠다드," + DAY + ",많음,0,2,8,OPEN,2\r\n";

        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/inventory/import", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "non-numeric")
                .contentType("text/csv")
                .content(csv.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void badDateIsRejected() throws Exception {
        String csv = HEADER + "\r\n"
                + STANDARD + ",스탠다드,2027-01-99,8,0,2,8,OPEN,2\r\n";

        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/inventory/import", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "bad-date")
                .contentType("text/csv")
                .content(csv.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void capacityOutOfRangeIsRejected() throws Exception {
        String csv = HEADER + "\r\n"
                + STANDARD + ",스탠다드," + DAY + ",1001,0,2,8,OPEN,2\r\n";

        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/inventory/import", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "too-high")
                .contentType("text/csv")
                .content(csv.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void branchStaffCannotImport() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/inventory/import", SOKCHO)
                .header("X-Staff-Session", sokchoToken)
                .header("Idempotency-Key", "branch-import")
                .contentType("text/csv")
                .content(csvWithTwoDays(STANDARD, 8, 12).getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isForbidden());

        Integer capacity = jdbc.queryForObject(
                "select capacity from inventory_day where room_type_id = ? and stay_date = ?",
                Integer.class, STANDARD, DAY);
        org.assertj.core.api.Assertions.assertThat(capacity).isEqualTo(10);
    }

    @Test
    void missingSessionIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/inventory/import", SOKCHO)
                .header("Idempotency-Key", "no-session")
                .contentType("text/csv")
                .content(csvWithTwoDays(STANDARD, 8, 12).getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/inventory/import", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .contentType("text/csv")
                .content(csvWithTwoDays(STANDARD, 8, 12).getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownHotelIsNotFound() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/inventory/import", UUID.randomUUID())
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "unknown-hotel")
                .contentType("text/csv")
                .content(csvWithTwoDays(STANDARD, 8, 12).getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isNotFound());
    }

    @Test
    void roomTypeFromAnotherHotelIsRejected() throws Exception {
        // STANDARD는 속초 소속인데 제주 호텔 경로로 올린다.
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/hotels/{hotelId}/inventory/import", JEJU)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "other-hotel")
                .contentType("text/csv")
                .content(csvWithTwoDays(STANDARD, 8, 12).getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isNotFound());
    }

    private static final String HEADER = "객실 유형 ID,객실 유형,숙박일,총량,보류,확정,잔여,판매 상태,최대 인원";

    private String exportCsv() throws Exception {
        MvcResult result = mvc.perform(MockMvcRequestBuilders.get("/api/staff/hotels/{hotelId}/inventory/export", SOKCHO)
                .param("from", DAY.toString())
                .param("to", DAY.plusDays(1).toString())
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private String csvWithTwoDays(UUID roomTypeId, int firstCapacity, int secondCapacity) {
        return HEADER + "\r\n"
                + roomTypeId + ",스탠다드," + DAY + "," + firstCapacity + ",0,2," + (firstCapacity - 2) + ",OPEN,2\r\n"
                + roomTypeId + ",스탠다드," + DAY.plusDays(1) + "," + secondCapacity + ",0,2," + (secondCapacity - 2) + ",OPEN,2\r\n";
    }

    private String csvForTwoRoomTypes() {
        return HEADER + "\r\n"
                + STANDARD + ",스탠다드," + DAY + ",9,0,2,7,OPEN,2\r\n"
                + SUITE + ",스위트," + DAY + ",4,0,0,4,OPEN,4\r\n";
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
