package team.hotelchain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import team.hotelchain.reservation.ReservationGuest;
import team.hotelchain.reservation.ReservationNotFoundException;
import team.hotelchain.reservation.ReservationRequest;
import team.hotelchain.reservation.ReservationService;
import team.hotelchain.reservation.ReservationView;
import team.hotelchain.payment.TossPaymentsClient.ProviderPayment;
import team.hotelchain.payment.TossPaymentsClient.ProviderStatus;
import team.hotelchain.payment.TossReservationPaymentView.CheckoutView;
import team.hotelchain.payment.TossReservationPaymentView.ConfirmPaymentRequest;

@SpringBootTest(properties = {"payment.provider=toss-test", "payment.toss.client-key=test_ck_fixture",
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
    @Autowired TestPaymentService fakePayments;
    @Autowired ReservationExpiryService expiry;
    @Autowired PaymentExpiryIntegrationTest.TestClock clock;
    @Autowired StubProvider provider;
    @Autowired WebApplicationContext context;

    @BeforeEach void seed() {
        clean();
        clock.set(java.time.Instant.parse("2026-09-10T08:00:00Z"));
        provider.calls.set(0);
        provider.lookups.set(0);
        provider.response = command -> new ProviderPayment(command.paymentKey(), command.orderId(),
                command.amountKrw(), "KRW", ProviderStatus.DONE, "txn", null);
        provider.lookup = null;
        jdbc.update("insert into hotel values (?, ?, ?, ?)", HOTEL, "토스 테스트", "속초", "Asia/Seoul");
        jdbc.update("insert into room_type values (?, ?, ?, ?)", ROOM, HOTEL, "스탠다드", 2);
        jdbc.update("insert into rate_plan values (?, ?, ?, ?, ?)", RATE, ROOM, "유연 요금", false, "FLEX-2026-01");
        for (int i = 0; i < 2; i++) {
            jdbc.update("insert into rate_day values (?, ?, 100000)", RATE, CHECK_IN.plusDays(i));
            jdbc.update("insert into inventory_day values (?, ?, 2, 0, 0)", ROOM, CHECK_IN.plusDays(i));
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

    @Test void checkoutUsesStoredAmountAndReplaysOneActiveOrder() {
        ReservationView reservation = create("checkout");
        CheckoutView checkout = payments.checkout(reservation.id(), TOKEN, "once");
        assertThat(payments.checkout(reservation.id(), TOKEN, "another-key")).isEqualTo(checkout);
        assertThat(checkout.amountKrw()).isEqualTo(200000);
        assertThat(checkout.currency()).isEqualTo("KRW");
        assertThat(checkout.clientKey()).isEqualTo("test_ck_fixture");
        assertThat(checkout.successUrl()).startsWith("http://127.0.0.1:4000/").doesNotContain(TOKEN);
        assertThat(checkout.environmentLabel()).contains("테스트");
        assertThat(count("payment_provider_attempt")).isEqualTo(1);
        assertInventory(2, 0);
    }

    @Test void databaseRejectsParallelActiveOrders() {
        ReservationView reservation = create("db-active");
        payments.checkout(reservation.id(), TOKEN, "first");
        assertThatThrownBy(() -> jdbc.update("""
                insert into payment_provider_attempt
                 (id, reservation_id, provider, merchant_account, order_id, idempotency_key, amount_krw, currency, status)
                values (?, ?, 'TOSS_TEST', 'hotel-test', 'forbidden-active', 'forbidden-idem', 200000, 'KRW', 'NEW')
                """, UUID.randomUUID(), reservation.id())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void databaseRejectsDuplicateProviderPaymentKey() {
        ReservationView first = create("db-key-first");
        payments.confirm(first.id(), TOKEN, confirmation(payments.checkout(first.id(), TOKEN, "first")));
        ReservationView second = create("db-key-second");
        CheckoutView checkout = payments.checkout(second.id(), TOKEN, "second");
        assertThatThrownBy(() -> jdbc.update("update payment_provider_attempt set payment_key = 'pay' where order_id = ?", checkout.orderId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void rejectsCallbackAmountWithoutInventoryChange() {
        ReservationView reservation = create("tamper");
        CheckoutView checkout = payments.checkout(reservation.id(), TOKEN, "once");
        assertThatThrownBy(() -> payments.confirm(reservation.id(), TOKEN,
                new ConfirmPaymentRequest(checkout.orderId(), "pay", 1L))).hasMessageContaining("결제 금액");
        assertThat(provider.calls.get()).isZero();
        assertInventory(2, 0);
    }

    @Test void duplicateConfirmCapturesOnceAndRejectsDifferentPaymentKey() {
        ReservationView reservation = create("duplicate");
        CheckoutView checkout = payments.checkout(reservation.id(), TOKEN, "once");
        var first = payments.confirm(reservation.id(), TOKEN, confirmation(checkout));
        assertThat(first.status()).isEqualTo("CONFIRMED");
        assertThat(first.paymentStatus()).isEqualTo("SUCCEEDED");
        assertThat(payments.confirm(reservation.id(), TOKEN, confirmation(checkout))).isEqualTo(first);
        assertThatThrownBy(() -> payments.confirm(reservation.id(), TOKEN,
                new ConfirmPaymentRequest(checkout.orderId(), "different", 200000L))).hasMessageContaining("결제 키");
        assertThat(provider.calls.get()).isEqualTo(1);
        assertThat(count("payment_transaction")).isEqualTo(1);
        assertThat(jdbc.queryForMap("select provider, merchant_account, captured_amount_krw from payment_transaction where reservation_id = ?", reservation.id()))
                .containsEntry("provider", "TOSS_TEST").containsEntry("merchant_account", "hotel-test")
                .containsEntry("captured_amount_krw", 200000L);
        assertInventory(0, 2);
    }

    @Test void explicitFailureKeepsRetryableHoldButExpiresNormally() {
        ReservationView reservation = create("failed");
        CheckoutView checkout = payments.checkout(reservation.id(), TOKEN, "once");
        provider.response = command -> new ProviderPayment(null, null, 0, null, ProviderStatus.FAILED, null, "DECLINED");
        assertThat(payments.confirm(reservation.id(), TOKEN, confirmation(checkout)).paymentStatus()).isEqualTo("FAILED");
        assertInventory(2, 0);
        assertThat(payments.checkout(reservation.id(), TOKEN, "retry").orderId()).isNotEqualTo(checkout.orderId());
        clock.set(reservation.expiresAt());
        assertThat(expiry.expireDue(10)).isEqualTo(1);
        assertInventory(0, 0);
    }

    @Test void paymentKeyBoundToAnotherReservationIsRejectedBeforeApproval() {
        ReservationView first = create("key-first");
        CheckoutView firstCheckout = payments.checkout(first.id(), TOKEN, "first");
        payments.confirm(first.id(), TOKEN, confirmation(firstCheckout));
        ReservationView second = create("key-second");
        CheckoutView secondCheckout = payments.checkout(second.id(), TOKEN, "second");
        assertThatThrownBy(() -> payments.confirm(second.id(), TOKEN, confirmation(secondCheckout)))
                .hasMessageContaining("결제 키");
        assertThat(provider.calls.get()).isEqualTo(1);
        assertThat(count("payment_transaction")).isEqualTo(1);
        assertInventory(2, 2);
    }

    @Test void providerSuccessWithInternalApplyFailureRollsBackAndReplaysLookup() {
        ReservationView reservation = create("apply-failure");
        CheckoutView checkout = payments.checkout(reservation.id(), TOKEN, "once");
        jdbc.update("update inventory_day set held = 0 where room_type_id = ? and stay_date = ?", ROOM, CHECK_IN.plusDays(1));
        assertThat(payments.confirm(reservation.id(), TOKEN, confirmation(checkout)).paymentStatus()).isEqualTo("UNKNOWN");
        assertThat(reservations.get(reservation.id(), TOKEN).status()).isEqualTo("PENDING_PAYMENT");
        assertThat(count("payment_transaction")).isZero();
        assertInventory(1, 0);
        jdbc.update("update inventory_day set held = 1 where room_type_id = ? and stay_date = ?", ROOM, CHECK_IN.plusDays(1));
        provider.lookup = new ProviderPayment("pay", checkout.orderId(), 200000, "KRW", ProviderStatus.DONE, "txn", null);
        assertThat(payments.confirm(reservation.id(), TOKEN, confirmation(checkout)).status()).isEqualTo("CONFIRMED");
        assertThat(provider.calls.get()).isEqualTo(1);
        assertThat(count("payment_transaction")).isEqualTo(1);
        assertInventory(0, 2);
    }

    @Test void unknownPreservesExpiredHoldAndReconcilesByLookupWithoutReapproval() {
        ReservationView reservation = create("unknown");
        CheckoutView checkout = payments.checkout(reservation.id(), TOKEN, "once");
        provider.response = command -> new ProviderPayment(null, null, 0, null, ProviderStatus.UNKNOWN, null, "HTTP_IO");
        assertThat(payments.confirm(reservation.id(), TOKEN, confirmation(checkout)).paymentStatus()).isEqualTo("UNKNOWN");
        clock.set(reservation.expiresAt().plusSeconds(16 * 86400));
        assertThat(expiry.expireDue(10)).isZero();
        assertInventory(2, 0);
        provider.lookup = new ProviderPayment("pay", checkout.orderId(), 200000, "KRW", ProviderStatus.DONE, "txn", null);
        assertThat(payments.confirm(reservation.id(), TOKEN, confirmation(checkout)).status()).isEqualTo("CONFIRMED");
        assertThat(provider.calls.get()).isEqualTo(1);
        assertThat(provider.lookups.get()).isEqualTo(1);
        assertInventory(0, 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"order", "paymentKey", "amount", "currency"})
    void providerMismatchCannotConfirmOrReleaseHold(String mismatch) {
        ReservationView reservation = create("mismatch");
        CheckoutView checkout = payments.checkout(reservation.id(), TOKEN, "once");
        provider.response = command -> new ProviderPayment("paymentKey".equals(mismatch) ? "wrong-key" : "pay",
                "order".equals(mismatch) ? "wrong-order" : checkout.orderId(),
                "amount".equals(mismatch) ? 1 : 200000, "currency".equals(mismatch) ? "USD" : "KRW", ProviderStatus.DONE, "txn", null);
        assertThat(payments.confirm(reservation.id(), TOKEN, confirmation(checkout)).paymentStatus()).isEqualTo("UNKNOWN");
        assertThat(count("payment_transaction")).isZero();
        clock.set(reservation.expiresAt());
        assertThat(expiry.expireDue(10)).isZero();
        assertInventory(2, 0);
    }

    @Test void providerExceptionAndStillUnknownLookupCannotReleaseOrReapprove() {
        ReservationView reservation = create("transport-exception");
        CheckoutView checkout = payments.checkout(reservation.id(), TOKEN, "once");
        provider.response = command -> { throw new IllegalStateException("external timeout"); };
        assertThat(payments.confirm(reservation.id(), TOKEN, confirmation(checkout)).paymentStatus()).isEqualTo("UNKNOWN");
        assertThat(payments.confirm(reservation.id(), TOKEN, confirmation(checkout)).paymentStatus()).isEqualTo("UNKNOWN");
        assertThat(provider.calls.get()).isEqualTo(1);
        clock.set(reservation.expiresAt());
        assertThat(expiry.expireDue(10)).isZero();
        assertInventory(2, 0);
    }

    @Test void expiryCannotReleaseApprovingHoldAndConcurrentConfirmCannotCaptureTwice() throws Exception {
        ReservationView reservation = create("race");
        CheckoutView checkout = payments.checkout(reservation.id(), TOKEN, "once");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        provider.response = command -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            entered.countDown();
            await(finish);
            return new ProviderPayment("pay", checkout.orderId(), 200000, "KRW", ProviderStatus.DONE, "txn", null);
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> payments.confirm(reservation.id(), TOKEN, confirmation(checkout)));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                clock.set(reservation.expiresAt());
                assertThat(expiry.expireDue(10)).isZero();
                assertThat(payments.confirm(reservation.id(), TOKEN, confirmation(checkout)).paymentStatus()).isEqualTo("APPROVING");
                assertInventory(2, 0);
            } finally { finish.countDown(); }
            assertThat(first.get(10, TimeUnit.SECONDS).status()).isEqualTo("CONFIRMED");
        }
        assertThat(provider.calls.get()).isEqualTo(1);
        assertInventory(0, 2);
    }

    @Test void checkoutOnlyAndUnstartedConfirmationExpireWithoutProviderCall() {
        ReservationView reservation = create("expired");
        CheckoutView checkout = payments.checkout(reservation.id(), TOKEN, "once");
        clock.set(reservation.expiresAt());
        assertThatThrownBy(() -> payments.confirm(reservation.id(), TOKEN, confirmation(checkout)))
                .isInstanceOf(ReservationExpiredException.class);
        assertThat(expiry.expireDue(10)).isEqualTo(1);
        assertThat(provider.calls.get()).isZero();
        assertInventory(0, 0);
    }

    @Test void protectedAttemptDoesNotStarveLaterDueReservationsInLimitedBatch() {
        ReservationView first = create("protected-first");
        CheckoutView checkout = payments.checkout(first.id(), TOKEN, "first");
        provider.response = command -> new ProviderPayment(null, null, 0, null, ProviderStatus.UNKNOWN, null, "HTTP_IO");
        payments.confirm(first.id(), TOKEN, confirmation(checkout));
        clock.set(clock.instant().plusSeconds(60));
        ReservationView later = create("unprotected-later");
        payments.checkout(later.id(), TOKEN, "later");
        clock.set(later.expiresAt());
        assertThat(expiry.expireDue(1)).isEqualTo(1);
        assertThat(reservations.get(first.id(), TOKEN).status()).isEqualTo("PENDING_PAYMENT");
        assertThat(reservations.get(later.id(), TOKEN).status()).isEqualTo("EXPIRED");
        assertInventory(2, 0);
    }

    @Test void wrongTokenAndForeignOrderCannotMutatePayment() {
        ReservationView reservation = create("auth");
        CheckoutView checkout = payments.checkout(reservation.id(), TOKEN, "once");
        byte[] otherBytes = new byte[32];
        java.util.Arrays.fill(otherBytes, (byte) 1);
        String otherToken = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(otherBytes);
        assertThatThrownBy(() -> payments.status(reservation.id(), otherToken)).isInstanceOf(ReservationNotFoundException.class);
        assertThatThrownBy(() -> payments.confirm(reservation.id(), TOKEN,
                new ConfirmPaymentRequest("foreign", "pay", 200000L))).hasMessageContaining("주문");
        assertThat(provider.calls.get()).isZero();
        assertInventory(2, 0);
    }

    @Test void tossModeCannotCreateFakePaymentOrTransaction() {
        ReservationView reservation = create("fake-isolation");
        assertThatThrownBy(() -> fakePayments.pay(reservation.id(), TOKEN, "fake", PaymentOutcome.SUCCESS))
                .hasMessageContaining("fake");
        assertThat(count("payment_attempt")).isZero();
        assertThat(count("payment_transaction")).isZero();
        assertInventory(2, 0);
    }

    @Test void reservationScopedEndpointsRequireTokenAndExposeOnlyServerState() throws Exception {
        ReservationView reservation = create("http");
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        mvc.perform(post("/api/reservations/{id}/payment-checkout", reservation.id())
                .header("X-Reservation-Token", TOKEN).header("Idempotency-Key", "http"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amountKrw").value(200000))
                .andExpect(jsonPath("$.clientKey").value("test_ck_fixture"));
        CheckoutView checkout = payments.checkout(reservation.id(), TOKEN, "http");
        mvc.perform(post("/api/reservations/{id}/payment-confirm", reservation.id())
                .header("X-Reservation-Token", TOKEN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"" + checkout.orderId() + "\",\"paymentKey\":\"pay\",\"amountKrw\":200000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(get("/api/reservations/{id}/payment-status", reservation.id()).header("X-Reservation-Token", TOKEN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.paymentKey").doesNotExist()).andExpect(jsonPath("$.secretKey").doesNotExist());
        mvc.perform(get("/api/reservations/{id}/payment-status", reservation.id())).andExpect(status().isBadRequest());
    }

    private ReservationView create(String key) {
        return reservations.create(key, TOKEN, new ReservationRequest(ROOM, RATE, CHECK_IN, CHECK_IN.plusDays(2),
                2, 0, 1, 200000, new ReservationGuest("결제 고객", "toss@example.com")));
    }
    private ConfirmPaymentRequest confirmation(CheckoutView checkout) { return new ConfirmPaymentRequest(checkout.orderId(), "pay", 200000L); }
    private int count(String table) { return jdbc.queryForObject("select count(*) from " + table + " where reservation_id in (select id from reservation where room_type_id = ?)", Integer.class, ROOM); }
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
        @Override public ProviderPayment cancel(CancelCommand command) { throw new UnsupportedOperationException(); }
    }
}
