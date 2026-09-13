package team.hotelchain.reservationchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import team.hotelchain.operations.StaffOperationsService;
import team.hotelchain.reservation.BusinessConflictException;
import team.hotelchain.reservation.StaffReservationPartyUpdateRequest;
import team.hotelchain.reservation.StaffReservationPartyUpdateService;
import team.hotelchain.staff.StaffAccessService;

@SpringBootTest
class ReservationChangeMutationGuardIntegrationTest {
    private static final UUID HOTEL = UUID.fromString("91000000-0000-0000-0000-000000000001");
    private static final UUID ROOM_TYPE = UUID.fromString("92000000-0000-0000-0000-000000000001");
    private static final UUID RATE_PLAN = UUID.fromString("93000000-0000-0000-0000-000000000001");
    private static final UUID RESERVATION = UUID.fromString("94000000-0000-0000-0000-000000000001");
    private static final UUID STAFF = UUID.fromString("95000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST = UUID.fromString("96000000-0000-0000-0000-000000000001");
    private static final UUID ROOM = UUID.fromString("97000000-0000-0000-0000-000000000001");

    @Autowired JdbcTemplate jdbc;
    @Autowired StaffAccessService staffAccess;
    @Autowired StaffReservationPartyUpdateService partyUpdates;
    @Autowired StaffOperationsService operations;

    private String token;

    @BeforeEach
    void seed() {
        clean();
        LocalDate checkIn = LocalDate.now().plusDays(20);
        jdbc.update("insert into hotel values (?, ?, ?, ?)", HOTEL, "mutation guard 테스트 호텔", "속초", "Asia/Seoul");
        jdbc.update("insert into room_type values (?, ?, ?, ?)", ROOM_TYPE, HOTEL, "스탠다드", 4);
        jdbc.update("insert into rate_plan values (?, ?, ?, false, ?)", RATE_PLAN, ROOM_TYPE, "룸 온리", "FLEX");
        jdbc.update("insert into physical_room values (?, ?, ?, '901', 'CLEAN')", ROOM, HOTEL, ROOM_TYPE);
        jdbc.update("""
                insert into staff_member (id, email, display_name, password_hash, role, hotel_id)
                values (?, 'mutation-guard@example.com', '변경 보호 직원', ?, 'BRANCH_STAFF', ?)
                """, STAFF, new BCryptPasswordEncoder().encode("password"), HOTEL);
        jdbc.update("""
                insert into reservation
                    (id, room_type_id, rate_plan_id, check_in, check_out, adults, children, rooms,
                     status, total_krw, currency, expires_at, guest_name, guest_email,
                     management_token_hash, policy_snapshot)
                values (?, ?, ?, ?, ?, 2, 0, 1, 'CONFIRMED', 100000, 'KRW', now(),
                        '투숙객', 'guard-guest@example.com', repeat('d', 64), '{}'::jsonb)
                """, RESERVATION, ROOM_TYPE, RATE_PLAN, checkIn, checkIn.plusDays(1));
        jdbc.update("insert into reservation_room_assignment values (?, ?)", RESERVATION, ROOM);
        token = staffAccess.login("mutation-guard@example.com", "password").token();
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void expiresApprovalOnlyRequestAndIncrementsRevisionAfterPartyUpdate() {
        insertChangeRequest("PENDING_APPROVAL");

        partyUpdates.update(token, RESERVATION, "guard-party", new StaffReservationPartyUpdateRequest(3, 0));

        assertThat(jdbc.queryForObject(
                "select operation_revision from reservation where id = ?", Long.class, RESERVATION)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select status from reservation_change_request where id = ?", String.class, REQUEST)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("""
                select payload ->> 'reason' from reservation_change_event
                where request_id = ? and event_type = 'REQUEST_EXPIRED'
                """, String.class, REQUEST)).isEqualTo("RESERVATION_CHANGED");
    }

    @Test
    void blocksCheckInForEveryActiveChangeRequest() {
        insertChangeRequest("PENDING_APPROVAL");

        assertThatThrownBy(() -> operations.checkIn(token, RESERVATION))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(error -> ((BusinessConflictException) error).code())
                .isEqualTo("RESERVATION_CHANGE_SETTLEMENT_ACTIVE");
        assertThat(jdbc.queryForObject(
                "select status from reservation where id = ?", String.class, RESERVATION)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject(
                "select status from reservation_change_request where id = ?", String.class, REQUEST))
                .isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void blocksPartyUpdateWhileSettlementIsActive() {
        insertChangeRequest("AWAITING_PAYMENT");

        assertThatThrownBy(() -> partyUpdates.update(
                token, RESERVATION, "guard-active-party", new StaffReservationPartyUpdateRequest(3, 0)))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(error -> ((BusinessConflictException) error).code())
                .isEqualTo("RESERVATION_CHANGE_SETTLEMENT_ACTIVE");
        assertThat(jdbc.queryForObject(
                "select operation_revision from reservation where id = ?", Long.class, RESERVATION)).isZero();
    }

    private void insertChangeRequest(String status) {
        LocalDate checkIn = LocalDate.now().plusDays(20);
        jdbc.update("""
                insert into reservation_change_request (
                    id, reservation_id, hotel_id, base_operation_revision, status,
                    settlement_direction, requested_by, idempotency_key, request_hash,
                    previous_check_in, previous_check_out, previous_room_type_id, previous_rate_plan_id,
                    target_check_in, target_check_out, target_room_type_id, target_rate_plan_id,
                    rooms, adults, children, approval_expires_at)
                values (?, ?, ?, 0, ?, 'CHARGE', ?, 'mutation-guard', repeat('e', 64),
                        ?, ?, ?, ?, ?, ?, ?, ?, 1, 2, 0, now() + interval '24 hours')
                """, REQUEST, RESERVATION, HOTEL, status, STAFF,
                checkIn, checkIn.plusDays(1), ROOM_TYPE, RATE_PLAN,
                checkIn.plusDays(1), checkIn.plusDays(2), ROOM_TYPE, RATE_PLAN);
    }

    private void clean() {
        jdbc.update("delete from staff_session where staff_id = ?", STAFF);
        jdbc.update("delete from reservation_change_event where request_id = ?", REQUEST);
        jdbc.update("delete from reservation_change_request where id = ?", REQUEST);
        jdbc.update("delete from reservation_party_change where reservation_id = ?", RESERVATION);
        jdbc.update("delete from reservation_room_assignment where reservation_id = ?", RESERVATION);
        jdbc.update("delete from reservation where id = ?", RESERVATION);
        jdbc.update("delete from physical_room where id = ?", ROOM);
        jdbc.update("delete from staff_member where id = ?", STAFF);
        jdbc.update("delete from rate_plan where id = ?", RATE_PLAN);
        jdbc.update("delete from room_type where id = ?", ROOM_TYPE);
        jdbc.update("delete from hotel where id = ?", HOTEL);
    }
}
