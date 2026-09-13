package team.hotelchain.reservationchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import team.hotelchain.payment.PaymentOutcome;
import team.hotelchain.payment.ReservationExpiredException;
import team.hotelchain.payment.TestPaymentService;
import team.hotelchain.reservation.ReservationGuest;
import team.hotelchain.reservation.ReservationRequest;
import team.hotelchain.reservation.ReservationService;
import team.hotelchain.reservation.ReservationView;
import team.hotelchain.staff.StaffAccessService;

@SpringBootTest(properties = {
        "reservation.change.settlement-enabled=true",
        "reservation.change.gateway=fake"
})
class ReservationChangeSettlementIntegrationTest {
    private static final UUID HOTEL = UUID.fromString("81000000-0000-0000-0000-000000000001");
    private static final UUID ROOM_TYPE = UUID.fromString("82000000-0000-0000-0000-000000000001");
    private static final UUID RATE_PLAN = UUID.fromString("83000000-0000-0000-0000-000000000001");
    private static final UUID STAFF = UUID.fromString("85000000-0000-0000-0000-000000000001");
    private static final String TOKEN = token(8);

    @Autowired JdbcTemplate jdbc;
    @Autowired ReservationService reservations;
    @Autowired TestPaymentService payments;
    @Autowired PaymentAdjustmentGateway gateway;
    @Autowired StaffAccessService staffAccess;
    @Autowired ReservationChangeRequestService changeRequests;
    @Autowired ReservationChangeOutboxWorker outboxWorker;
    @Autowired ReservationChangeSettlementService settlement;
    @Autowired WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        clean();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        LocalDate checkIn = LocalDate.now().plusDays(40);
        jdbc.update("insert into hotel values (?, ?, ?, ?)", HOTEL, "정산 테스트 호텔", "속초", "Asia/Seoul");
        jdbc.update("insert into room_type values (?, ?, ?, ?)", ROOM_TYPE, HOTEL, "정산 테스트 객실", 2);
        jdbc.update("insert into rate_plan values (?, ?, ?, false, ?)", RATE_PLAN, ROOM_TYPE, "정산 테스트 요금", "FLEX");
        jdbc.update("""
                insert into staff_member (id, email, display_name, password_hash, role, hotel_id)
                values (?, 'settlement-hq@example.com', '정산 본사 관리자', ?, 'HQ_ADMIN', null)
                """, STAFF, new BCryptPasswordEncoder().encode("password"));
        for (int day = 0; day < 6; day++) {
            LocalDate date = checkIn.plusDays(day);
            jdbc.update("insert into rate_day values (?, ?, 100000)", RATE_PLAN, date);
            jdbc.update("insert into inventory_day values (?, ?, 3, 0, 0)", ROOM_TYPE, date);
        }
    }

    @AfterEach
    void cleanAfter() {
        clean();
    }

    @Test
    void createsSettlementSchemaWithHashedTokensAndSafetyConstraints() {
        assertThat(existingTables()).contains(
                "payment_transaction",
                "reservation_change_hold_day",
                "payment_adjustment_attempt",
                "reservation_change_outbox",
                "reservation_change_customer_session");
        assertThat(columns("payment_adjustment_attempt")).contains("public_token_hash").doesNotContain("public_token");
        assertThat(columns("reservation_change_customer_session")).contains("token_hash").doesNotContain("token");
        assertThat(columns("reservation_change_outbox")).contains(
                "claim_token", "attempt_count", "next_attempt_at", "lease_expires_at");
        assertThat(columns("reservation_stay_change")).contains("change_request_id");
        assertThat(constraintDefinitions("payment_transaction"))
                .anyMatch(definition -> definition.contains("refunded_amount_krw <= captured_amount_krw"));
    }

    @Test
    void successfulTestPaymentRecordsOneFakeOriginalTransaction() {
        ReservationView reservation = create("successful-original", 0);

        payments.pay(reservation.id(), TOKEN, "capture-once", PaymentOutcome.SUCCESS);
        payments.pay(reservation.id(), TOKEN, "capture-once", PaymentOutcome.SUCCESS);

        List<Map<String, Object>> rows = jdbc.queryForList("""
                select provider, merchant_account, transaction_type, captured_amount_krw,
                       refunded_amount_krw, currency
                from payment_transaction where reservation_id = ?
                """, reservation.id());
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst()).containsEntry("provider", "FAKE")
                .containsEntry("merchant_account", "LOCAL")
                .containsEntry("transaction_type", "ORIGINAL_CHARGE")
                .containsEntry("captured_amount_krw", 200_000L)
                .containsEntry("refunded_amount_krw", 0L)
                .containsEntry("currency", "KRW");
    }

    @Test
    void failedAndExpiredTestPaymentsDoNotCreateCapturedTransactions() {
        ReservationView failed = create("failed-original", 0);
        payments.pay(failed.id(), TOKEN, "capture-failed", PaymentOutcome.FAILURE);

        ReservationView expired = create("expired-original", 2);
        jdbc.update("update reservation set expires_at = now() - interval '1 second' where id = ?", expired.id());
        assertThatThrownBy(() -> payments.pay(expired.id(), TOKEN, "capture-expired", PaymentOutcome.SUCCESS))
                .isInstanceOf(ReservationExpiredException.class);

        assertThat(jdbc.queryForObject("select count(*) from payment_transaction", Integer.class)).isZero();
    }

    @Test
    void fakeGatewayUsesProviderNeutralCommands() {
        PaymentAdjustmentGateway.GatewayAdjustmentResult result = gateway.createCheckout(
                new PaymentAdjustmentGateway.GatewayCheckoutCommand(
                        UUID.randomUUID(), "gateway-contract", 100_000, "KRW", URI.create("http://localhost/return")));

        assertThat(result.status()).isEqualTo(PaymentAdjustmentGateway.GatewayResultStatus.PENDING);
        assertThat(result.checkoutUrl()).hasScheme("http");
    }

    @Test
    void paymentLinkStoresOnlyHashesAndConvergesThroughCustomerSession() throws Exception {
        ReservationView reservation = create("change-charge", 0);
        payments.pay(reservation.id(), TOKEN, "capture-change-charge", PaymentOutcome.SUCCESS);
        LocalDate targetCheckIn = LocalDate.now().plusDays(42);
        jdbc.update("update rate_day set amount_krw = 150000 where rate_plan_id = ? and stay_date >= ? and stay_date < ?",
                RATE_PLAN, targetCheckIn, targetCheckIn.plusDays(2));
        String staffToken = staffAccess.login("settlement-hq@example.com", "password").token();
        ReservationChangeRequestView request = changeRequests.create(
                staffToken, reservation.id(), "create-change-charge",
                new CreateReservationChangeRequest(
                        targetCheckIn, targetCheckIn.plusDays(2), ROOM_TYPE, RATE_PLAN, 300_000L));
        String publicToken = token(9);

        mockMvc.perform(post("/api/staff/reservation-change-requests/{id}/payment-link", request.id())
                        .header("X-Staff-Session", staffToken)
                        .header("Idempotency-Key", "create-payment-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":%d,"publicToken":"%s"}
                                """.formatted(request.version(), publicToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.customerUrl").value(
                        "http://127.0.0.1:4000/reservation-change-payment#" + publicToken));

        assertThat(jdbc.queryForObject("select public_token_hash from payment_adjustment_attempt", String.class))
                .hasSize(64).doesNotContain(publicToken);
        assertThat(jdbc.queryForObject("select count(*) from reservation_change_outbox", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select sum(held) from inventory_day where room_type_id = ?", Integer.class, ROOM_TYPE))
                .isEqualTo(2);

        assertThat(outboxWorker.processNext()).isTrue();
        assertThat(jdbc.queryForObject("select checkout_url from payment_adjustment_attempt", String.class))
                .contains("/api/test/reservation-change-payments/");

        MvcResult exchanged = mockMvc.perform(post("/api/reservation-change-payments/session")
                        .header("X-Reservation-Change-Token", publicToken))
                .andExpect(status().isNoContent())
                .andReturn();
        String setCookie = exchanged.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("reservation_change_session=")
                .contains("HttpOnly").contains("SameSite=Strict")
                .contains("Path=/api/reservation-change-payments")
                .doesNotContain(publicToken);
        String cookie = setCookie.substring(0, setCookie.indexOf(';'));
        String[] cookieParts = cookie.split("=", 2);
        Cookie sessionCookie = new Cookie(cookieParts[0], cookieParts[1]);

        mockMvc.perform(get("/api/reservation-change-payments/current").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationNumberSuffix").value(
                        reservation.id().toString().substring(reservation.id().toString().length() - 8)))
                .andExpect(jsonPath("$.additionalAmountKrw").value(100_000))
                .andExpect(jsonPath("$.environmentLabel").value("테스트 결제"));
        mockMvc.perform(post("/api/reservation-change-payments/current/checkout").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl").isString());

        UUID attemptId = jdbc.queryForObject("select id from payment_adjustment_attempt", UUID.class);
        settlement.recordGatewayResult(attemptId, "fake-success-once", PaymentAdjustmentGateway.GatewayResultStatus.SUCCEEDED);
        settlement.recordGatewayResult(attemptId, "fake-success-once", PaymentAdjustmentGateway.GatewayResultStatus.SUCCEEDED);
        assertThat(jdbc.queryForObject(
                "select status from reservation_change_request where id = ?", String.class, request.id()))
                .isEqualTo("READY_TO_APPLY");
        assertThat(jdbc.queryForObject("""
                select count(*) from payment_transaction
                where change_request_id = ? and transaction_type = 'CHANGE_CHARGE'
                """, Integer.class, request.id())).isEqualTo(1);
    }

    private ReservationView create(String key, int dayOffset) {
        LocalDate checkIn = LocalDate.now().plusDays(40 + dayOffset);
        return reservations.create(key, TOKEN, new ReservationRequest(
                ROOM_TYPE, RATE_PLAN, checkIn, checkIn.plusDays(2), 2, 0, 1, 200_000,
                new ReservationGuest("정산 고객", key + "@example.com")));
    }

    private List<String> existingTables() {
        return jdbc.queryForList("""
                select table_name from information_schema.tables
                where table_schema = 'public'
                  and (table_name like 'reservation_change_%'
                       or table_name in ('payment_transaction', 'payment_adjustment_attempt'))
                """, String.class);
    }

    private List<String> columns(String table) {
        return jdbc.queryForList("""
                select column_name from information_schema.columns
                where table_schema = 'public' and table_name = ? order by ordinal_position
                """, String.class, table);
    }

    private List<String> constraintDefinitions(String table) {
        return jdbc.queryForList("""
                select pg_get_constraintdef(c.oid)
                from pg_constraint c join pg_class t on t.oid = c.conrelid
                where t.relname = ?
                """, String.class, table);
    }

    private void clean() {
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
