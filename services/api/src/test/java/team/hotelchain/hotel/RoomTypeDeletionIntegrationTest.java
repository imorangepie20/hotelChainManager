package team.hotelchain.hotel;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import team.hotelchain.staff.StaffAccessService;

/**
 * 본사가 객실 유형을 지우는 동작을 검증한다.
 * <p>
 * 진행 중인 판매·예약·배정이 있으면 409로 거부하고, 종료된 예약과 이력은
 * 삭제 후에도 그대로 둔다.
 */
@SpringBootTest
class RoomTypeDeletionIntegrationTest {

    private static final UUID SOKCHO = UUID.fromString("18000000-0000-0000-0000-000000000001");
    private static final UUID JEJU = UUID.fromString("18000000-0000-0000-0000-000000000002");
    private static final UUID STANDARD = UUID.fromString("28000000-0000-0000-0000-000000000001");
    private static final UUID UNUSED = UUID.fromString("28000000-0000-0000-0000-000000000002");
    private static final UUID HQ_EMAIL_ID = UUID.randomUUID();
    private static final String HQ_EMAIL = "room-type-delete-hq@example.com";
    private static final String SOKCHO_EMAIL = "room-type-delete-sokcho@example.com";
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

        jdbc.update("insert into hotel values (?, ?, ?, ?)", SOKCHO, "삭제 속초", "속초", "Asia/Seoul");
        jdbc.update("insert into hotel values (?, ?, ?, ?)", JEJU, "삭제 제주", "제주도", "Asia/Seoul");
        // V53 이후 room_type은 seed_breakfast_included·seed_default_rate_krw 열을 가진다.
        jdbc.update("insert into room_type (id, hotel_id, name, max_occupancy) values (?, ?, ?, ?)",
                STANDARD, SOKCHO, "스탠다드", 2);
        jdbc.update("insert into room_type (id, hotel_id, name, max_occupancy) values (?, ?, ?, ?)",
                UNUSED, SOKCHO, "미사용", 2);
        seedSellablePlan(STANDARD);

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, ?, ?)",
                HQ_EMAIL_ID, HQ_EMAIL, "본사 관리자", encoder.encode("hq-password"), "HQ_ADMIN", null);
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), SOKCHO_EMAIL, "속초 직원", encoder.encode("branch-password"), "BRANCH_STAFF", SOKCHO);

        hqToken = staffAccess.login(HQ_EMAIL, "hq-password").token();
        sokchoToken = staffAccess.login(SOKCHO_EMAIL, "branch-password").token();
    }

    // 고객 검색에서 오퍼가 나오도록 요금제·일자 재고를 만든다.
    private void seedSellablePlan(UUID roomTypeId) {
        UUID ratePlanId = UUID.randomUUID();
        jdbc.update("insert into rate_plan (id, room_type_id, name, breakfast_included, policy_version) values (?, ?, ?, false, 'FLEX-2026-01')",
                ratePlanId, roomTypeId, "객실만");
        for (int offset = 0; offset < 7; offset++) {
            jdbc.update("insert into rate_day (rate_plan_id, stay_date, amount_krw) values (?, ?, 100000)",
                    ratePlanId, DAY.plusDays(offset));
            jdbc.update("insert into inventory_day (room_type_id, stay_date, capacity) values (?, ?, 4)",
                    roomTypeId, DAY.plusDays(offset));
        }
    }

    @AfterEach
    void cleanAfter() {
        clean();
    }

    @Test
    void headquartersDeletesUnusedRoomType() throws Exception {
        // 요금·재고·예약이 없는 유형은 지울 수 있다.
        deleteRoomType(UNUSED, "delete-unused", status().isOk())
                .andExpect(jsonPath("$.deleted").value(true))
                .andExpect(jsonPath("$.name").value("미사용"))
                .andExpect(jsonPath("$.remainingRoomTypes").value(1));

        Integer remaining = jdbc.queryForObject(
                "select count(*) from room_type where hotel_id = ?", Integer.class, SOKCHO);
        org.assertj.core.api.Assertions.assertThat(remaining).isEqualTo(1);
    }

    @Test
    void deletionRemovesInventoryAndDetachesRates() throws Exception {
        deleteRoomType(STANDARD, "delete-seeded", status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        // 재고는 유형 없이 의미가 없으므로 지운다.
        Integer inventory = jdbc.queryForObject(
                "select count(*) from inventory_day where room_type_id = ?", Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(inventory).isZero();

        // 요금제는 종료된 예약의 스냅샷 근거이므로 남기고 연결만 끊는다.
        // AvailabilityService가 room_type을 거쳐 join하므로 유형이 없으면
        // 고객 검색에 나타나지 않는다.
        Integer ratePlans = jdbc.queryForObject(
                "select count(*) from rate_plan where room_type_id = ?", Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(ratePlans).isZero();

        Integer rateDays = jdbc.queryForObject("""
                select count(*) from rate_day rd
                  join rate_plan rp on rp.id = rd.rate_plan_id
                 where rp.room_type_id is null
                """, Integer.class);
        org.assertj.core.api.Assertions.assertThat(rateDays).isEqualTo(7);
    }

    @Test
    void deletedRoomTypeDisappearsFromCustomerSearch() throws Exception {
        // 삭제 전에는 고객 가용성에 나타난다.
        mvc.perform(availabilityQuery())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers[0].roomTypeName").value("스탠다드"));

        deleteRoomType(STANDARD, "delete-from-search", status().isOk());

        // 삭제 뒤에는 오퍼가 내려가지 않는다.
        mvc.perform(availabilityQuery())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers").isEmpty());
    }

    @Test
    void deletedRoomTypeDisappearsFromStaffCatalog() throws Exception {
        mvc.perform(get("/api/staff/hotels/{hotelId}/room-types", SOKCHO)
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2));

        deleteRoomType(STANDARD, "delete-from-catalog", status().isOk());

        mvc.perform(get("/api/staff/hotels/{hotelId}/room-types", SOKCHO)
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.roomTypes[0].name").value("미사용"));
    }

    @Test
    void confirmedReservationBlocksDeletion() throws Exception {
        seedConfirmedReservation();

        deleteRoomType(STANDARD, "delete-with-reservation", status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_TYPE_DELETION_CONFLICT"));

        Integer remaining = jdbc.queryForObject(
                "select count(*) from room_type where id = ?", Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(remaining).isEqualTo(1);
    }

    @Test
    void heldInventoryBlocksDeletion() throws Exception {
        // 보류는 만료되기 전까지 실재고를 잡고 있다.
        jdbc.update("update inventory_day set held = 1 where room_type_id = ? and stay_date = ?",
                STANDARD, DAY);

        deleteRoomType(STANDARD, "delete-with-held", status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_TYPE_DELETION_CONFLICT"));

        Integer remaining = jdbc.queryForObject(
                "select count(*) from room_type where id = ?", Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(remaining).isEqualTo(1);
    }

    @Test
    void physicalRoomBlocksDeletion() throws Exception {
        jdbc.update("insert into physical_room (id, hotel_id, room_type_id, room_number) values (?, ?, ?, ?)",
                UUID.randomUUID(), SOKCHO, STANDARD, "101");

        deleteRoomType(STANDARD, "delete-with-physical-room", status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_TYPE_DELETION_CONFLICT"));

        Integer remaining = jdbc.queryForObject(
                "select count(*) from room_type where id = ?", Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(remaining).isEqualTo(1);
    }

    @Test
    void openChangeRequestBlocksDeletion() throws Exception {
        UUID reservationId = seedConfirmedReservation();
        // 진행 중인 변경 요청은 previous/target이 유형을 계속 참조해야 한다.
        jdbc.update("""
                insert into reservation_change_request
                    (id, reservation_id, hotel_id, base_operation_revision, status, settlement_direction,
                     requested_by, idempotency_key, request_hash, previous_check_in, previous_check_out,
                     previous_room_type_id, previous_rate_plan_id, target_check_in, target_check_out,
                     target_room_type_id, target_rate_plan_id, rooms, adults, children, approval_expires_at)
                values (?, ?, ?, 0, 'PENDING_APPROVAL', 'NONE', ?, 'change-key', 'change-hash',
                    ?, ?, ?, ?, ?, ?, ?, ?, 1, 2, 0, now() + interval '1 hour')
                """, UUID.randomUUID(), reservationId, SOKCHO, HQ_EMAIL_ID,
                DAY, DAY.plusDays(2), STANDARD, planOf(STANDARD),
                DAY.plusDays(3), DAY.plusDays(5), STANDARD, planOf(STANDARD));

        deleteRoomType(STANDARD, "delete-with-change-request", status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_TYPE_DELETION_CONFLICT"));

        Integer remaining = jdbc.queryForObject(
                "select count(*) from room_type where id = ?", Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(remaining).isEqualTo(1);
    }

    @Test
    void completedReservationsSurviveDeletion() throws Exception {
        // 과거 숙박 이력은 보존해야 한다. 유형을 지워도 예약이 사라지지 않는다.
        // UNUSED는 취소된 예약이 있어야 삭제 뒤 보존되는지 확인할 수 있다.
        UUID reservationId = seedCancelledPlan(UNUSED);

        deleteRoomType(UNUSED, "delete-keeps-history", status().isOk());

        Integer cancelled = jdbc.queryForObject(
                "select count(*) from reservation where id = ? and status = 'CANCELLED'",
                Integer.class, reservationId);
        org.assertj.core.api.Assertions.assertThat(cancelled).isEqualTo(1);

        // 예약이 가리키던 요금제도 살아 있어야 예약이 유효하다.
        Integer ratePlans = jdbc.queryForObject(
                "select count(*) from rate_plan where room_type_id is null", Integer.class);
        org.assertj.core.api.Assertions.assertThat(ratePlans).isEqualTo(1);
    }

    @Test
    void websitePageConnectionIsCleared() throws Exception {
        // HOTEL_LANDING 페이지는 부모 SECTION이 필요하므로 함께 만든다.
        // 경로는 유일해야 하므로 테스트마다 섞이지 않는 값을 쓴다.
        UUID sectionId = UUID.randomUUID();
        UUID pageId = UUID.randomUUID();
        String token = pageId.toString().substring(0, 8);
        String sectionPath = "/delete-" + token;
        String landingPath = sectionPath + "/deluxe";
        jdbc.update("""
                insert into website_page (id, hotel_id, parent_id, page_type, draft_slug, published_slug,
                                          draft_path, published_path, draft_menu_label, published_menu_label)
                values (?, null, null, 'SECTION', ?, ?, ?, ?, '숙박', '숙박')
                """, sectionId, "del-" + token, "del-" + token, sectionPath, sectionPath);
        jdbc.update("""
                insert into website_page (id, hotel_id, parent_id, page_type, draft_slug, published_slug,
                                          draft_path, published_path, draft_menu_label, published_menu_label)
                values (?, ?, ?, 'HOTEL_LANDING', ?, ?, ?, ?, '디럭스', '디럭스')
                """, pageId, SOKCHO, sectionId, "deluxe-" + token, "deluxe-" + token,
                landingPath, landingPath);
        jdbc.update("insert into website_page_room_type (page_id, document_state, room_type_id) values (?, 'DRAFT', ?)",
                pageId, UNUSED);

        deleteRoomType(UNUSED, "delete-clears-connections", status().isOk());

        Integer connections = jdbc.queryForObject(
                "select count(*) from website_page_room_type where room_type_id = ?", Integer.class, UNUSED);
        org.assertj.core.api.Assertions.assertThat(connections).isZero();

        // 페이지 자체는 두고 연결만 끊는다.
        Integer pages = jdbc.queryForObject(
                "select count(*) from website_page where id = ?", Integer.class, pageId);
        org.assertj.core.api.Assertions.assertThat(pages).isEqualTo(1);
    }

    @Test
    void idempotencyReplayReturnsSameResult() throws Exception {
        deleteRoomType(UNUSED, "replay", status().isOk()).andExpect(jsonPath("$.deleted").value(true));
        // 같은 키 재호출은 200 deleted=false로 같은 결과를 돌려준다.
        deleteRoomType(UNUSED, "replay", status().isOk())
                .andExpect(jsonPath("$.deleted").value(false))
                .andExpect(jsonPath("$.remainingRoomTypes").value(1));

        Integer commands = jdbc.queryForObject("""
                select count(*) from room_type_command
                 where hotel_id = ? and kind = 'DELETE' and idempotency_key = 'replay'
                """, Integer.class, SOKCHO);
        org.assertj.core.api.Assertions.assertThat(commands).isEqualTo(1);
    }

    @Test
    void newKeyWithSameContentReturnsSameResult() throws Exception {
        // 응답을 잃은 뒤 새 키로 같은 내용을 보내도 SHA-256 지문이 같아 같은 결과를 돌려준다.
        deleteRoomType(UNUSED, "first-key", status().isOk()).andExpect(jsonPath("$.deleted").value(true));
        deleteRoomType(UNUSED, "second-key", status().isOk())
                .andExpect(jsonPath("$.deleted").value(false))
                .andExpect(jsonPath("$.remainingRoomTypes").value(1));

        // 지문이 같은 재시도는 두 번째 멱원 행을 만들지 않는다.
        Integer commands = jdbc.queryForObject(
                "select count(*) from room_type_command where hotel_id = ? and kind = 'DELETE'",
                Integer.class, SOKCHO);
        org.assertj.core.api.Assertions.assertThat(commands).isEqualTo(1);

        // 유형이 두 번 지워지지 않는다.
        Integer remaining = jdbc.queryForObject(
                "select count(*) from room_type where hotel_id = ?", Integer.class, SOKCHO);
        org.assertj.core.api.Assertions.assertThat(remaining).isEqualTo(1);
    }

    @Test
    void branchStaffCannotDeleteRoomType() throws Exception {
        mvc.perform(deleteRequest(UNUSED, "branch-delete")
                .header("X-Staff-Session", sokchoToken))
                .andExpect(status().isForbidden());

        Integer remaining = jdbc.queryForObject(
                "select count(*) from room_type where id = ?", Integer.class, UNUSED);
        org.assertj.core.api.Assertions.assertThat(remaining).isEqualTo(1);
    }

    @Test
    void missingSessionIsRejected() throws Exception {
        mvc.perform(deleteRequest(UNUSED, "no-session"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        mvc.perform(delete("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}", SOKCHO, UNUSED)
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownRoomTypeIsNotFound() throws Exception {
        mvc.perform(deleteRequest(UUID.randomUUID(), "missing")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void roomTypeOfAnotherHotelIsNotFound() throws Exception {
        // STANDARD는 속초 지점의 유형이다. 제주 지점에서 지우려 하면 404다.
        mvc.perform(delete("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}", JEJU, STANDARD)
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "wrong-hotel"))
                .andExpect(status().isNotFound());

        Integer remaining = jdbc.queryForObject(
                "select count(*) from room_type where id = ?", Integer.class, STANDARD);
        org.assertj.core.api.Assertions.assertThat(remaining).isEqualTo(1);
    }

    private org.springframework.test.web.servlet.ResultActions deleteRoomType(
            UUID roomTypeId, String idempotencyKey, ResultMatcher expected) throws Exception {
        return mvc.perform(deleteRequest(roomTypeId, idempotencyKey)
                .header("X-Staff-Session", hqToken))
                .andExpect(expected);
    }

    private MockHttpServletRequestBuilder deleteRequest(UUID roomTypeId, String idempotencyKey) {
        return delete("/api/staff/hotels/{hotelId}/room-types/{roomTypeId}", SOKCHO, roomTypeId)
                .header("Idempotency-Key", idempotencyKey);
    }

    private MockHttpServletRequestBuilder availabilityQuery() {
        return get("/api/availability")
                .param("hotelId", SOKCHO.toString())
                .param("checkIn", DAY.plusDays(1).toString())
                .param("checkOut", DAY.plusDays(3).toString())
                .param("adults", "2");
    }

    private UUID seedConfirmedReservation() {
        return seedReservation(STANDARD, "CONFIRMED");
    }

    private UUID seedCancelledReservation(UUID roomTypeId) {
        return seedReservation(roomTypeId, "CANCELLED");
    }

    private UUID seedCancelledPlan(UUID roomTypeId) {
        UUID ratePlanId = UUID.randomUUID();
        jdbc.update("insert into rate_plan (id, room_type_id, name, breakfast_included, policy_version) values (?, ?, ?, false, 'FLEX-2026-01')",
                ratePlanId, roomTypeId, "과거 요금제");
        return seedReservation(roomTypeId, ratePlanId, "CANCELLED");
    }

    // 삭제가 종료된 예약을 보존하는지 확인하려면 예약이 필요하다.
    // UNUSED의 예약은 STANDARD와 겹치지 않도록 과거 날짜로 만든다.
    private UUID seedReservation(UUID roomTypeId, String status) {
        return seedReservation(roomTypeId, planOf(roomTypeId), status);
    }

    private UUID seedReservation(UUID roomTypeId, UUID ratePlanId, String status) {
        UUID reservationId = UUID.randomUUID();
        String tokenHash = sha256("management-token-for-" + reservationId);
        jdbc.update("""
                insert into reservation (id, room_type_id, rate_plan_id, check_in, check_out, rooms,
                                         adults, children, status, total_krw, currency, expires_at,
                                         guest_name, guest_email, management_token_hash, policy_snapshot)
                values (?, ?, ?, ?, ?, 1, 2, 0, ?, 200000, 'KRW', now() + interval '1 hour',
                        '테스트 고객', 'guest@example.com', ?, '{}')
                """, reservationId, roomTypeId, ratePlanId, DAY.minusDays(10),
                DAY.minusDays(8), status, tokenHash);
        return reservationId;
    }

    private UUID planOf(UUID roomTypeId) {
        return jdbc.query("select id from rate_plan where room_type_id = ?",
                rs -> rs.next() ? rs.getObject("id", UUID.class) : null, roomTypeId);
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
        // staff_session이 staff_member를 참조하므로 세션부터 지운다.
        jdbc.update("delete from staff_session");
        // website_page_room_type가 room_type을 참조하므로 페이지보다 먼저 지운다.
        jdbc.update("delete from website_page_room_type");
        jdbc.update("delete from website_page where hotel_id in (?, ?)", SOKCHO, JEJU);
        jdbc.update("delete from reservation_room_assignment");
        jdbc.update("delete from physical_room where hotel_id in (?, ?)", SOKCHO, JEJU);
        jdbc.update("delete from payment_adjustment_attempt");
        jdbc.update("delete from reservation_change_outbox");
        jdbc.update("delete from reservation_change_customer_session");
        jdbc.update("delete from reservation_change_approval");
        jdbc.update("delete from reservation_change_quote_night");
        jdbc.update("delete from reservation_change_quote");
        jdbc.update("delete from reservation_change_event");
        jdbc.update("delete from reservation_change_request where hotel_id in (?, ?)", SOKCHO, JEJU);
        jdbc.update("delete from payment_transaction");
        jdbc.update("delete from reservation_night");
        // 삭제가 유형의 참조를 끊으므로 이전 실행의 예약·요금제가
        // room_type_id IS NULL로 남을 수 있다. 지우지 않으면 다음 실행이 섞인다.
        jdbc.update("delete from reservation");
        // room_type_command가 staff_member와 room_type을 참조하므로 먼저 지운다.
        jdbc.update("delete from room_type_command where hotel_id in (?, ?)", SOKCHO, JEJU);
        jdbc.update("delete from staff_member");
        // 시드가 만든 rate_plan·inventory_day가 room_type을 참조하므로 room_type보다 먼저 지운다.
        // 삭제가 끊어 놓은 room_type_id IS NULL 요금제도 같이 지운다.
        jdbc.update("delete from rate_day");
        jdbc.update("delete from rate_plan");
        jdbc.update("delete from inventory_day");
        jdbc.update("delete from room_type where hotel_id in (?, ?)", SOKCHO, JEJU);
        jdbc.update("delete from hotel where id in (?, ?)", SOKCHO, JEJU);
    }
}
