package team.hotelchain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

import team.hotelchain.reservation.ReservationGuest;
import team.hotelchain.reservation.ReservationRequest;
import team.hotelchain.reservation.ReservationService;
import team.hotelchain.reservation.ReservationView;

@SpringBootTest
@Import(PaymentExpiryIntegrationTest.ClockConfiguration.class)
class PaymentExpiryIntegrationTest {

    private static final UUID HOTEL_ID = UUID.fromString("12000000-0000-0000-0000-000000000001");
    private static final UUID ROOM_TYPE_ID = UUID.fromString("22000000-0000-0000-0000-000000000001");
    private static final UUID RATE_PLAN_ID = UUID.fromString("32000000-0000-0000-0000-000000000001");
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 12, 2);
    private static final String TOKEN = token(4);
    private static final Instant START = Instant.parse("2026-09-10T08:00:00Z");

    @Autowired JdbcTemplate jdbc;
    @Autowired ReservationService reservationService;
    @Autowired TestClock clock;
    @Autowired TestPaymentService paymentService;
    @Autowired ReservationExpiryService expiryService;

    @BeforeEach
    void seed() {
        clean();
        clock.set(START);
        jdbc.update("insert into hotel values (?, ?, ?, ?)", HOTEL_ID, "속초 결제 테스트", "속초", "Asia/Seoul");
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
        clean();
    }

    @Test
    void successfulPaymentMovesHeldInventoryOnce() {
        ReservationView reservation = create("payment-success");

        PaymentResult first = paymentService.pay(reservation.id(), TOKEN, "pay-once", PaymentOutcome.SUCCESS);
        PaymentResult repeated = paymentService.pay(reservation.id(), TOKEN, "pay-once", PaymentOutcome.SUCCESS);

        assertThat(first.status()).isEqualTo("CONFIRMED");
        assertThat(repeated).isEqualTo(first);
        assertInventory(0, 2);
    }

    @Test
    void failedPaymentKeepsTheHoldForRetry() {
        ReservationView reservation = create("payment-failure");

        PaymentResult result = paymentService.pay(reservation.id(), TOKEN, "failed-attempt", PaymentOutcome.FAILURE);

        assertThat(result.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(result.paymentStatus()).isEqualTo("FAILED");
        assertInventory(2, 0);
    }

    @Test
    void paymentAtTheExpiryInstantExpiresAndReturnsInventory() {
        ReservationView reservation = create("expires-at-boundary");
        clock.set(reservation.expiresAt());

        assertThatThrownBy(() -> paymentService.pay(
                reservation.id(), TOKEN, "too-late", PaymentOutcome.SUCCESS))
                .isInstanceOf(ReservationExpiredException.class);

        assertThat(reservationService.get(reservation.id(), TOKEN).status()).isEqualTo("EXPIRED");
        assertInventory(0, 0);
    }

    @Test
    void expiryBatchReturnsEachHoldOnlyOnce() {
        ReservationView reservation = create("expiry-batch");
        clock.set(reservation.expiresAt().plusSeconds(1));

        assertThat(expiryService.expireDue(10)).isEqualTo(1);
        assertThat(expiryService.expireDue(10)).isZero();
        assertInventory(0, 0);
    }

    @Test
    void paymentAndExpiryRaceStillReturnTheHoldOnce() throws Exception {
        ReservationView reservation = create("expiry-race");
        clock.set(reservation.expiresAt());
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> payment = executor.submit(() -> {
                await(start);
                try {
                    paymentService.pay(reservation.id(), TOKEN, "race-payment", PaymentOutcome.SUCCESS);
                } catch (ReservationExpiredException ignored) {
                    // 만료 정각에는 결제와 만료 중 잠금을 먼저 얻은 경로가 재고를 한 번 반환한다.
                }
            });
            Future<?> expiry = executor.submit(() -> {
                await(start);
                expiryService.expireDue(10);
            });
            start.countDown();
            payment.get();
            expiry.get();
        }

        assertThat(reservationService.get(reservation.id(), TOKEN).status()).isEqualTo("EXPIRED");
        assertInventory(0, 0);
    }

    private ReservationView create(String key) {
        return reservationService.create(key, TOKEN, new ReservationRequest(
                ROOM_TYPE_ID, RATE_PLAN_ID, CHECK_IN, CHECK_IN.plusDays(2), 2, 0, 1, 200_000,
                new ReservationGuest("결제 고객", "payment@example.com")));
    }

    private void assertInventory(int heldTotal, int confirmedTotal) {
        assertThat(jdbc.queryForObject("select sum(held) from inventory_day where room_type_id = ?", Integer.class, ROOM_TYPE_ID))
                .isEqualTo(heldTotal);
        assertThat(jdbc.queryForObject("select sum(confirmed) from inventory_day where room_type_id = ?", Integer.class, ROOM_TYPE_ID))
                .isEqualTo(confirmedTotal);
    }

    private void clean() {
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

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static String token(int fill) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, (byte) fill);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock(START);
        }
    }

    static final class TestClock extends Clock {
        private final AtomicReference<Instant> instant;

        TestClock(Instant initial) {
            this.instant = new AtomicReference<>(initial);
        }

        void set(Instant value) {
            instant.set(value);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
    }
}
