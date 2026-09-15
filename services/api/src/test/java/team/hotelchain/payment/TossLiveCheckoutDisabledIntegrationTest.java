package team.hotelchain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.Clock;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import team.hotelchain.reservation.BusinessConflictException;
import team.hotelchain.reservation.ReservationGuest;
import team.hotelchain.reservation.ReservationAccess;
import team.hotelchain.reservation.ReservationRequest;
import team.hotelchain.reservation.ReservationService;

@SpringBootTest(properties = {
        "payment.provider=toss-live",
        "reservation.change.gateway=toss-live",
        "payment.checkout-enabled=false",
        "payment.toss.client-key=live_gck_fixture",
        "payment.toss.secret-key=live_gsk_fixture",
        "payment.toss.merchant-account=hotel-live",
        "payment.toss.customer-origin=https://hotel.example"
})
class TossLiveCheckoutDisabledIntegrationTest {
    private static final UUID HOTEL = UUID.fromString("12100000-0000-0000-0000-000000000091");
    private static final UUID ROOM = UUID.fromString("22100000-0000-0000-0000-000000000091");
    private static final UUID RATE = UUID.fromString("32100000-0000-0000-0000-000000000091");
    private static final String TOKEN = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 12, 20);

    @Autowired JdbcTemplate jdbc;
    @Autowired ReservationService reservations;
    @Autowired TossReservationPaymentService payments;
    @Autowired ReservationAccess access;
    @Autowired TransactionTemplate transactions;
    @Autowired Clock clock;
    @Autowired TossPaymentsProperties properties;
    @Autowired TossPaymentsClient provider;
    @Autowired PaymentModeController paymentModes;
    @Autowired team.hotelchain.reservationchange.TossPaymentAdjustmentGateway changeGateway;

    @BeforeEach
    void seed() {
        clean();
        jdbc.update("insert into hotel values (?, ?, ?, ?)", HOTEL, "운영 전환", "서울", "Asia/Seoul");
        jdbc.update("insert into room_type values (?, ?, ?, ?)", ROOM, HOTEL, "스탠다드", 2);
        jdbc.update("insert into rate_plan values (?, ?, ?, ?, ?)", RATE, ROOM, "유연 요금", false, "LIVE-2026-01");
        jdbc.update("insert into rate_day values (?, ?, 100000)", RATE, CHECK_IN);
        jdbc.update("insert into inventory_day values (?, ?, 2, 0, 0)", ROOM, CHECK_IN);
    }

    @AfterEach
    void clean() {
        jdbc.update("delete from payment_provider_attempt where reservation_id in (select id from reservation where room_type_id = ?)", ROOM);
        jdbc.update("delete from reservation_idempotency where reservation_id in (select id from reservation where room_type_id = ?)", ROOM);
        jdbc.update("delete from reservation_night where reservation_id in (select id from reservation where room_type_id = ?)", ROOM);
        jdbc.update("delete from reservation where room_type_id = ?", ROOM);
        jdbc.update("delete from inventory_day where room_type_id = ?", ROOM);
        jdbc.update("delete from rate_day where rate_plan_id = ?", RATE);
        jdbc.update("delete from rate_plan where id = ?", RATE);
        jdbc.update("delete from room_type where id = ?", ROOM);
        jdbc.update("delete from hotel where id = ?", HOTEL);
    }

    @Test
    void disabled_live_checkout_creates_no_provider_attempt() {
        var reservation = reservations.create("live-disabled", TOKEN,
                new ReservationRequest(ROOM, RATE, CHECK_IN, CHECK_IN.plusDays(1),
                        2, 0, 1, 100000, new ReservationGuest("운영 고객", "live@example.com")));

        assertThatThrownBy(() -> payments.checkout(reservation.id(), TOKEN, "checkout-1"))
                .isInstanceOfSatisfying(BusinessConflictException.class,
                        error -> assertThat(error.code()).isEqualTo("PAYMENT_CHECKOUT_DISABLED"));
        assertThat(jdbc.queryForObject(
                "select count(*) from payment_provider_attempt where reservation_id = ?",
                Integer.class, reservation.id())).isZero();
    }

    @Test
    void enabled_live_checkout_stores_live_provider_snapshot() {
        var reservation = reservations.create("live-enabled", TOKEN,
                new ReservationRequest(ROOM, RATE, CHECK_IN, CHECK_IN.plusDays(1),
                        2, 0, 1, 100000, new ReservationGuest("운영 고객", "live@example.com")));
        var enabledPayments = new TossReservationPaymentService(
                jdbc, access, transactions, clock, properties, provider,
                TossPaymentEnvironment.LIVE, new PaymentCheckoutPolicy("toss-live", true));

        var checkout = enabledPayments.checkout(reservation.id(), TOKEN, "checkout-live");

        assertThat(jdbc.queryForObject(
                "select provider from payment_provider_attempt where reservation_id = ?",
                String.class, reservation.id())).isEqualTo("TOSS_LIVE");
        assertThat(payments.checkout(reservation.id(), TOKEN, "reopen-existing").orderId())
                .isEqualTo(checkout.orderId());
        assertThat(paymentModes.mode().provider()).isEqualTo("toss-live");
        assertThat(changeGateway).isNotNull();
    }
}
