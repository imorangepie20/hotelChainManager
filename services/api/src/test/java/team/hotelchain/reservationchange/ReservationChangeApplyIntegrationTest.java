package team.hotelchain.reservationchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import team.hotelchain.payment.PaymentOutcome;
import team.hotelchain.payment.TestPaymentService;
import team.hotelchain.reservation.ReservationGuest;
import team.hotelchain.reservation.BusinessConflictException;
import team.hotelchain.reservation.ReservationRequest;
import team.hotelchain.reservation.ReservationService;
import team.hotelchain.reservation.StaffReservationStayChangeRequest;
import team.hotelchain.reservation.StaffReservationStayChangeService;
import team.hotelchain.reservation.ReservationView;
import team.hotelchain.staff.StaffAccessService;

@SpringBootTest(properties = {
        "reservation.change.settlement-enabled=true",
        "reservation.change.gateway=fake"
})
class ReservationChangeApplyIntegrationTest {
    private static final UUID HOTEL = UUID.fromString("91000000-0000-0000-0000-000000000001");
    private static final UUID ROOM_TYPE = UUID.fromString("92000000-0000-0000-0000-000000000001");
    private static final UUID RATE_PLAN = UUID.fromString("93000000-0000-0000-0000-000000000001");
    private static final UUID STAFF = UUID.fromString("95000000-0000-0000-0000-000000000001");
    private static final String TOKEN = token(10);

    @Autowired JdbcTemplate jdbc;
    @Autowired ReservationService reservations;
    @Autowired TestPaymentService payments;
    @Autowired StaffAccessService staffAccess;
    @Autowired ReservationChangeRequestService requests;
    @Autowired ReservationChangeSettlementService settlements;
    @Autowired ReservationChangeOutboxWorker outbox;
    @Autowired ReservationChangeApplyJob applyJob;
    @Autowired StaffReservationStayChangeService directStayChanges;

    private LocalDate oldCheckIn;

