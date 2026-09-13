package team.hotelchain.reservationchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootTest
class ReservationChangeApprovalIntegrationTest {
    private static final UUID HOTEL = UUID.fromString("71000000-0000-0000-0000-000000000001");
    private static final UUID ROOM_TYPE = UUID.fromString("72000000-0000-0000-0000-000000000001");
    private static final UUID RATE_PLAN = UUID.fromString("73000000-0000-0000-0000-000000000001");
    private static final UUID RESERVATION = UUID.fromString("74000000-0000-0000-0000-000000000001");
    private static final UUID STAFF = UUID.fromString("75000000-0000-0000-0000-000000000001");
    private static final UUID FIRST_REQUEST = UUID.fromString("76000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_REQUEST = UUID.fromString("76000000-0000-0000-0000-000000000002");

    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clean() {
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "select to_regclass('public.reservation_change_request') is not null", Boolean.class))) {
            jdbc.update("delete from reservation_change_event where request_id in (?, ?)", FIRST_REQUEST, SECOND_REQUEST);
            jdbc.update("delete from reservation_change_approval where request_id in (?, ?)", FIRST_REQUEST, SECOND_REQUEST);
            jdbc.update("delete from reservation_change_quote_night where quote_id in (select id from reservation_change_quote where request_id in (?, ?))",
                    FIRST_REQUEST, SECOND_REQUEST);
            jdbc.update("delete from reservation_change_quote where request_id in (?, ?)", FIRST_REQUEST, SECOND_REQUEST);
            jdbc.update("delete from reservation_change_request where id in (?, ?)", FIRST_REQUEST, SECOND_REQUEST);
        }
        jdbc.update("delete from reservation_night where reservation_id = ?", RESERVATION);
        jdbc.update("delete from reservation where id = ?", RESERVATION);
        jdbc.update("delete from staff_member where id = ?", STAFF);
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
                insert into reservation
                    (id, room_type_id, rate_plan_id, check_in, check_out, adults, children, rooms,
                     status, total_krw, currency, expires_at, guest_name, guest_email,
                     management_token_hash, policy_snapshot)
                values (?, ?, ?, ?, ?, 2, 0, 1, 'CONFIRMED', 200000, 'KRW', now(),
                        '투숙객', 'approval-guest@example.com', repeat('a', 64),
                        '{"version":"FLEX","timezone":"Asia/Seoul"}'::jsonb)
                """, RESERVATION, ROOM_TYPE, RATE_PLAN, checkIn, checkOut);
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
}
