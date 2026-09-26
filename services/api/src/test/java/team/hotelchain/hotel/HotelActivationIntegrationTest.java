package team.hotelchain.hotel;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import team.hotelchain.staff.StaffAccessService;

/**
 * 본사가 지점의 판매를 중지·재개하는 동작을 검증한다.
 * <p>
 * 중지한 지점은 고객 {@code GET /api/hotels}·{@code GET /api/availability}에서 빠진다.
 * 이미 확정된 예약은 중지가 취소하지 않는다.
 */
@SpringBootTest
class HotelActivationIntegrationTest {

    private static final UUID SOKCHO = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID HQ_EMAIL_ID = UUID.randomUUID();
    private static final String HQ_EMAIL = "hotel-active-hq@example.com";
    private static final String SOKCHO_EMAIL = "hotel-active-sokcho@example.com";

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

        jdbc.update("insert into hotel values (?, ?, ?, ?)", SOKCHO, "중지 속초", "속초", "Asia/Seoul");
        seedSellableRoomType();

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, ?, ?)",
                HQ_EMAIL_ID, HQ_EMAIL, "본사 관리자", encoder.encode("hq-password"), "HQ_ADMIN", null);
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), SOKCHO_EMAIL, "속초 직원", encoder.encode("branch-password"), "BRANCH_STAFF", SOKCHO);

        hqToken = staffAccess.login(HQ_EMAIL, "hq-password").token();
        sokchoToken = staffAccess.login(SOKCHO_EMAIL, "branch-password").token();
    }

    // 고객 검색에서 오퍼가 나오도록 객실 유형·요금제·일자 재고를 하나씩 만든다.
    private void seedSellableRoomType() {
        UUID roomTypeId = UUID.fromString("23000000-0000-0000-0000-000000000010");
        UUID ratePlanId = UUID.fromString("33000000-0000-0000-0000-000000000010");
        jdbc.update("insert into room_type (id, hotel_id, name, max_occupancy) values (?, ?, ?, ?)",
                roomTypeId, SOKCHO, "스탠다드", 2);
        jdbc.update("insert into rate_plan (id, room_type_id, name, breakfast_included, policy_version) values (?, ?, ?, false, 'FLEX-2026-01')",
                ratePlanId, roomTypeId, "객실만");
        LocalDate day = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        for (int offset = 0; offset < 7; offset++) {
            LocalDate stayDate = day.plusDays(offset);
            jdbc.update("insert into rate_day (rate_plan_id, stay_date, amount_krw) values (?, ?, ?)",
                    ratePlanId, stayDate, 100000);
            jdbc.update("insert into inventory_day (room_type_id, stay_date, capacity) values (?, ?, 4)",
                    roomTypeId, stayDate);
        }
    }

    @AfterEach
    void cleanAfter() {
        clean();
    }

    @Test
    void headquartersStopsSales() throws Exception {
        setActive("stop", status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.changed").value(true))
                .andExpect(jsonPath("$.name").value("중지 속초"));

        Boolean active = jdbc.queryForObject(
                "select active from hotel where id = ?", Boolean.class, SOKCHO);
        org.assertj.core.api.Assertions.assertThat(active).isFalse();
    }

    @Test
    void stoppedHotelIsHiddenFromCustomers() throws Exception {
        // 중지하기 전에는 고객 지점 목록과 가용성에 나타난다.
        mvc.perform(get("/api/hotels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == '중지 속초')]").exists());

        setActive("stop", status().isOk());

        // 중지하면 고객 지점 목록에서 빠진다.
        mvc.perform(get("/api/hotels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == '중지 속초')]").doesNotExist());
    }

    @Test
    void stoppedHotelReturnsNoOffers() throws Exception {
        // 중지 전에는 오퍼가 내려온다.
        mvc.perform(availabilityQuery())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers[0].roomTypeName").value("스탠다드"));

        setActive("stop", status().isOk());

        // 중지하면 빈 오퍼가 내려온다. 예약할 수 없다는 것이지 오류가 아니다.
        mvc.perform(availabilityQuery())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers").isEmpty());
    }

    @Test
    void stoppedSalesResume() throws Exception {
        setActive("stop", status().isOk());
        // 재개 요청은 활성으로 보낸다. 헬퍼의 기본값이 중지이므로 본문을 명시한다.
        setActive("resume", "{\"active\":true}", status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.changed").value(true));

        mvc.perform(get("/api/hotels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == '중지 속초')]").exists());

        mvc.perform(availabilityQuery())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers[0].roomTypeName").value("스탠다드"));
    }

    @Test
    void idempotencyReplayReturnsSameResult() throws Exception {
        // 같은 키 재호출은 200 changed=false로 같은 결과를 돌려준다.
        setActive("replay", status().isOk()).andExpect(jsonPath("$.changed").value(true));
        setActive("replay", status().isOk())
                .andExpect(jsonPath("$.changed").value(false))
                .andExpect(jsonPath("$.active").value(false));

        Integer commandCount = jdbc.queryForObject("""
                select count(*) from hotel_command
                 where hotel_id = ? and kind = 'ACTIVATE' and idempotency_key = 'replay'
                """, Integer.class, SOKCHO);
        org.assertj.core.api.Assertions.assertThat(commandCount).isEqualTo(1);
    }

    @Test
    void newKeyWithSameContentReturnsSameResult() throws Exception {
        // 응답을 잃은 뒤 새 키로 같은 내용을 보내도 SHA-256 지문이 같아 같은 결과를 돌려준다.
        setActive("first-key", status().isOk()).andExpect(jsonPath("$.changed").value(true));
        setActive("second-key", status().isOk())
                .andExpect(jsonPath("$.changed").value(false))
                .andExpect(jsonPath("$.active").value(false));

        // 지문이 같은 재시도는 두 번째 멱원 행을 만들지 않는다.
        Integer activationRecords = jdbc.queryForObject(
                "select count(*) from hotel_command where hotel_id = ? and kind = 'ACTIVATE'",
                Integer.class, SOKCHO);
        org.assertj.core.api.Assertions.assertThat(activationRecords).isEqualTo(1);

        // 중지가 두 번 적용되지 않고 한 번만 바뀐다.
        Boolean active = jdbc.queryForObject(
                "select active from hotel where id = ?", Boolean.class, SOKCHO);
        org.assertj.core.api.Assertions.assertThat(active).isFalse();
    }

    @Test
    void sameStatusDoesNotTouchDatabase() throws Exception {
        // 이미 활성인데 다시 활성으로 보내면 DB를 건드리지 않고 멱원 기록만 남긴다.
        setActive("no-op", "{\"active\":true}", status().isOk())
                .andExpect(jsonPath("$.changed").value(false));

        Boolean active = jdbc.queryForObject(
                "select active from hotel where id = ?", Boolean.class, SOKCHO);
        org.assertj.core.api.Assertions.assertThat(active).isTrue();

        // 멱원 기록은 있어야 재호출이 같은 결과를 찾을 수 있다.
        Integer commandCount = jdbc.queryForObject("""
                select count(*) from hotel_command
                 where hotel_id = ? and kind = 'ACTIVATE' and idempotency_key = 'no-op'
                """, Integer.class, SOKCHO);
        org.assertj.core.api.Assertions.assertThat(commandCount).isEqualTo(1);
    }

    @Test
    void stoppedHotelStillServesExistingReservations() throws Exception {
        // 중지는 신규 판매에만 적용된다. 확정 예약은 취소하지 않는다.
        UUID reservationId = seedConfirmedReservation();

        setActive("stop", status().isOk());

        Integer stillThere = jdbc.queryForObject(
                "select count(*) from reservation where id = ? and status = 'CONFIRMED'",
                Integer.class, reservationId);
        org.assertj.core.api.Assertions.assertThat(stillThere).isEqualTo(1);
    }

    @Test
    void branchStaffCannotChangeActivation() throws Exception {
        // 판매 중지·재개는 본사 전용이다. 지점 직원은 403이다.
        mvc.perform(patch("/api/staff/hotels/{hotelId}/active", SOKCHO)
                .header("X-Staff-Session", sokchoToken)
                .header("Idempotency-Key", "branch-stop")
                .contentType("application/json")
                .content("{\"active\":false}"))
                .andExpect(status().isForbidden());

        Boolean active = jdbc.queryForObject(
                "select active from hotel where id = ?", Boolean.class, SOKCHO);
        org.assertj.core.api.Assertions.assertThat(active).isTrue();
    }

    @Test
    void missingSessionIsRejected() throws Exception {
        mvc.perform(patch("/api/staff/hotels/{hotelId}/active", SOKCHO)
                .header("Idempotency-Key", "no-session")
                .contentType("application/json")
                .content("{\"active\":false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        mvc.perform(patch("/api/staff/hotels/{hotelId}/active", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .contentType("application/json")
                .content("{\"active\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownHotelIsNotFound() throws Exception {
        UUID missing = UUID.fromString("21000000-0000-0000-0000-000000000099");
        mvc.perform(patch("/api/staff/hotels/{hotelId}/active", missing)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "missing")
                .contentType("application/json")
                .content("{\"active\":false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingActiveFieldIsRejected() throws Exception {
        setActive("missing-field", "{}", status().isBadRequest());
    }

    @Test
    void staffListIncludesStoppedHotels() throws Exception {
        // 직원은 중지한 지점도 본다. 다시 판매하려면 상태를 알아야 한다.
        setActive("stop", status().isOk());

        mvc.perform(get("/api/staff/hotels")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == '중지 속초')].active").value(false));
    }

    private org.springframework.test.web.servlet.ResultActions setActive(
            String idempotencyKey, ResultMatcher expected) throws Exception {
        return setActive(idempotencyKey, "{\"active\":false}", expected);
    }

    private org.springframework.test.web.servlet.ResultActions setActive(
            String idempotencyKey, String body, ResultMatcher expected) throws Exception {
        return mvc.perform(patch("/api/staff/hotels/{hotelId}/active", SOKCHO)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType("application/json")
                .content(body))
                .andExpect(expected);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder availabilityQuery() {
        LocalDate day = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        return get("/api/availability")
                .param("hotelId", SOKCHO.toString())
                .param("checkIn", day.plusDays(1).toString())
                .param("checkOut", day.plusDays(3).toString())
                .param("adults", "2");
    }

    // 중지가 확정 예약에 영향을 주지 않는지 확인하려면 예약이 필요하다.
    private UUID seedConfirmedReservation() {
        UUID roomTypeId = UUID.fromString("23000000-0000-0000-0000-000000000010");
        UUID ratePlanId = UUID.fromString("33000000-0000-0000-0000-000000000010");
        UUID reservationId = UUID.randomUUID();
        LocalDate day = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(1);
        String tokenHash = sha256("management-token-for-test");
        jdbc.update("""
                insert into reservation (id, room_type_id, rate_plan_id, check_in, check_out, rooms,
                                         adults, children, status, total_krw, currency, expires_at,
                                         guest_name, guest_email, management_token_hash, policy_snapshot)
                values (?, ?, ?, ?, ?, 1, 2, 0, 'CONFIRMED', 200000, 'KRW', now() + interval '1 hour',
                        '테스트 고객', 'guest@example.com', ?, '{}')
                """, reservationId, roomTypeId, ratePlanId, day, day.plusDays(2), tokenHash);
        jdbc.update("update inventory_day set confirmed = 1 where room_type_id = ? and stay_date >= ? and stay_date < ?",
                roomTypeId, day, day.plusDays(2));
        return reservationId;
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void clean() {
        // staff_session이 staff_member를 참조하고 staff_member가 hotel을 참조하므로
        // 세션 → 직원 → 예약 → 지점 순으로 지운다.
        jdbc.update("delete from staff_session");
        jdbc.update("delete from hotel_command");
        jdbc.update("delete from reservation_night");
        jdbc.update("delete from reservation");
        jdbc.update("delete from inventory_day");
        jdbc.update("delete from rate_day");
        jdbc.update("delete from rate_plan");
        jdbc.update("delete from room_type");
        jdbc.update("delete from staff_member where email in (?, ?)", HQ_EMAIL, SOKCHO_EMAIL);
        jdbc.update("delete from hotel where id = ?", SOKCHO);
    }
}
