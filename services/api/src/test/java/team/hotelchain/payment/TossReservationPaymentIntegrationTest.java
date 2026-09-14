package team.hotelchain.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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

/**
 * TEST_DATABASE_URL의 격리 PostgreSQL에서만 실행하는 결제 원자성 회귀 테스트다.
 * 현재 사용자 서버·데이터에 연결하지 않기 위해 의도적으로 비활성화했다.
 */
@Disabled("격리된 TEST_DATABASE_URL PostgreSQL을 준비한 뒤에만 실행한다. 사용자 서버에는 실행하지 않는다.")
@SpringBootTest(properties = {
        "payment.provider=toss-test", "payment.toss.client-key=test_ck_fixture",
        "payment.toss.secret-key=test_sk_fixture", "payment.toss.merchant-account=hotel-test",
        "payment.toss.customer-origin=http://127.0.0.1:4000"})
@Import({PaymentExpiryIntegrationTest.ClockConfiguration.class, TossReservationPaymentIntegrationTest.ProviderConfiguration.class})
class TossReservationPaymentIntegrationTest {
    private static final UUID HOTEL = UUID.fromString("12100000-0000-0000-0000-000000000001");
    private static final UUID ROOM = UUID.fromString("22100000-0000-0000-0000-000000000001");
    private static final UUID RATE = UUID.fromString("32100000-0000-0000-0000-000000000001");
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 12, 2);
    private static final String TOKEN = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);

    @Autowired JdbcTemplate jdbc;
    @Autowired ReservationService reservations;
    @Autowired TossReservationPaymentService payments;
    @Autowired ReservationExpiryService expiry;
    @Autowired PaymentExpiryIntegrationTest.TestClock clock;
    @Autowired StubProvider provider;

    @BeforeEach void seed() {
        clean();
        clock.set(Instant.parse("2026-09-10T08:00:00Z"));
        provider.calls.set(0);
        provider.lookups.set(0);
        provider.response = command -> done(command.paymentKey(), command.orderId());
        provider.lookup = null;
        jdbc.update("insert into hotel values (?, ?, ?, ?)", HOTEL, "토스 테스트", "속초", "Asia/Seoul");
        jdbc.update("insert into room_type values (?, ?, ?, ?)", ROOM, HOTEL, "스탠다드", 2);
        jdbc.update("insert into rate_plan values (?, ?, ?, ?, ?)", RATE, ROOM, "유연 요금", false, "FLEX-2026-01");
        for (int day = 0; day < 2; day++) {
            jdbc.update("insert into rate_day values (?, ?, 100000)", RATE, CHECK_IN.plusDays(day));
            jdbc.update("insert into inventory_day values (?, ?, 2, 0, 0)", ROOM, CHECK_IN.plusDays(day));
        }
    }

    @AfterEach void clean() {
        jdbc.update("delete from payment_provider_attempt where reservation_id in (select id from reservation where room_type_id = ?)", ROOM);
        jdbc.update("delete from payment_transaction where reservation_id in (select id from reservation where room_type_id = ?)", ROOM);
        jdbc.update("delete from payment_attempt where reservation_id in (select id from reservation where room_type_id = ?)", ROOM);
        jdbc.update("delete from reservation_idempotency where reservation_id in (select id from reservation where room_type_id = ?)", ROOM);
        jdbc.update("delete from reservation_night where reservation_id in (select id from reservation where room_type_id = ?)", ROOM);
        jdbc.update("delete from reservation where room_type_id = ?", ROOM);
        jdbc.update("delete from inventory_day where room_type_id = ?", ROOM);
        jdbc.update("delete from rate_day where rate_plan_id = ?", RATE);
        jdbc.update("delete from rate_plan where id = ?", RATE);
        jdbc.update("delete from room_type where id = ?", ROOM);
        jdbc.update("delete from hotel where id = ?", HOTEL);
    }

    @Test void duplicateConfirmCapturesOnceAndMovesEveryNightFromHeldToConfirmed() {
        ReservationView reservation = create("duplicate");
        var checkout = payments.checkout(reservation.id(), TOKEN, "once");

        var first = payments.confirm(reservation.id(), TOKEN, confirmation(checkout));
        var replay = payments.confirm(reservation.id(), TOKEN, confirmation(checkout));

        assertThat(first).isEqualTo(replay);
        assertThat(first.status()).isEqualTo("CONFIRMED");
        assertThat(provider.calls.get()).isEqualTo(1);
        assertThat(count("payment_provider_attempt")).isEqualTo(1);
        assertThat(count("payment_transaction")).isEqualTo(1);
        assertInventory(0, 2);
    }

    @Test void unknownApprovalKeepsHoldPastExpiryThenReconcilesWithoutSecondConfirm() {
        ReservationView reservation = create("unknown");
        var checkout = payments.checkout(reservation.id(), TOKEN, "once");
        provider.response = command -> new TossPaymentsClient.ProviderPayment(null, null, 0, null,
                TossPaymentsClient.ProviderStatus.UNKNOWN, null, "HTTP_IO");

        assertThat(payments.confirm(reservation.id(), TOKEN, confirmation(checkout)).paymentStatus()).isEqualTo("UNKNOWN");
        clock.set(reservation.expiresAt());
        assertThat(expiry.expireDue(10)).isZero();
        provider.lookup = done("pay", checkout.orderId());

        assertThat(payments.reconcile(reservation.id(), TOKEN).status()).isEqualTo("CONFIRMED");
        assertThat(provider.calls.get()).isEqualTo(1);
        assertThat(provider.lookups.get()).isEqualTo(1);
        assertInventory(0, 2);
    }

    @Test void expiryRaceCannotReleaseApprovingHoldOrCaptureTwice() throws Exception {
        ReservationView reservation = create("race");
        var checkout = payments.checkout(reservation.id(), TOKEN, "once");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        provider.response = command -> {
            entered.countDown();
            await(finish);
            return done(command.paymentKey(), command.orderId());
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> payments.confirm(reservation.id(), TOKEN, confirmation(checkout)));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            clock.set(reservation.expiresAt());
            assertThat(expiry.expireDue(10)).isZero();
            assertThat(payments.confirm(reservation.id(), TOKEN, confirmation(checkout)).paymentStatus()).isEqualTo("APPROVING");
            finish.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).status()).isEqualTo("CONFIRMED");
        }
        assertThat(provider.calls.get()).isEqualTo(1);
        assertInventory(0, 2);
    }

    private ReservationView create(String idempotencyKey) {
        return reservations.create(idempotencyKey, TOKEN, new ReservationRequest(ROOM, RATE, CHECK_IN, CHECK_IN.plusDays(2),
                2, 0, 1, 200000, new ReservationGuest("결제 고객", "toss@example.com")));
    }
    private TossReservationPaymentView.ConfirmPaymentRequest confirmation(TossReservationPaymentView.CheckoutView checkout) {
        return new TossReservationPaymentView.ConfirmPaymentRequest(checkout.orderId(), "pay", 200000L);
    }
    private TossPaymentsClient.ProviderPayment done(String paymentKey, String orderId) {
        return new TossPaymentsClient.ProviderPayment(paymentKey, orderId, 200000, "KRW", TossPaymentsClient.ProviderStatus.DONE, "txn", null);
    }
    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table + " where reservation_id in (select id from reservation where room_type_id = ?)", Integer.class, ROOM);
    }
    private void assertInventory(int held, int confirmed) {
        assertThat(jdbc.queryForObject("select sum(held) from inventory_day where room_type_id = ?", Integer.class, ROOM)).isEqualTo(held);
        assertThat(jdbc.queryForObject("select sum(confirmed) from inventory_day where room_type_id = ?", Integer.class, ROOM)).isEqualTo(confirmed);
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("provider timeout"); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
    }

    @TestConfiguration static class ProviderConfiguration {
        @Bean @Primary StubProvider stubProvider() { return new StubProvider(); }
    }
    static class StubProvider implements TossPaymentsClient {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger lookups = new AtomicInteger();
        Function<ConfirmCommand, ProviderPayment> response;
        ProviderPayment lookup;
        @Override public ProviderPayment confirm(ConfirmCommand command) { calls.incrementAndGet(); return response.apply(command); }
        @Override public ProviderPayment lookup(String paymentKey) { lookups.incrementAndGet(); return lookup; }
    }
}
