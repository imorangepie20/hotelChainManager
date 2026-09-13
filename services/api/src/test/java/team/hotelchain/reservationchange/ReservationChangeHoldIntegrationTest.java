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

import team.hotelchain.reservation.BusinessConflictException;
import team.hotelchain.staff.StaffAccessService;

@SpringBootTest
class ReservationChangeHoldIntegrationTest {
    private static final UUID HOTEL = UUID.fromString("91000000-0000-0000-0000-000000000001");
    private static final UUID ROOM_TYPE = UUID.fromString("92000000-0000-0000-0000-000000000001");
    private static final UUID RATE_PLAN = UUID.fromString("93000000-0000-0000-0000-000000000001");
    private static final UUID RESERVATION = UUID.fromString("94000000-0000-0000-0000-000000000001");
    private static final UUID STAFF = UUID.fromString("95000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST = UUID.fromString("96000000-0000-0000-0000-000000000001");
    private static final UUID QUOTE = UUID.fromString("97000000-0000-0000-0000-000000000001");

    @Autowired JdbcTemplate jdbc;
    @Autowired ReservationChangeHoldService holds;
    @Autowired ReservationChangeExpiryJob expiryJob;
    @Autowired ReservationChangeRequestService requests;
    @Autowired StaffAccessService staffAccess;

    private LocalDate oldCheckIn;

    @BeforeEach
    void setUp() {
        clean();
        oldCheckIn = LocalDate.now().plusDays(30);
        jdbc.update("insert into hotel values (?, ?, ?, ?)", HOTEL, "재고 확보 테스트 호텔", "속초", "Asia/Seoul");
        jdbc.update("insert into room_type values (?, ?, ?, ?)", ROOM_TYPE, HOTEL, "스탠다드", 2);
        jdbc.update("insert into rate_plan values (?, ?, ?, false, ?)", RATE_PLAN, ROOM_TYPE, "룸 온리", "FLEX");
        jdbc.update("""
                insert into staff_member (id, email, display_name, password_hash, role, hotel_id)
                values (?, 'hold-test@example.com', '재고 테스트 직원', ?, 'BRANCH_STAFF', ?)
                """, STAFF, new BCryptPasswordEncoder().encode("password"), HOTEL);
        for (int offset = 0; offset < 5; offset++) {
            LocalDate date = oldCheckIn.plusDays(offset);
            int confirmed = offset < 2 ? 1 : 0;
            jdbc.update("insert into inventory_day values (?, ?, 2, 0, ?)", ROOM_TYPE, date, confirmed);
            jdbc.update("insert into rate_day values (?, ?, 100000)", RATE_PLAN, date);
        }
        jdbc.update("""
                insert into reservation (
                    id, room_type_id, rate_plan_id, check_in, check_out, adults, children, rooms,
                    status, total_krw, currency, expires_at, guest_name, guest_email,
                    management_token_hash, policy_snapshot)
                values (?, ?, ?, ?, ?, 2, 0, 1, 'CONFIRMED', 200000, 'KRW', now(),
                        '재고 고객', 'hold-guest@example.com', repeat('a', 64), '{}'::jsonb)
                """, RESERVATION, ROOM_TYPE, RATE_PLAN, oldCheckIn, oldCheckIn.plusDays(2));
        jdbc.update("insert into reservation_night values (?, ?, 100000)", RESERVATION, oldCheckIn);
        jdbc.update("insert into reservation_night values (?, ?, 100000)", RESERVATION, oldCheckIn.plusDays(1));
    }

    @AfterEach
    void cleanAfter() {
        clean();
    }

    @Test
    void holdsOnlyNetNewDatesForSameRoomType() {
        insertApprovedRequest(oldCheckIn.plusDays(1), oldCheckIn.plusDays(4), "CHARGE");

        assertThat(held(oldCheckIn.plusDays(2))).isZero();
        ReservationChangeHoldService.HoldResult result = holds.acquire(REQUEST, 0);

        assertThat(result.newHeldNights()).isEqualTo(2);
        assertThat(holdKind(oldCheckIn.plusDays(1))).isEqualTo("EXISTING_CONFIRMED");
        assertThat(held(oldCheckIn.plusDays(1))).isZero();
        assertThat(holdKind(oldCheckIn.plusDays(2))).isEqualTo("NEW_HOLD");
        assertThat(held(oldCheckIn.plusDays(2))).isEqualTo(1);
        assertThat(held(oldCheckIn.plusDays(3))).isEqualTo(1);
    }

    @Test
    void soldOutDateRollsBackEveryHoldAndReleaseIsIdempotent() {
        insertApprovedRequest(oldCheckIn.plusDays(1), oldCheckIn.plusDays(4), "CHARGE");
        jdbc.update("update inventory_day set confirmed = capacity where room_type_id = ? and stay_date = ?",
                ROOM_TYPE, oldCheckIn.plusDays(3));

        assertThatThrownBy(() -> holds.acquire(REQUEST, 0))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("재고");
        assertThat(jdbc.queryForObject(
                "select count(*) from reservation_change_hold_day where request_id = ?", Integer.class, REQUEST)).isZero();
        assertThat(held(oldCheckIn.plusDays(2))).isZero();

        jdbc.update("update inventory_day set confirmed = 0 where room_type_id = ? and stay_date = ?",
                ROOM_TYPE, oldCheckIn.plusDays(3));
        holds.acquire(REQUEST, 0);
        holds.release(REQUEST, "TEST_RELEASE");
        holds.release(REQUEST, "TEST_RELEASE_AGAIN");
        assertThat(held(oldCheckIn.plusDays(2))).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from reservation_change_hold_day
                where request_id = ? and status = 'RELEASED'
                """, Integer.class, REQUEST)).isEqualTo(3);
    }

    @Test
    void expiryEndsApprovalOnlyRequestsButPreservesUnknownFinancialResults() {
        insertApprovedRequest(oldCheckIn.plusDays(1), oldCheckIn.plusDays(4), "CHARGE");
        jdbc.update("update reservation_change_request set approval_expires_at = now() - interval '1 second' where id = ?", REQUEST);

        assertThat(expiryJob.expireDue(10)).isEqualTo(1);
        assertThat(status()).isEqualTo("EXPIRED");

        deleteRequest();
        insertApprovedRequest(oldCheckIn.plusDays(1), oldCheckIn.plusDays(4), "CHARGE");
        holds.acquire(REQUEST, 0);
        jdbc.update("""
                insert into payment_adjustment_attempt (
                    id, request_id, adjustment_type, provider, idempotency_key, request_hash,
                    amount_krw, currency, status)
                values (?, ?, 'CREATE_CHECKOUT', 'FAKE', 'unknown-attempt', repeat('d', 64), 100000, 'KRW', 'UNKNOWN')
                """, UUID.randomUUID(), REQUEST);
        jdbc.update("""
                update reservation_change_request
                set status = 'AWAITING_PAYMENT', settlement_expires_at = now() - interval '1 second'
                where id = ?
                """, REQUEST);

        assertThat(expiryJob.expireDue(10)).isEqualTo(1);
        assertThat(status()).isEqualTo("RECONCILIATION_REQUIRED");
        assertThat(held(oldCheckIn.plusDays(2))).isEqualTo(1);
    }

    @Test
    void approvedZeroDifferenceRepriceAcquiresHoldAndBecomesReadyToApply() {
        insertApprovedRequest(oldCheckIn.plusDays(1), oldCheckIn.plusDays(3), "NONE");
        jdbc.update("update reservation_change_quote set total_krw = 200000, difference_krw = 0 where id = ?", QUOTE);
        String token = staffAccess.login("hold-test@example.com", "password").token();

        ReservationChangeRequestView view = requests.reprice(
                token, REQUEST, "zero-difference-reprice", new ReservationChangeVersionRequest(0));

        assertThat(view.status()).isEqualTo("READY_TO_APPLY");
        assertThat(holdKind(oldCheckIn.plusDays(1))).isEqualTo("EXISTING_CONFIRMED");
        assertThat(holdKind(oldCheckIn.plusDays(2))).isEqualTo("NEW_HOLD");
        assertThat(held(oldCheckIn.plusDays(2))).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from payment_adjustment_attempt where request_id = ?", Integer.class, REQUEST)).isZero();
    }

    private void insertApprovedRequest(LocalDate targetCheckIn, LocalDate targetCheckOut, String direction) {
        jdbc.update("""
                insert into reservation_change_request (
                    id, reservation_id, hotel_id, base_operation_revision, status,
                    settlement_direction, requested_by, idempotency_key, request_hash,
                    previous_check_in, previous_check_out, previous_room_type_id, previous_rate_plan_id,
                    target_check_in, target_check_out, target_room_type_id, target_rate_plan_id,
                    rooms, adults, children, approval_limit_krw, approval_expires_at)
                values (?, ?, ?, 0, 'APPROVED', ?, ?, 'hold-request', repeat('b', 64),
                        ?, ?, ?, ?, ?, ?, ?, ?, 1, 2, 0, 300000, now() + interval '24 hours')
                """, REQUEST, RESERVATION, HOTEL, direction, STAFF,
                oldCheckIn, oldCheckIn.plusDays(2), ROOM_TYPE, RATE_PLAN,
                targetCheckIn, targetCheckOut, ROOM_TYPE, RATE_PLAN);
        jdbc.update("""
                insert into reservation_change_quote (
                    id, request_id, revision, previous_total_krw, total_krw,
                    difference_krw, currency, rooms)
                values (?, ?, 1, 200000, 300000, 100000, 'KRW', 1)
                """, QUOTE, REQUEST);
        for (LocalDate date = targetCheckIn; date.isBefore(targetCheckOut); date = date.plusDays(1)) {
            jdbc.update("insert into reservation_change_quote_night values (?, ?, 100000)", QUOTE, date);
        }
        jdbc.update("update reservation_change_request set current_quote_id = ? where id = ?", QUOTE, REQUEST);
    }

    private int held(LocalDate date) {
        return jdbc.queryForObject("select held from inventory_day where room_type_id = ? and stay_date = ?",
                Integer.class, ROOM_TYPE, date);
    }

    private String holdKind(LocalDate date) {
        return jdbc.queryForObject("""
                select hold_kind from reservation_change_hold_day where request_id = ? and stay_date = ?
                """, String.class, REQUEST, date);
    }

    private String status() {
        return jdbc.queryForObject("select status from reservation_change_request where id = ?", String.class, REQUEST);
    }

    private void deleteRequest() {
        jdbc.update("delete from reservation_change_customer_session where request_id = ?", REQUEST);
        jdbc.update("delete from reservation_change_outbox where request_id = ?", REQUEST);
        jdbc.update("delete from payment_adjustment_attempt where request_id = ?", REQUEST);
        jdbc.update("delete from reservation_change_hold_day where request_id = ?", REQUEST);
        jdbc.update("delete from reservation_change_event where request_id = ?", REQUEST);
        jdbc.update("delete from reservation_change_approval where request_id = ?", REQUEST);
        jdbc.update("delete from reservation_change_quote_night where quote_id = ?", QUOTE);
        jdbc.update("update reservation_change_request set current_quote_id = null where id = ?", REQUEST);
        jdbc.update("delete from reservation_change_quote where request_id = ?", REQUEST);
        jdbc.update("delete from reservation_change_request where id = ?", REQUEST);
    }

    private void clean() {
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "select to_regclass('public.reservation_change_hold_day') is not null", Boolean.class))) {
            deleteRequest();
        }
        jdbc.update("delete from reservation_night where reservation_id = ?", RESERVATION);
        jdbc.update("delete from reservation where id = ?", RESERVATION);
        jdbc.update("delete from staff_session where staff_id = ?", STAFF);
        jdbc.update("delete from staff_member where id = ?", STAFF);
        jdbc.update("delete from inventory_day where room_type_id = ?", ROOM_TYPE);
        jdbc.update("delete from rate_day where rate_plan_id = ?", RATE_PLAN);
        jdbc.update("delete from rate_plan where id = ?", RATE_PLAN);
        jdbc.update("delete from room_type where id = ?", ROOM_TYPE);
        jdbc.update("delete from hotel where id = ?", HOTEL);
    }
}