    @BeforeEach
    void setUp() {
        clean();
        oldCheckIn = LocalDate.now().plusDays(50);
        jdbc.update("insert into hotel values (?, ?, ?, ?)", HOTEL, "적용 테스트 호텔", "서울", "Asia/Seoul");
        jdbc.update("insert into room_type values (?, ?, ?, ?)", ROOM_TYPE, HOTEL, "적용 테스트 객실", 2);
        jdbc.update("insert into rate_plan values (?, ?, ?, false, ?)", RATE_PLAN, ROOM_TYPE, "적용 테스트 요금", "FLEX");
        jdbc.update("""
                insert into staff_member (id, email, display_name, password_hash, role, hotel_id)
                values (?, 'apply-hq@example.com', '적용 본사 관리자', ?, 'HQ_ADMIN', null)
                """, STAFF, new BCryptPasswordEncoder().encode("password"));
        for (int day = 0; day < 4; day++) {
            LocalDate date = oldCheckIn.plusDays(day);
            jdbc.update("insert into rate_day values (?, ?, ?)", RATE_PLAN, date, day == 2 ? 150_000 : 100_000);
            jdbc.update("insert into inventory_day values (?, ?, 2, 0, 0)", ROOM_TYPE, date);
        }
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void appliesSettledOverlapExactlyOnceWithAuditAndNightlyPrices() {
        ReservationView reservation = reservations.create("apply-reservation", TOKEN, new ReservationRequest(
                ROOM_TYPE, RATE_PLAN, oldCheckIn, oldCheckIn.plusDays(2), 2, 0, 1, 200_000,
                new ReservationGuest("적용 고객", "apply@example.com")));
        payments.pay(reservation.id(), TOKEN, "apply-original", PaymentOutcome.SUCCESS);
        String staffToken = staffAccess.login("apply-hq@example.com", "password").token();
        ReservationChangeRequestView request = requests.create(
                staffToken, reservation.id(), "apply-change",
                new CreateReservationChangeRequest(
                        oldCheckIn.plusDays(1), oldCheckIn.plusDays(3), ROOM_TYPE, RATE_PLAN, 250_000L));
        settlements.createPaymentLink(staffToken, request.id(), "apply-link",
                new ReservationChangePaymentLinkRequest(request.version(), token(11)));
        assertThat(outbox.processNext()).isTrue();
        UUID attemptId = jdbc.queryForObject(
                "select id from payment_adjustment_attempt where request_id = ?", UUID.class, request.id());
        settlements.recordGatewayResult(
                attemptId, "apply-success", PaymentAdjustmentGateway.GatewayResultStatus.SUCCEEDED);

        assertThat(applyJob.claimAndApplyNext()).isTrue();
        assertThat(applyJob.claimAndApplyNext()).isFalse();

        assertThat(jdbc.queryForMap("""
                select room_type_id, rate_plan_id, check_in, check_out, total_krw,
                       operation_revision from reservation where id = ?
                """, reservation.id()))
                .containsEntry("room_type_id", ROOM_TYPE)
                .containsEntry("rate_plan_id", RATE_PLAN)
                .containsEntry("check_in", java.sql.Date.valueOf(oldCheckIn.plusDays(1)))
                .containsEntry("check_out", java.sql.Date.valueOf(oldCheckIn.plusDays(3)))
                .containsEntry("total_krw", 250_000L)
                .containsEntry("operation_revision", 1L);
        assertThat(jdbc.queryForList("""
                select stay_date, amount_krw from reservation_night
                where reservation_id = ? order by stay_date
                """, reservation.id())).containsExactly(
                        Map.of("stay_date", java.sql.Date.valueOf(oldCheckIn.plusDays(1)), "amount_krw", 100_000),
                        Map.of("stay_date", java.sql.Date.valueOf(oldCheckIn.plusDays(2)), "amount_krw", 150_000));
        assertThat(confirmed(oldCheckIn)).isZero();
        assertThat(confirmed(oldCheckIn.plusDays(1))).isEqualTo(1);
        assertThat(confirmed(oldCheckIn.plusDays(2))).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select held from inventory_day where room_type_id = ? and stay_date = ?",
                Integer.class, ROOM_TYPE, oldCheckIn.plusDays(2))).isZero();
        assertThat(jdbc.queryForObject(
                "select status from reservation_change_request where id = ?", String.class, request.id()))
                .isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject(
                "select count(*) from reservation_stay_change where change_request_id = ?",
                Integer.class, request.id())).isEqualTo(1);
    }

    @Test
    void settlementModeBlocksTheLegacyDirectStayMutation() {
        ReservationView reservation = reservations.create("blocked-direct-reservation", TOKEN, new ReservationRequest(
                ROOM_TYPE, RATE_PLAN, oldCheckIn, oldCheckIn.plusDays(2), 2, 0, 1, 200_000,
                new ReservationGuest("직접 변경 차단 고객", "blocked-direct@example.com")));
        payments.pay(reservation.id(), TOKEN, "blocked-direct-original", PaymentOutcome.SUCCESS);
        String staffToken = staffAccess.login("apply-hq@example.com", "password").token();

        assertThatThrownBy(() -> directStayChanges.update(
                staffToken, reservation.id(), "blocked-direct",
                new StaffReservationStayChangeRequest(
                        oldCheckIn.plusDays(1), oldCheckIn.plusDays(3), ROOM_TYPE, RATE_PLAN, 250_000L)))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(exception -> ((BusinessConflictException) exception).code())
                .isEqualTo("CHANGE_SETTLEMENT_REQUIRED");
    }

    private int confirmed(LocalDate date) {
        return jdbc.queryForObject(
                "select confirmed from inventory_day where room_type_id = ? and stay_date = ?",
                Integer.class, ROOM_TYPE, date);
    }

    private void clean() {
        jdbc.update("delete from reservation_stay_change");
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "select to_regclass('public.payment_transaction') is not null", Boolean.class))) {
            jdbc.update("delete from reservation_change_customer_session");
            jdbc.update("delete from reservation_change_outbox");
            jdbc.update("delete from payment_adjustment_attempt");
            jdbc.update("delete from reservation_change_hold_day");
            jdbc.update("delete from payment_transaction");
            jdbc.update("delete from reservation_change_event");
            jdbc.update("delete from reservation_change_approval");
            jdbc.update("delete from reservation_change_quote_night");
            jdbc.update("update reservation_change_request set current_quote_id = null");
            jdbc.update("delete from reservation_change_quote");
            jdbc.update("delete from reservation_change_request");
        }
        jdbc.update("delete from payment_attempt");
        jdbc.update("delete from reservation_idempotency");
        jdbc.update("delete from reservation_night");
        jdbc.update("delete from reservation where room_type_id = ?", ROOM_TYPE);
        jdbc.update("delete from staff_session where staff_id = ?", STAFF);
        jdbc.update("delete from staff_member where id = ?", STAFF);
        jdbc.update("delete from inventory_day where room_type_id = ?", ROOM_TYPE);
        jdbc.update("delete from rate_day where rate_plan_id = ?", RATE_PLAN);
        jdbc.update("delete from rate_plan where id = ?", RATE_PLAN);
        jdbc.update("delete from room_type where id = ?", ROOM_TYPE);
        jdbc.update("delete from hotel where id = ?", HOTEL);
    }

    private static String token(int fill) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, (byte) fill);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
