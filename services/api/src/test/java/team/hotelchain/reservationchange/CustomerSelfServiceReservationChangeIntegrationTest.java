package team.hotelchain.reservationchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import team.hotelchain.reservation.ReservationAccess;
import team.hotelchain.reservation.ReservationNotFoundException;

@SpringBootTest(properties = {
        "reservation.change.settlement-enabled=true",
        "reservation.change.gateway=fake"
})
class CustomerSelfServiceReservationChangeIntegrationTest {
    private static final UUID HOTEL = UUID.fromString("91000000-0000-0000-0000-000000000001");
    private static final UUID ROOM = UUID.fromString("92000000-0000-0000-0000-000000000001");
    private static final UUID RATE = UUID.fromString("93000000-0000-0000-0000-000000000001");
    private static final UUID RESERVATION = UUID.fromString("94000000-0000-0000-0000-000000000001");
    private static final String TOKEN = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);

    @Autowired JdbcTemplate jdbc;
    @Autowired ReservationAccess access;
    @Autowired CustomerReservationChangeService service;
    private LocalDate checkIn;

    @BeforeEach void seed() {
        clean();
        checkIn = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(20);
        jdbc.update("insert into hotel values (?,?,?,?)", HOTEL, "고객 변경 호텔", "속초", "Asia/Seoul");
        jdbc.update("insert into room_type values (?,?,?,?)", ROOM, HOTEL, "스탠다드", 4);
        jdbc.update("insert into rate_plan values (?,?,?,false,?)", RATE, ROOM, "룸 온리", "FLEX");
        for (int day = 0; day < 2; day++) {
            jdbc.update("insert into inventory_day values (?,?,2,0,1)", ROOM, checkIn.plusDays(day));
            jdbc.update("insert into rate_day values (?,?,120000)", RATE, checkIn.plusDays(day));
        }
        jdbc.update("""
                insert into reservation
                    (id,room_type_id,rate_plan_id,check_in,check_out,adults,children,rooms,status,
                     total_krw,currency,expires_at,guest_name,guest_email,management_token_hash,policy_snapshot)
                values (?,?,?,?,?,2,0,1,'CONFIRMED',240000,'KRW',now(),'고객','customer@example.com',?,
                        '{"version":"FLEX","timezone":"Asia/Seoul"}'::jsonb)
                """, RESERVATION, ROOM, RATE, checkIn, checkIn.plusDays(2), access.hashToken(TOKEN));
        jdbc.update("insert into reservation_night values (?,?,120000)", RESERVATION, checkIn);
        jdbc.update("insert into reservation_night values (?,?,120000)", RESERVATION, checkIn.plusDays(1));
        jdbc.update("""
                insert into payment_transaction
                    (id,reservation_id,provider,merchant_account,gateway_transaction_id,transaction_type,
                     captured_amount_krw,refunded_amount_krw,currency)
                values (?,?, 'FAKE','LOCAL','customer-original','ORIGINAL_CHARGE',240000,0,'KRW')
                """, UUID.randomUUID(), RESERVATION);
    }

    @AfterEach void tearDown() { clean(); }

    @Test void customerCanQuotePartyChangeAndStartZeroDifferenceSettlement() {
        CustomerReservationChangeQuoteView quote = service.quote(TOKEN, RESERVATION,
                new CustomerReservationChangeQuoteRequest(checkIn, checkIn.plusDays(2), 3, 0));
        var offer = quote.offers().getFirst();
        CustomerReservationChangeService.StartResult result = service.create(TOKEN, RESERVATION, "customer-change-1",
                new CustomerReservationChangeRequest(quote.quoteId(), offer.roomTypeId(), offer.ratePlanId(), offer.total()));

        assertThat(result.view().status()).isEqualTo("READY_TO_APPLY");
        assertThat(jdbc.queryForMap("select request_origin,requested_by,adults from reservation_change_request where id=?",
                result.view().requestId())).containsEntry("request_origin", "CUSTOMER").containsEntry("requested_by", null)
                .containsEntry("adults", 3);
    }

    @Test void wrongManagementTokenCannotReadEligibility() {
        String wrong = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {
                1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1 });
        assertThatThrownBy(() -> service.eligibility(wrong, RESERVATION))
                .isInstanceOf(ReservationNotFoundException.class);
    }

    @Test void additionalAmountStartsPaymentInSameBrowserSession() {
        jdbc.update("update rate_day set amount_krw=130000 where rate_plan_id=?", RATE);
        var quote = service.quote(TOKEN, RESERVATION,
                new CustomerReservationChangeQuoteRequest(checkIn, checkIn.plusDays(2), 3, 0));
        var offer = quote.offers().getFirst();
        var result = service.create(TOKEN, RESERVATION, "customer-charge-1",
                new CustomerReservationChangeRequest(quote.quoteId(), offer.roomTypeId(), offer.ratePlanId(), offer.total()));

        assertThat(result.view().status()).isEqualTo("AWAITING_PAYMENT");
        assertThat(result.sessionToken()).isNotBlank();
        assertThat(jdbc.queryForObject("select count(*) from reservation_change_customer_session where request_id=?",
                Integer.class, result.view().requestId())).isEqualTo(1);
    }

    @Test void lowerAmountStartsRefundWithoutChangingReservationFirst() {
        jdbc.update("update rate_day set amount_krw=110000 where rate_plan_id=?", RATE);
        var quote = service.quote(TOKEN, RESERVATION,
                new CustomerReservationChangeQuoteRequest(checkIn, checkIn.plusDays(2), 3, 0));
        var offer = quote.offers().getFirst();
        var result = service.create(TOKEN, RESERVATION, "customer-refund-1",
                new CustomerReservationChangeRequest(quote.quoteId(), offer.roomTypeId(), offer.ratePlanId(), offer.total()));

        assertThat(result.view().status()).isEqualTo("REFUND_PENDING");
        assertThat(jdbc.queryForObject("select adults from reservation where id=?", Integer.class, RESERVATION)).isEqualTo(2);
    }

    @Test void customerCanCancelUnstartedAdditionalPaymentIdempotently() {
        jdbc.update("update rate_day set amount_krw=130000 where rate_plan_id=?", RATE);
        var quote = service.quote(TOKEN, RESERVATION,
                new CustomerReservationChangeQuoteRequest(checkIn, checkIn.plusDays(2), 3, 0));
        var offer = quote.offers().getFirst();
        var started = service.create(TOKEN, RESERVATION, "customer-cancel-start",
                new CustomerReservationChangeRequest(quote.quoteId(), offer.roomTypeId(), offer.ratePlanId(), offer.total()));

        var first = service.cancel(TOKEN, RESERVATION, started.view().requestId(), "customer-cancel-1");
        var replay = service.cancel(TOKEN, RESERVATION, started.view().requestId(), "customer-cancel-1");

        assertThat(first.status()).isEqualTo("CANCELLED");
        assertThat(replay.status()).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select count(*) from reservation_change_hold_day where request_id=? and status='HELD'",
                Integer.class, started.view().requestId())).isZero();
    }

    private void clean() {
        jdbc.update("delete from reservation_change_customer_session where request_id in (select id from reservation_change_request where reservation_id=?)", RESERVATION);
        jdbc.update("delete from reservation_change_outbox where request_id in (select id from reservation_change_request where reservation_id=?)", RESERVATION);
        jdbc.update("delete from payment_adjustment_attempt where request_id in (select id from reservation_change_request where reservation_id=?)", RESERVATION);
        jdbc.update("delete from reservation_change_hold_day where request_id in (select id from reservation_change_request where reservation_id=?)", RESERVATION);
        jdbc.update("delete from reservation_change_event where request_id in (select id from reservation_change_request where reservation_id=?)", RESERVATION);
        jdbc.update("delete from reservation_change_quote_night where quote_id in (select id from reservation_change_quote where request_id in (select id from reservation_change_request where reservation_id=?))", RESERVATION);
        jdbc.update("delete from reservation_change_approval where request_id in (select id from reservation_change_request where reservation_id=?)", RESERVATION);
        jdbc.update("update reservation_change_request set current_quote_id=null where reservation_id=?", RESERVATION);
        jdbc.update("delete from reservation_change_quote where request_id in (select id from reservation_change_request where reservation_id=?)", RESERVATION);
        jdbc.update("delete from reservation_change_request where reservation_id=?", RESERVATION);
        jdbc.update("delete from reservation_change_customer_quote where reservation_id=?", RESERVATION);
        jdbc.update("delete from payment_transaction where reservation_id=?", RESERVATION);
        jdbc.update("delete from reservation_night where reservation_id=?", RESERVATION);
        jdbc.update("delete from reservation where id=?", RESERVATION);
        jdbc.update("delete from rate_day where rate_plan_id=?", RATE);
        jdbc.update("delete from inventory_day where room_type_id=?", ROOM);
        jdbc.update("delete from rate_plan where id=?", RATE);
        jdbc.update("delete from room_type where id=?", ROOM);
        jdbc.update("delete from hotel where id=?", HOTEL);
    }
}
