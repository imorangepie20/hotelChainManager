package team.hotelchain.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

import team.hotelchain.payment.PaymentOutcome;
import team.hotelchain.payment.TestPaymentService;

@SpringBootTest
@Import(CancellationIntegrationTest.ClockConfiguration.class)
class CancellationIntegrationTest {

    private static final UUID HOTEL_ID = UUID.fromString("13000000-0000-0000-0000-000000000001");
    private static final UUID ROOM_TYPE_ID = UUID.fromString("23000000-0000-0000-0000-000000000001");
    private static final UUID RATE_PLAN_ID = UUID.fromString("33000000-0000-0000-0000-000000000001");
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 12, 12);
    private static final String TOKEN = token(5);
    private static final Instant START = Instant.parse("2026-09-10T08:00:00Z");

    @Autowired JdbcTemplate jdbc;
    @Autowired ReservationService reservationService;
    @Autowired TestPaymentService paymentService;
    @Autowired CancellationService cancellationService;
    @Autowired TestRefundGateway refundGateway;
    @Autowired TestClock clock;

    @BeforeEach
    void seed() {
        clean();
        clock.set(START);
        refundGateway.reset();
        jdbc.update("insert into hotel values (?, ?, ?, ?)", HOTEL_ID, "속초 취소 테스트", "속초", "Asia/Seoul");
        jdbc.update("insert into room_type values (?, ?, ?, ?)", ROOM_TYPE_ID, HOTEL_ID, "스탠다드", 2);
        jdbc.update("insert into rate_plan values (?, ?, ?, ?, ?)", RATE_PLAN_ID, ROOM_TYPE_ID, "유연 요금", false, "FLEX-2026-01");
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
        jdbc.update("delete from cancellation_attempt");
        jdbc.update("delete from payment_attempt");
        jdbc.update("delete from reservation_idempotency");
        jdbc.update("delete from reservation_night");
        jdbc.update("delete from reservation");
        jdbc.update("delete from inventory_day where room_type_id = ?", ROOM_TYPE_ID);
        jdbc.update("delete from rate_day where rate_plan_id = ?", RATE_PLAN_ID);
        jdbc.update("delete from rate_plan where id = ?", RATE_PLAN_ID);
        jdbc.update("delete from room_type where id = ?", ROOM_TYPE_ID);
        jdbc.update("delete from hotel where id = ?", HOTEL_ID);
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
