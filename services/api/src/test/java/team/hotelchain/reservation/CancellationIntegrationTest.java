package team.hotelchain.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import team.hotelchain.payment.PaymentOutcome;
import team.hotelchain.payment.TestPaymentService;
import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffSessionView;

@SpringBootTest
@Import(CancellationIntegrationTest.ClockConfiguration.class)
class CancellationIntegrationTest {

    private static final UUID HOTEL_ID = UUID.fromString("13000000-0000-0000-0000-000000000001");
    private static final UUID ROOM_TYPE_ID = UUID.fromString("23000000-0000-0000-0000-000000000001");
    private static final UUID RATE_PLAN_ID = UUID.fromString("33000000-0000-0000-0000-000000000001");
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 12, 12);
    private static final String TOKEN = token(5);
    private static final Instant START = Instant.parse("2026-09-10T08:00:00Z");
    private static final UUID STAFF_ID = UUID.fromString("43000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_HOTEL_ID = UUID.fromString("13000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_STAFF_ID = UUID.fromString("43000000-0000-0000-0000-000000000002");

    @Autowired JdbcTemplate jdbc;
    @Autowired ReservationService reservationService;
    @Autowired TestPaymentService paymentService;
    @Autowired CancellationService cancellationService;
    @Autowired StaffReservationCancellationService staffCancellationService;
    @Autowired StaffAccessService staffAccess;
    @Autowired TestRefundGateway refundGateway;
    @Autowired TestClock clock;
    @Autowired WebApplicationContext context;

    @BeforeEach
    void seed() {
        clean();
        clock.set(START);
        refundGateway.reset();
        jdbc.update("insert into hotel values (?, ?, ?, ?)", HOTEL_ID, "속초 취소 테스트", "속초", "Asia/Seoul");
        jdbc.update("insert into hotel values (?, ?, ?, ?)", OTHER_HOTEL_ID, "제주 취소 테스트", "제주", "Asia/Seoul");
        jdbc.update("insert into room_type values (?, ?, ?, ?)", ROOM_TYPE_ID, HOTEL_ID, "스탠다드", 2);
        jdbc.update("insert into rate_plan values (?, ?, ?, ?, ?)", RATE_PLAN_ID, ROOM_TYPE_ID, "유연 요금", false, "FLEX-2026-01");
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, 'cancel-staff@example.com', '취소 담당자', ?, 'BRANCH_STAFF', ?)",
                STAFF_ID, new BCryptPasswordEncoder().encode("password"), HOTEL_ID);
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, 'other-cancel-staff@example.com', '다른 지점 담당자', ?, 'BRANCH_STAFF', ?)",
                OTHER_STAFF_ID, new BCryptPasswordEncoder().encode("password"), OTHER_HOTEL_ID);
        for (int day = 0; day < 2; day++) {
            LocalDate date = CHECK_IN.plusDays(day);
            jdbc.update("insert into rate_day values (?, ?, 100000)", RATE_PLAN_ID, date);
            jdbc.update("insert into inventory_day values (?, ?, 1, 0, 0)", ROOM_TYPE_ID, date);
        }
    }

    @AfterEach
    void cleanAfter() {
        refundGateway.reset();
        clean();
    }

    @Test
    void usesTheSavedPolicyAndRefundsTheConfirmedReservation() {
        ReservationView reservation = confirmedReservation("saved-policy");
        jdbc.update("update rate_plan set policy_version = 'CHANGED-LATER' where id = ?", RATE_PLAN_ID);

        CancellationResult result = cancellationService.cancel(reservation.id(), TOKEN, "cancel-saved-policy");

        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(result.refundAmount()).isEqualTo(200_000);
        assertThat(reservationService.get(reservation.id(), TOKEN).cancellationPolicy()).contains("FLEX-2026-01");
        assertInventory(0);
    }

    @Test
    void repeatedCancellationReturnsInventoryOnlyOnce() {
        ReservationView reservation = confirmedReservation("repeat-cancel");

        CancellationResult first = cancellationService.cancel(reservation.id(), TOKEN, "same-cancel");
        CancellationResult repeated = cancellationService.cancel(reservation.id(), TOKEN, "same-cancel");

        assertThat(repeated).isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from cancellation_attempt where reservation_id = ?", Integer.class, reservation.id()))
                .isEqualTo(1);
        assertInventory(0);
    }

    @Test
    void refundFailureKeepsTheReservationConfirmed() {
        ReservationView reservation = confirmedReservation("refund-failure");
        refundGateway.failFor(reservation.id());

        assertThatThrownBy(() -> cancellationService.cancel(reservation.id(), TOKEN, "failed-refund"))
                .isInstanceOf(RefundFailedException.class);

        assertThat(reservationService.get(reservation.id(), TOKEN).status()).isEqualTo("CONFIRMED");
        assertInventory(2);
    }

    @Test
    void cancellationIsNotAllowedAtThePolicyCutoff() {
        ReservationView reservation = confirmedReservation("cutoff");
        Instant cutoff = LocalDateTime.of(CHECK_IN.minusDays(1), LocalTime.of(18, 0))
                .atZone(ZoneId.of("Asia/Seoul")).toInstant();
        clock.set(cutoff);

        assertThatThrownBy(() -> cancellationService.cancel(reservation.id(), TOKEN, "late-cancel"))
                .isInstanceOf(CancellationNotAllowedException.class);
        assertInventory(2);
    }

    @Test
    void staffCancellationUsesTheSavedPolicyAndRecordsTheActor() {
        ReservationView reservation = confirmedReservation("staff-cancel");
        StaffSessionView session = staffAccess.login("cancel-staff@example.com", "password");

        StaffCancellationPreview preview = staffCancellationService.preview(session.token(), reservation.id());
        CancellationResult result = staffCancellationService.cancel(
                session.token(), reservation.id(), "staff-cancel-request");
        CancellationResult repeated = staffCancellationService.cancel(
                session.token(), reservation.id(), "staff-cancel-request");

        assertThat(preview.cancellable()).isTrue();
        assertThat(preview.refundAmount()).isEqualTo(200_000);
        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(repeated).isEqualTo(result);
        assertThatThrownBy(() -> staffCancellationService.cancel(
                session.token(), reservation.id(), "different-staff-cancel-request"))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("확정된 예약만 취소할 수 있습니다.");
        assertThat(jdbc.queryForObject("select staff_id from cancellation_attempt where reservation_id = ?",
                UUID.class, reservation.id())).isEqualTo(STAFF_ID);
        assertThat(jdbc.queryForObject("select count(*) from cancellation_attempt where reservation_id = ?",
                Integer.class, reservation.id())).isEqualTo(1);
        assertInventory(0);
    }

    @Test
    void staffRefundFailureKeepsTheReservationAndAuditsOneFailedAttempt() {
        ReservationView reservation = confirmedReservation("staff-refund-failure");
        StaffSessionView session = staffAccess.login("cancel-staff@example.com", "password");
        refundGateway.failFor(reservation.id());

        assertThatThrownBy(() -> staffCancellationService.cancel(
                session.token(), reservation.id(), "staff-failed-refund"))
                .isInstanceOf(RefundFailedException.class);
        assertThatThrownBy(() -> staffCancellationService.cancel(
                session.token(), reservation.id(), "staff-failed-refund"))
                .isInstanceOf(RefundFailedException.class);

        assertThat(reservationService.get(reservation.id(), TOKEN).status()).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("""
                select count(*) from cancellation_attempt
                where reservation_id = ? and refund_status = 'FAILED' and actor_type = 'STAFF' and staff_id = ?
                """, Integer.class, reservation.id(), STAFF_ID)).isEqualTo(1);
        assertInventory(2);
    }

    @Test
    void staffCancellationEndpointsPreviewAndCancelTheReservation() throws Exception {
        ReservationView reservation = confirmedReservation("staff-http-cancel");
        StaffSessionView session = staffAccess.login("cancel-staff@example.com", "password");
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();

        mvc.perform(get("/api/staff/reservations/{reservationId}/cancellation-preview", reservation.id())
                        .header("X-Staff-Session", session.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellable").value(true))
                .andExpect(jsonPath("$.refundAmount").value(200_000));

        mvc.perform(post("/api/staff/reservations/{reservationId}/cancel", reservation.id())
                        .header("X-Staff-Session", session.token())
                        .header("Idempotency-Key", "staff-http-request"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void staffCancellationEndpointsRejectAnotherHotel() throws Exception {
        ReservationView reservation = confirmedReservation("staff-other-hotel");
        StaffSessionView session = staffAccess.login("other-cancel-staff@example.com", "password");
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();

        mvc.perform(get("/api/staff/reservations/{reservationId}/cancellation-preview", reservation.id())
                        .header("X-Staff-Session", session.token()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/staff/reservations/{reservationId}/cancel", reservation.id())
                        .header("X-Staff-Session", session.token())
                        .header("Idempotency-Key", "other-hotel-request"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCancellationPreviewDisablesTheActionAtTheSavedPolicyCutoff() {
        ReservationView reservation = confirmedReservation("staff-cutoff-preview");
        Instant cutoff = LocalDateTime.of(CHECK_IN.minusDays(1), LocalTime.of(18, 0))
                .atZone(ZoneId.of("Asia/Seoul")).toInstant();
        clock.set(cutoff);
        StaffSessionView session = staffAccess.login("cancel-staff@example.com", "password");

        StaffCancellationPreview preview = staffCancellationService.preview(session.token(), reservation.id());

        assertThat(preview.cancellable()).isFalse();
        assertThat(preview.refundAmount()).isZero();
        assertThat(preview.unavailableReason()).isEqualTo("취소 가능 시간이 지났습니다.");
    }

    private ReservationView confirmedReservation(String key) {
        ReservationView reservation = reservationService.create(key, TOKEN, new ReservationRequest(
                ROOM_TYPE_ID, RATE_PLAN_ID, CHECK_IN, CHECK_IN.plusDays(2), 2, 0, 1, 200_000,
                new ReservationGuest("취소 고객", "cancel@example.com")));
        paymentService.pay(reservation.id(), TOKEN, "pay-" + key, PaymentOutcome.SUCCESS);
        return reservationService.get(reservation.id(), TOKEN);
    }

    private void assertInventory(int confirmedTotal) {
        assertThat(jdbc.queryForObject("select sum(held) from inventory_day where room_type_id = ?", Integer.class, ROOM_TYPE_ID))
                .isZero();
        assertThat(jdbc.queryForObject("select sum(confirmed) from inventory_day where room_type_id = ?", Integer.class, ROOM_TYPE_ID))
                .isEqualTo(confirmedTotal);
    }

    private void clean() {
        jdbc.update("delete from staff_session");
        jdbc.update("delete from cancellation_attempt");
        jdbc.update("delete from staff_member where id in (?, ?)", STAFF_ID, OTHER_STAFF_ID);
        jdbc.update("delete from payment_attempt");
        jdbc.update("delete from reservation_idempotency");
        jdbc.update("delete from reservation_night");
        jdbc.update("delete from reservation");
        jdbc.update("delete from inventory_day where room_type_id = ?", ROOM_TYPE_ID);
        jdbc.update("delete from rate_day where rate_plan_id = ?", RATE_PLAN_ID);
        jdbc.update("delete from rate_plan where id = ?", RATE_PLAN_ID);
        jdbc.update("delete from room_type where id = ?", ROOM_TYPE_ID);
        jdbc.update("delete from hotel where id = ?", HOTEL_ID);
        jdbc.update("delete from hotel where id = ?", OTHER_HOTEL_ID);
    }

    private static String token(int fill) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, (byte) fill);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean @Primary TestClock testClock() { return new TestClock(START); }
    }

    static final class TestClock extends Clock {
        private final AtomicReference<Instant> instant;
        TestClock(Instant initial) { instant = new AtomicReference<>(initial); }
        void set(Instant value) { instant.set(value); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
    }
}
