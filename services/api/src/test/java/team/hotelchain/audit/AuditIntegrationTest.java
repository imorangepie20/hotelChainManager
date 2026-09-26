package team.hotelchain.audit;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

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
class AuditIntegrationTest {

    private static final UUID SOKCHO = UUID.fromString("16000000-0000-0000-0000-000000000001");
    private static final UUID JEJU = UUID.fromString("16000000-0000-0000-0000-000000000002");
    private static final UUID SOKCHO_ROOM_TYPE = UUID.fromString("26000000-0000-0000-0000-000000000001");
    private static final UUID SOKCHO_RATE_PLAN = UUID.fromString("36000000-0000-0000-0000-000000000001");
    private static final UUID SOKCHO_ROOM = UUID.fromString("46000000-0000-0000-0000-000000000001");
    private static final UUID SOKCHO_ROOM_2 = UUID.fromString("46000000-0000-0000-0000-000000000002");
    private static final UUID SOKCHO_RESERVATION = UUID.fromString("56000000-0000-0000-0000-000000000001");
    private static final String HQ_EMAIL = "audit-hq@example.com";
    private static final String SOKCHO_EMAIL = "audit-sokcho@example.com";

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

        jdbc.update("insert into hotel values (?, ?, ?, ?)", SOKCHO, "audit-sokcho", "sokcho", "Asia/Seoul");
        jdbc.update("insert into hotel values (?, ?, ?, ?)", JEJU, "audit-jeju", "jeju", "Asia/Seoul");
        jdbc.update("insert into room_type values (?, ?, ?, ?)", SOKCHO_ROOM_TYPE, SOKCHO, "standard", 2);
        jdbc.update("insert into rate_plan values (?, ?, ?, true, 'FLEX-2026-01')", SOKCHO_RATE_PLAN, SOKCHO_ROOM_TYPE, "room-only");

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        UUID hqId = UUID.randomUUID();
        UUID sokchoId = UUID.randomUUID();
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, ?, ?)",
                hqId, HQ_EMAIL, "headquarters-admin", encoder.encode("hq-password"), "HQ_ADMIN", null);
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, ?, ?)",
                sokchoId, SOKCHO_EMAIL, "sokcho-staff", encoder.encode("branch-password"), "BRANCH_STAFF", SOKCHO);

        hqToken = staffAccess.login(HQ_EMAIL, "hq-password").token();
        sokchoToken = staffAccess.login(SOKCHO_EMAIL, "branch-password").token();

        seedRooms();
        seedReservation(hqId);
        seedGuestChange(sokchoId);
        seedPartyChange(sokchoId);
        seedRoomReassignment(sokchoId);
        seedStayChange(sokchoId);
        seedStaffCancellation(sokchoId);
        seedRoomOperationalEvent(sokchoId);
        seedCheckedInRoomMove(sokchoId);
    }

    @AfterEach
    void cleanAfter() {
        clean();
    }

    @Test
    void headquartersReadsAllEventTypes() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/audit")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(8))
                .andExpect(jsonPath("$.events", Matchers.hasSize(8)))
                .andExpect(jsonPath("$.events[0].staffEmail").value(HQ_EMAIL))
                .andExpect(jsonPath("$.events[0].eventType").value("CHANGE_REQUEST_EVENT"));
    }

    @Test
    void eventsAreOrderedByCreatedAtDescending() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/audit")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[1].eventType").value("CHECKED_IN_ROOM_MOVE"))
                .andExpect(jsonPath("$.events[2].eventType").value("ROOM_OPERATIONAL_TRANSITION"))
                .andExpect(jsonPath("$.events[7].eventType").value("GUEST_UPDATE"));
    }

    @Test
    void branchStaffCannotReadAudit() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/audit")
                .header("X-Staff-Session", sokchoToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingSessionIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/audit"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void filtersByReservation() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/audit")
                .header("X-Staff-Session", hqToken)
                .param("reservationId", SOKCHO_RESERVATION.toString()))
                .andExpect(status().isOk())
                // CHANGE_REQUEST_EVENT references a separate reservation and the two room-only
                // event types (operational transition, checked-in move) have no reservation.
                .andExpect(jsonPath("$.totalCount").value(6))
                .andExpect(jsonPath("$.events", Matchers.hasSize(6)));
    }

    @Test
    void filtersByHotel() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/audit")
                .header("X-Staff-Session", hqToken)
                .param("hotelId", JEJU.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.events", Matchers.hasSize(0)));
    }

    @Test
    void filtersByDateRange() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/audit")
                .header("X-Staff-Session", hqToken)
                .param("from", LocalDate.now().plusDays(1).toString())
                .param("to", LocalDate.now().plusDays(2).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0));

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/audit")
                .header("X-Staff-Session", hqToken)
                .param("from", LocalDate.now().minusDays(1).toString())
                .param("to", LocalDate.now().plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(8));
    }

    @Test
    void reversedDateRangeIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/audit")
                .header("X-Staff-Session", hqToken)
                .param("from", LocalDate.now().plusDays(2).toString())
                .param("to", LocalDate.now().toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void limitOutsideRangeIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/audit")
                .header("X-Staff-Session", hqToken)
                .param("limit", "0"))
                .andExpect(status().isBadRequest());

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/audit")
                .header("X-Staff-Session", hqToken)
                .param("limit", "201"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void negativeOffsetIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/audit")
                .header("X-Staff-Session", hqToken)
                .param("offset", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pagingSkipsEarlierEvents() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/audit")
                .header("X-Staff-Session", hqToken)
                .param("limit", "2")
                .param("offset", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(2))
                .andExpect(jsonPath("$.offset").value(2))
                .andExpect(jsonPath("$.events", Matchers.hasSize(2)))
                .andExpect(jsonPath("$.events[0].eventType").value("ROOM_OPERATIONAL_TRANSITION"));
    }

    @Test
    void unknownHotelIsNotFound() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/audit")
                .header("X-Staff-Session", hqToken)
                .param("hotelId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void summariesDoNotLeakRawPayload() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/audit")
                .header("X-Staff-Session", hqToken)
                .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].summary").exists())
                .andExpect(jsonPath("$.events[0].summary").isString());
    }

    @Test
    void maskedQueryHidesGuestNamesAndEmails() throws Exception {
        // GUEST_UPDATE 요약에 이름·이메일이 들어 있으므로 같이 가려지는지 확인한다.
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/audit")
                .header("X-Staff-Session", hqToken)
                .param("masked", "true")
                .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.masked").value(true))
                // 'audit-guest'의 마지막 한 글자만 남으므로 원문과 달라야 한다.
                .andExpect(jsonPath("$.events[?(@.eventType == 'GUEST_UPDATE')].guestName")
                        .value(Matchers.not(Matchers.hasItem("audit-guest"))))
                .andExpect(jsonPath("$.events[?(@.eventType == 'GUEST_UPDATE')].guestName")
                        .value(Matchers.hasItem(Matchers.endsWith("t"))))
                .andExpect(jsonPath("$.events[?(@.eventType == 'GUEST_UPDATE')].summary")
                        .value(Matchers.hasItem(Matchers.containsString("**"))))
                // 이메일 도메인은 식별자가 아니므로 그대로 둔다.
                .andExpect(jsonPath("$.events[?(@.eventType == 'GUEST_UPDATE')].summary")
                        .value(Matchers.hasItem(Matchers.containsString("@example.com"))));

        // masked를 생략하면 원래 값이 내려온다.
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/audit")
                .header("X-Staff-Session", hqToken)
                .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.masked").value(false))
                .andExpect(jsonPath("$.events[?(@.eventType == 'GUEST_UPDATE')].guestName")
                        .value(Matchers.hasItem("audit-guest")));
    }

    @Test
    void maskedQueryKeepsStaffAndRoomContext() throws Exception {
        // 식별에 필요한 처리 직원·객실 번호·예약 id는 가리지 않는다.
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/audit")
                .header("X-Staff-Session", hqToken)
                .param("masked", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[?(@.eventType == 'ROOM_REASSIGNMENT')].roomNumber")
                        .value(Matchers.hasItem("102")))
                .andExpect(jsonPath("$.events[?(@.staffEmail == '" + HQ_EMAIL + "')]").exists());
    }

    @Test
    void branchStaffCannotReadMaskedAudit() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/audit")
                .header("X-Staff-Session", sokchoToken)
                .param("masked", "true"))
                .andExpect(status().isForbidden());
    }

    private void seedReservation(UUID hqId) {
        jdbc.update("""
                insert into reservation
                    (id, room_type_id, rate_plan_id, check_in, check_out, adults, children, rooms,
                     status, total_krw, currency, expires_at, guest_name, guest_email, guest_phone,
                     management_token_hash, policy_snapshot)
                values (?, ?, ?, ?, ?, ?, ?, ?, 'CONFIRMED', ?, 'KRW', now() + interval '1 hour',
                        ?, ?, ?, ?, '{}'::jsonb)
                """, SOKCHO_RESERVATION, SOKCHO_ROOM_TYPE, SOKCHO_RATE_PLAN,
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(12), 2, 0, 1,
                200_000L, "audit-guest", "guest@example.com", "01012345678", "audit-token-hash");

        // CHANGE_REQUEST_EVENT references a separate reservation.
        UUID otherReservation = UUID.randomUUID();
        jdbc.update("""
                insert into reservation
                    (id, room_type_id, rate_plan_id, check_in, check_out, adults, children, rooms,
                     status, total_krw, currency, expires_at, guest_name, guest_email, guest_phone,
                     management_token_hash, policy_snapshot)
                values (?, ?, ?, ?, ?, ?, ?, ?, 'CONFIRMED', ?, 'KRW', now() + interval '1 hour',
                        ?, ?, ?, ?, '{}'::jsonb)
                """, otherReservation, SOKCHO_ROOM_TYPE, SOKCHO_RATE_PLAN,
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(12), 2, 0, 1,
                200_000L, "other-guest", "other@example.com", "01098765432", "audit-token-hash-2");

        UUID request = UUID.randomUUID();
        jdbc.update("""
                insert into reservation_change_request
                    (id, reservation_id, hotel_id, base_operation_revision, status, settlement_direction,
                     requested_by, idempotency_key, request_hash, previous_check_in, previous_check_out,
                     previous_room_type_id, previous_rate_plan_id, target_check_in, target_check_out,
                     target_room_type_id, target_rate_plan_id, rooms, adults, children, approval_expires_at, version)
                values (?, ?, ?, 0, 'APPROVED', 'NONE', ?, 'key', 'hash', ?, ?, ?, ?, ?, ?, ?, ?, 1, 2, 0, now() + interval '1 hour', 1)
                """, request, otherReservation, SOKCHO, hqId,
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(12),
                SOKCHO_ROOM_TYPE, SOKCHO_RATE_PLAN,
                LocalDate.now().plusDays(11), LocalDate.now().plusDays(13),
                SOKCHO_ROOM_TYPE, SOKCHO_RATE_PLAN);
        jdbc.update("""
                insert into reservation_change_event
                    (id, request_id, event_type, from_status, to_status, actor_staff_id, dedupe_key, payload, created_at)
                values (?, ?, 'APPROVED', 'PENDING_APPROVAL', 'APPROVED', ?, 'dedupe', '{}'::jsonb, now())
                """, UUID.randomUUID(), request, hqId);
    }

    private void seedGuestChange(UUID staffId) {
        jdbc.update("""
                insert into reservation_guest_change
                    (id, reservation_id, idempotency_key, request_hash, staff_id,
                     previous_guest_name, previous_guest_email, guest_name, guest_email, created_at)
                values (?, ?, 'guest-key', 'guest-hash', ?, 'audit-previous-guest', 'old@example.com', 'audit-guest', 'guest@example.com', now() - interval '6 minute')
                """, UUID.randomUUID(), SOKCHO_RESERVATION, staffId);
    }

    private void seedPartyChange(UUID staffId) {
        jdbc.update("""
                insert into reservation_party_change
                    (id, reservation_id, idempotency_key, request_hash, staff_id,
                     previous_adults, previous_children, adults, children, created_at)
                values (?, ?, 'party-key', 'party-hash', ?, 2, 0, 3, 1, now() - interval '5 minute')
                """, UUID.randomUUID(), SOKCHO_RESERVATION, staffId);
    }

    private void seedRoomReassignment(UUID staffId) {
        jdbc.update("""
                insert into reservation_room_assignment_change
                    (id, reservation_id, idempotency_key, request_hash, staff_id,
                     previous_physical_room_id, previous_room_number, physical_room_id, room_number, created_at)
                values (?, ?, 'room-key', 'room-hash', ?, ?, '101', ?, '102', now() - interval '4 minute')
                """, UUID.randomUUID(), SOKCHO_RESERVATION, staffId, SOKCHO_ROOM, SOKCHO_ROOM_2);
    }

    private void seedStayChange(UUID staffId) {
        jdbc.update("""
                insert into reservation_stay_change
                    (id, reservation_id, idempotency_key, request_hash, staff_id,
                     previous_room_type_id, room_type_id, previous_rate_plan_id, rate_plan_id,
                     previous_check_in, previous_check_out, check_in, check_out,
                     previous_total_krw, total_krw, difference_krw, created_at)
                values (?, ?, 'stay-key', 'stay-hash', ?, ?, ?, ?, ?, ?, ?, ?, ?, 200000, 300000, 100000, now() - interval '3 minute')
                """, UUID.randomUUID(), SOKCHO_RESERVATION, staffId,
                SOKCHO_ROOM_TYPE, SOKCHO_ROOM_TYPE, SOKCHO_RATE_PLAN, SOKCHO_RATE_PLAN,
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(12),
                LocalDate.now().plusDays(11), LocalDate.now().plusDays(13));
    }

    private void seedStaffCancellation(UUID staffId) {
        // Staff cancellations are exposed; customer ones are excluded from audit.
        jdbc.update("""
                insert into cancellation_attempt
                    (id, reservation_id, idempotency_key, request_hash, refund_amount_krw,
                     refund_status, reservation_status, actor_type, staff_id, created_at)
                values (?, ?, 'cancel-key', 'cancel-hash', 100000, 'SUCCEEDED', 'CANCELLED', 'STAFF', ?, now() - interval '2 minute')
                """, UUID.randomUUID(), SOKCHO_RESERVATION, staffId);
        jdbc.update("""
                insert into cancellation_attempt
                    (id, reservation_id, idempotency_key, request_hash, refund_amount_krw,
                     refund_status, reservation_status, actor_type, created_at)
                values (?, ?, 'customer-cancel-key', 'customer-cancel-hash', 0, 'SUCCEEDED', 'CANCELLED', 'CUSTOMER', now() - interval '90 second')
                """, UUID.randomUUID(), SOKCHO_RESERVATION);
    }

    private void seedRooms() {
        jdbc.update("""
                insert into physical_room
                    (id, hotel_id, room_type_id, room_number, housekeeping_status, operational_status, operational_version)
                values (?, ?, ?, '101', 'CLEAN', 'AVAILABLE', 0)
                on conflict (id) do nothing
                """, SOKCHO_ROOM, SOKCHO, SOKCHO_ROOM_TYPE);
        jdbc.update("""
                insert into physical_room
                    (id, hotel_id, room_type_id, room_number, housekeeping_status, operational_status, operational_version)
                values (?, ?, ?, '102', 'CLEAN', 'AVAILABLE', 0)
                on conflict (id) do nothing
                """, SOKCHO_ROOM_2, SOKCHO, SOKCHO_ROOM_TYPE);
    }

    private void seedRoomOperationalEvent(UUID staffId) {
        jdbc.update("""
                insert into physical_room_operational_event
                    (id, physical_room_id, previous_status, status, previous_reason, reason,
                     previous_expected_recovery_at, expected_recovery_at, staff_id, idempotency_key, request_hash, created_at)
                values (?, ?, 'AVAILABLE', 'INSPECTION_REQUIRED', null, 'inspection-needed', null, null, ?, 'ops-key', 'ops-hash', now() - interval '70 second')
                """, UUID.randomUUID(), SOKCHO_ROOM, staffId);
    }

    private void seedCheckedInRoomMove(UUID staffId) {
        jdbc.update("""
                insert into checked_in_room_move
                    (id, reservation_id, previous_physical_room_id, previous_room_number,
                     physical_room_id, room_number, reason, staff_id, idempotency_key, request_hash, created_at)
                values (?, ?, ?, '101', ?, '102', 'guest-request', ?, 'move-key', 'move-hash', now() - interval '60 second')
                """, UUID.randomUUID(), SOKCHO_RESERVATION, SOKCHO_ROOM, SOKCHO_ROOM_2, staffId);
    }

    private void clean() {
        jdbc.update("delete from staff_session");
        jdbc.update("delete from checked_in_room_move");
        jdbc.update("delete from physical_room_operational_event");
        jdbc.update("delete from reservation_room_assignment_change");
        jdbc.update("delete from cancellation_attempt");
        jdbc.update("delete from reservation_change_event");
        jdbc.update("delete from reservation_change_request");
        jdbc.update("delete from reservation_stay_change");
        jdbc.update("delete from reservation_party_change");
        jdbc.update("delete from reservation_guest_change");
        jdbc.update("delete from reservation_night");
        jdbc.update("delete from reservation");
        jdbc.update("delete from physical_room");
        jdbc.update("delete from rate_plan");
        jdbc.update("delete from room_type");
        jdbc.update("delete from staff_member");
        jdbc.update("delete from hotel where id in (?, ?)", SOKCHO, JEJU);
    }
}
