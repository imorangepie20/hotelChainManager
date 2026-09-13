package team.hotelchain.reservationchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import team.hotelchain.staff.StaffAccessService;

@SpringBootTest
class ReservationChangeApprovalIntegrationTest {
    private static final UUID HOTEL = UUID.fromString("71000000-0000-0000-0000-000000000001");
    private static final UUID ROOM_TYPE = UUID.fromString("72000000-0000-0000-0000-000000000001");
    private static final UUID RATE_PLAN = UUID.fromString("73000000-0000-0000-0000-000000000001");
    private static final UUID RESERVATION = UUID.fromString("74000000-0000-0000-0000-000000000001");
    private static final UUID STAFF = UUID.fromString("75000000-0000-0000-0000-000000000001");
    private static final UUID HQ_STAFF = UUID.fromString("75000000-0000-0000-0000-000000000002");
    private static final UUID FIRST_REQUEST = UUID.fromString("76000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_REQUEST = UUID.fromString("76000000-0000-0000-0000-000000000002");

    @Autowired JdbcTemplate jdbc;
    @Autowired StaffAccessService staffAccess;
    @Autowired WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        clean();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void clean() {
        jdbc.update("delete from staff_session where staff_id in (?, ?)", STAFF, HQ_STAFF);
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "select to_regclass('public.reservation_change_request') is not null", Boolean.class))) {
            jdbc.update("delete from reservation_change_event where request_id in (select id from reservation_change_request where reservation_id = ?)", RESERVATION);
            jdbc.update("delete from reservation_change_approval where request_id in (select id from reservation_change_request where reservation_id = ?)", RESERVATION);
            jdbc.update("delete from reservation_change_quote_night where quote_id in (select q.id from reservation_change_quote q join reservation_change_request r on r.id = q.request_id where r.reservation_id = ?)", RESERVATION);
            jdbc.update("update reservation_change_request set current_quote_id = null where reservation_id = ?", RESERVATION);
            jdbc.update("delete from reservation_change_quote where request_id in (select id from reservation_change_request where reservation_id = ?)", RESERVATION);
            jdbc.update("delete from reservation_change_request where reservation_id = ?", RESERVATION);
        }
        jdbc.update("delete from reservation_night where reservation_id = ?", RESERVATION);
        jdbc.update("delete from reservation where id = ?", RESERVATION);
        jdbc.update("delete from staff_member where id in (?, ?)", STAFF, HQ_STAFF);
        jdbc.update("delete from rate_day where rate_plan_id = ?", RATE_PLAN);
        jdbc.update("delete from inventory_day where room_type_id = ?", ROOM_TYPE);
        jdbc.update("delete from rate_plan where id = ?", RATE_PLAN);
        jdbc.update("delete from room_type where id = ?", ROOM_TYPE);
        jdbc.update("delete from hotel where id = ?", HOTEL);
    }

    @Test
    void createsApprovalFoundationAndAllowsOnlyOneActiveRequestPerReservation() {
        List<String> tables = jdbc.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = 'public'
                  and table_name in (
                    'reservation_change_request', 'reservation_change_quote',
                    'reservation_change_quote_night', 'reservation_change_approval',
                    'reservation_change_event')
                order by table_name
                """, String.class);

        assertThat(tables).containsExactly(
                "reservation_change_approval",
                "reservation_change_event",
                "reservation_change_quote",
                "reservation_change_quote_night",
                "reservation_change_request");

        seedReservation();
        assertThat(jdbc.queryForObject(
                "select operation_revision from reservation where id = ?", Long.class, RESERVATION)).isZero();

        insertRequest(FIRST_REQUEST, "change-foundation-1");
        assertThatThrownBy(() -> insertRequest(SECOND_REQUEST, "change-foundation-2"))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("update reservation_change_request set status = 'CANCELLED' where id = ?", FIRST_REQUEST);
        insertRequest(SECOND_REQUEST, "change-foundation-2");

        insertEvent(SECOND_REQUEST, "gateway-event-1");
        assertThatThrownBy(() -> insertEvent(SECOND_REQUEST, "gateway-event-1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void appliesTheDirectLimitAndAllowsHeadquartersApproval() throws Exception {
        seedReservation();
        String branchToken = staffAccess.login("approval-foundation@example.com", "password").token();
        String hqToken = staffAccess.login("approval-hq@example.com", "password").token();
        LocalDate targetCheckOut = LocalDate.now().plusDays(23);

        mockMvc.perform(post("/api/staff/reservations/{id}/change-requests", RESERVATION)
                        .header("X-Staff-Session", branchToken)
                        .header("Idempotency-Key", "direct-limit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(targetCheckOut, 300_000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approval.limitKrw").value(100_000))
                .andExpect(jsonPath("$.actions").isArray());
        UUID directRequestId = requestId("direct-limit");

        mockMvc.perform(post("/api/staff/reservation-change-requests/{id}/cancel", directRequestId)
                        .header("X-Staff-Session", branchToken)
                        .header("Idempotency-Key", "cancel-direct-limit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        jdbc.update("update rate_day set amount_krw = 100001 where rate_plan_id = ? and stay_date = ?",
                RATE_PLAN, targetCheckOut.minusDays(1));
        mockMvc.perform(post("/api/staff/reservations/{id}/change-requests", RESERVATION)
                        .header("X-Staff-Session", branchToken)
                        .header("Idempotency-Key", "above-direct-limit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(targetCheckOut, 300_001)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.approval").doesNotExist());
        UUID approvalRequestId = requestId("above-direct-limit");

        mockMvc.perform(post("/api/staff/reservation-change-requests/{id}/approve", approvalRequestId)
                        .header("X-Staff-Session", branchToken)
                        .header("Idempotency-Key", "branch-cannot-approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/staff/reservation-change-requests/{id}/approve", approvalRequestId)
                        .header("X-Staff-Session", hqToken)
                        .header("Idempotency-Key", "hq-approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approval.decisionType").value("HQ_APPROVED"))
                .andExpect(jsonPath("$.approval.limitKrw").value(100_001));
    }

    @Test
    void replaysTheSameCreateRequestAndRejectsDifferentPayloadForTheKey() throws Exception {
        seedReservation();
        String token = staffAccess.login("approval-foundation@example.com", "password").token();
        LocalDate targetCheckOut = LocalDate.now().plusDays(23);

        String request = createRequestJson(targetCheckOut, 300_000);
        mockMvc.perform(post("/api/staff/reservations/{id}/change-requests", RESERVATION)
                        .header("X-Staff-Session", token)
                        .header("Idempotency-Key", "create-replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        UUID requestId = requestId("create-replay");

        mockMvc.perform(post("/api/staff/reservations/{id}/change-requests", RESERVATION)
                        .header("X-Staff-Session", token)
                        .header("Idempotency-Key", "create-replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId.toString()));

        mockMvc.perform(post("/api/staff/reservations/{id}/change-requests", RESERVATION)
                        .header("X-Staff-Session", token)
                        .header("Idempotency-Key", "create-replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(targetCheckOut.minusDays(1), 200_000)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

        mockMvc.perform(get("/api/staff/reservation-change-policy")
                        .header("X-Staff-Session", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directLimitKrw").value(100_000));
    }

    private void seedReservation() {
        LocalDate checkIn = LocalDate.now().plusDays(20);
        LocalDate checkOut = checkIn.plusDays(2);
        jdbc.update("insert into hotel values (?, ?, ?, ?)", HOTEL, "승인 스키마 테스트 호텔", "속초", "Asia/Seoul");
        jdbc.update("insert into room_type values (?, ?, ?, ?)", ROOM_TYPE, HOTEL, "스탠다드", 2);
        jdbc.update("insert into rate_plan values (?, ?, ?, false, ?)", RATE_PLAN, ROOM_TYPE, "룸 온리", "FLEX");
        jdbc.update("""
                insert into staff_member (id, email, display_name, password_hash, role, hotel_id)
                values (?, 'approval-foundation@example.com', '승인 테스트 직원', ?, 'BRANCH_STAFF', ?)
                """, STAFF, new BCryptPasswordEncoder().encode("password"), HOTEL);
        jdbc.update("""
                insert into staff_member (id, email, display_name, password_hash, role, hotel_id)
                values (?, 'approval-hq@example.com', '본사 승인자', ?, 'HQ_ADMIN', null)
                """, HQ_STAFF, new BCryptPasswordEncoder().encode("password"));
        for (LocalDate date = checkIn; date.isBefore(checkOut.plusDays(1)); date = date.plusDays(1)) {
            jdbc.update("insert into inventory_day values (?, ?, 2, 0, ?)",
                    ROOM_TYPE, date, date.isBefore(checkOut) ? 1 : 0);
            jdbc.update("insert into rate_day values (?, ?, 100000)", RATE_PLAN, date);
        }
        jdbc.update("""
                insert into reservation
                    (id, room_type_id, rate_plan_id, check_in, check_out, adults, children, rooms,
                     status, total_krw, currency, expires_at, guest_name, guest_email,
                     management_token_hash, policy_snapshot)
                values (?, ?, ?, ?, ?, 2, 0, 1, 'CONFIRMED', 200000, 'KRW', now(),
                        '투숙객', 'approval-guest@example.com', repeat('a', 64),
                        '{"version":"FLEX","timezone":"Asia/Seoul"}'::jsonb)
                """, RESERVATION, ROOM_TYPE, RATE_PLAN, checkIn, checkOut);
        jdbc.update("insert into reservation_night values (?, ?, 100000)", RESERVATION, checkIn);
        jdbc.update("insert into reservation_night values (?, ?, 100000)", RESERVATION, checkIn.plusDays(1));
    }

    private void insertRequest(UUID id, String idempotencyKey) {
        LocalDate checkIn = LocalDate.now().plusDays(20);
        LocalDate checkOut = checkIn.plusDays(2);
        jdbc.update("""
                insert into reservation_change_request (
                    id, reservation_id, hotel_id, base_operation_revision, status,
                    settlement_direction, requested_by, idempotency_key, request_hash,
                    previous_check_in, previous_check_out, previous_room_type_id, previous_rate_plan_id,
                    target_check_in, target_check_out, target_room_type_id, target_rate_plan_id,
                    rooms, adults, children, approval_expires_at)
                values (?, ?, ?, 0, 'PENDING_APPROVAL', 'CHARGE', ?, ?, repeat('b', 64),
                        ?, ?, ?, ?, ?, ?, ?, ?, 1, 2, 0, now() + interval '24 hours')
                """, id, RESERVATION, HOTEL, STAFF, idempotencyKey,
                checkIn, checkOut, ROOM_TYPE, RATE_PLAN,
                checkIn.plusDays(1), checkOut.plusDays(1), ROOM_TYPE, RATE_PLAN);
    }

    private void insertEvent(UUID requestId, String dedupeKey) {
        jdbc.update("""
                insert into reservation_change_event (
                    id, request_id, event_type, from_status, to_status, actor_staff_id, dedupe_key, payload)
                values (?, ?, 'GATEWAY_RESULT_RECORDED', 'AWAITING_PAYMENT', 'READY_TO_APPLY', ?, ?, '{}'::jsonb)
                """, UUID.randomUUID(), requestId, STAFF, dedupeKey);
    }

    private String createRequestJson(LocalDate targetCheckOut, long expectedTotal) {
        LocalDate targetCheckIn = LocalDate.now().plusDays(20);
        return """
                {"checkIn":"%s","checkOut":"%s","roomTypeId":"%s","ratePlanId":"%s","expectedTotal":%d}
                """.formatted(targetCheckIn, targetCheckOut, ROOM_TYPE, RATE_PLAN, expectedTotal);
    }

    private UUID requestId(String idempotencyKey) {
        return jdbc.queryForObject(
                "select id from reservation_change_request where reservation_id = ? and idempotency_key = ?",
                UUID.class, RESERVATION, idempotencyKey);
    }
}
