package team.hotelchain.reservationchange;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;
import team.hotelchain.payment.*;
import team.hotelchain.payment.TossPaymentsClient.*;
import team.hotelchain.payment.TossReservationPaymentView.*;
import team.hotelchain.reservation.*;
import team.hotelchain.staff.StaffAccessService;

@SpringBootTest(properties = {"payment.provider=toss-test", "reservation.change.gateway=toss-test",
        "reservation.change.settlement-enabled=true", "reservation.change.outbox-scan-delay=1h",
        "reservation.change.apply-scan-delay=1h", "payment.toss.client-key=test_ck_fixture",
        "payment.toss.secret-key=test_sk_fixture", "payment.toss.merchant-account=hotel-test",
        "payment.toss.customer-origin=http://127.0.0.1:4000", "payment.toss.cancellation-scan-delay=1h"})
@Import(TossPaymentAdjustmentIntegrationTest.ProviderConfiguration.class)
class TossPaymentAdjustmentIntegrationTest {
    static final UUID HOTEL = UUID.fromString("98100000-0000-0000-0000-000000000001");
    static final UUID ROOM = UUID.fromString("98200000-0000-0000-0000-000000000001");
    static final UUID RATE = UUID.fromString("98300000-0000-0000-0000-000000000001");
    static final UUID STAFF = UUID.fromString("98500000-0000-0000-0000-000000000001");
    static final String TOKEN = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
    static final LocalDate DAY = LocalDate.now().plusDays(40);
    @Autowired JdbcTemplate jdbc;
    @Autowired ReservationService reservations;
    @Autowired TossReservationPaymentService payments;
    @Autowired ReservationChangeSettlementService settlements;
    @Autowired ReservationChangeRequestService requests;
    @Autowired ReservationChangeOutboxWorker worker;
    @Autowired ReservationChangeHoldService holds;
    @Autowired ReservationChangeApplyJob applyJob;
    @Autowired TossPaymentAdjustmentGateway gateway;
    @Autowired CancellationService cancellations;
    @Autowired TossCancellationWorker cancellationWorker;
    @Autowired StaffAccessService staff;
    @Autowired StubProvider provider;
    @Autowired WebApplicationContext context;
    String staffToken;

    @BeforeEach void seed() {
        clean();
        provider.cancelCalls.clear();
        provider.confirmCalls = 0;
        provider.failConfirm = false;
        provider.cancelResult = c -> new ProviderPayment(c.paymentKey(), order(c.paymentKey()), c.amountKrw(),
                "KRW", ProviderStatus.DONE, "cancel-" + c.idempotencyKey(), null);
        provider.lookupResult = key -> new ProviderPayment(key, order(key), 100000, "KRW", ProviderStatus.DONE, "charge-event", null);
        jdbc.update("insert into hotel values (?, '토스 정산', '속초', 'Asia/Seoul')", HOTEL);
        jdbc.update("insert into room_type values (?, ?, '객실', 2)", ROOM, HOTEL);
        jdbc.update("insert into rate_plan values (?, ?, '요금', false, 'FLEX')", RATE, ROOM);
        jdbc.update("insert into staff_member(id,email,display_name,password_hash,role) values (?, 'toss-settle@example.com','본사',?,'HQ_ADMIN')",
                STAFF, new BCryptPasswordEncoder().encode("password"));
        for (int i = 0; i < 8; i++) {
            jdbc.update("insert into rate_day values (?, ?, 100000)", RATE, DAY.plusDays(i));
            jdbc.update("insert into inventory_day values (?, ?, 3, 0, 0)", ROOM, DAY.plusDays(i));
        }
        staffToken = staff.login("toss-settle@example.com", "password").token();
    }

    @AfterEach void clean() {
        // This database is exclusively the project's regression database.
        jdbc.execute("delete from toss_webhook_delivery");
        jdbc.execute("delete from toss_webhook_lookup");
        jdbc.execute("truncate table hotel, staff_member cascade");
    }

    @Test void checkoutDoesNotCompleteChangeAndConfirmationUsesStoredAmountOnce() {
        Change change = change(100000);
        var checkout = gateway.checkout(change.session());
        assertThat(checkout.amountKrw()).isEqualTo(100000);
        assertThat(statusOf(change.id())).isEqualTo("AWAITING_PAYMENT");
        assertThat(countCharges()).isZero();
        assertThatThrownBy(() -> gateway.confirm(change.session(), new ConfirmPaymentRequest(checkout.orderId(), "change-key", 1L)))
                .hasMessageContaining("금액");
        int before = provider.confirmCalls;
        gateway.confirm(change.session(), new ConfirmPaymentRequest(checkout.orderId(), "change-key", 100000L));
        gateway.confirm(change.session(), new ConfirmPaymentRequest(checkout.orderId(), "change-key", 100000L));
        assertThat(provider.confirmCalls - before).isEqualTo(1);
        assertThat(statusOf(change.id())).isEqualTo("READY_TO_APPLY");
        assertThat(countCharges()).isEqualTo(1);
        assertThat(jdbc.queryForMap("select provider, merchant_account, gateway_transaction_id from payment_transaction where transaction_type='CHANGE_CHARGE'"))
                .containsEntry("provider", "TOSS_TEST").containsEntry("merchant_account", "hotel-test")
                .containsEntry("gateway_transaction_id", "change-key");
        assertThat(applyJob.claimAndApplyNext()).isTrue();
        assertThat(statusOf(change.id())).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select total_krw from reservation where id=?", Long.class, change.reservation())).isEqualTo(300000);
        assertThat(jdbc.queryForObject("select sum(held) from inventory_day", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select sum(confirmed) from inventory_day", Long.class)).isEqualTo(2);
    }

    @Test void unknownChargeLookupMismatchKeepsHoldAndCannotForgeResult() {
        Change change = change(100000);
        var checkout = gateway.checkout(change.session());
        provider.failConfirm = true;
        gateway.confirm(change.session(), new ConfirmPaymentRequest(checkout.orderId(), "change-key", 100000L));
        provider.lookupResult = key -> new ProviderPayment(key, checkout.orderId(), 1, "KRW", ProviderStatus.DONE, "wrong", null);
        enqueue(change);
        worker.processNext();
        assertThat(statusOf(change.id())).isEqualTo("RECONCILIATION_REQUIRED");
        assertThat(countCharges()).isZero();
        assertThat(held(change.id())).isPositive();
        assertThatThrownBy(() -> settlements.recordGatewayResult(change.attempt(), "forged", PaymentAdjustmentGateway.GatewayResultStatus.SUCCEEDED))
                .isInstanceOf(BusinessConflictException.class);
    }

    @Test void verifiedLookupRecoversUnknownCharge() {
        Change change = change(100000);
        var checkout = gateway.checkout(change.session());
        provider.failConfirm = true;
        gateway.confirm(change.session(), new ConfirmPaymentRequest(checkout.orderId(), "change-key", 100000L));
        enqueue(change);
        worker.processNext();
        assertThat(statusOf(change.id())).isEqualTo("READY_TO_APPLY");
        assertThat(countCharges()).isEqualTo(1);
    }

    @Test void confirmResponseExposesActualChangeStateWhenApprovalIsUnknown() {
        Change change = change(100000);
        var checkout = gateway.checkout(change.session());
        provider.failConfirm = true;
        var result = gateway.confirm(change.session(), new ConfirmPaymentRequest(checkout.orderId(), "change-key", 100000L));
        assertThat(result.status()).isEqualTo("RECONCILIATION_REQUIRED");
        assertThat(result.paymentStatus()).isEqualTo("UNKNOWN");
    }

    @Test void partialRefundUsesOriginalKeyAndCountsOnlyOneVerifiedEvent() {
        Change change = change(-100000);
        worker.processNext();
        enqueue(change);
        worker.processNext();
        assertThat(provider.cancelCalls).hasSize(1);
        assertThat(provider.cancelCalls.getFirst().paymentKey()).isEqualTo("original-key");
        assertThat(provider.cancelCalls.getFirst().amountKrw()).isEqualTo(100000);
        assertThat(refunded()).isEqualTo(100000);
        assertThat(statusOf(change.id())).isEqualTo("READY_TO_APPLY");
    }

    @Test void unknownRefundPreservesHoldThenVerifiedLookupAppliesOnce() {
        Change change = change(-100000);
        provider.cancelResult = c -> new ProviderPayment(null, null, 0, null, ProviderStatus.UNKNOWN, null, "HTTP_IO");
        worker.processNext();
        assertThat(statusOf(change.id())).isEqualTo("RECONCILIATION_REQUIRED");
        assertThat(held(change.id())).isPositive();
        assertThat(refunded()).isZero();
        provider.cancelResult = c -> new ProviderPayment(c.paymentKey(), order(c.paymentKey()), c.amountKrw(), "KRW", ProviderStatus.DONE, "verified-cancel", null);
        enqueue(change);
        worker.processNext();
        assertThat(statusOf(change.id())).isEqualTo("READY_TO_APPLY");
        assertThat(refunded()).isEqualTo(100000);
    }

    @Test void explicitRefundRejectionReleasesOnlyNewHold() {
        Change change = change(-100000);
        provider.cancelResult = c -> new ProviderPayment(null, null, 0, null, ProviderStatus.FAILED, null, "REFUND_REJECTED");
        worker.processNext();
        assertThat(statusOf(change.id())).isEqualTo("CANCELLED");
        assertThat(held(change.id())).isZero();
        assertThat(refunded()).isZero();
        assertThat(jdbc.queryForObject("select status from reservation where id=?", String.class, change.reservation())).isEqualTo("CONFIRMED");
    }

    @Test void providerOrMerchantMismatchIsRejectedBeforeHold() {
        UUID reservation = original();
        jdbc.update("update payment_transaction set merchant_account='other' where reservation_id=?", reservation);
        var request = request(reservation, -100000);
        assertThatThrownBy(() -> settlements.startRefund(staffToken, request.id(), "refund", new ReservationChangeVersionRequest(request.version())))
                .isInstanceOf(BusinessConflictException.class);
        assertThat(held(request.id())).isZero();
        assertThat(provider.cancelCalls).isEmpty();
    }

    @Test void checkoutWithoutApprovalExpiresAndReleasesHold() {
        Change change=change(100000);
        gateway.checkout(change.session());
        jdbc.update("update reservation_change_request set settlement_expires_at=now()-interval '1 minute' where id=?",change.id());
        holds.expireNext();
        assertThat(statusOf(change.id())).isEqualTo("EXPIRED");
        assertThat(held(change.id())).isZero();
    }

    @Test void customerSessionMustStillAuthorizeConfirmation() {
        Change change=change(100000);
        var checkout=gateway.checkout(change.session());
        jdbc.update("update reservation_change_customer_session set expires_at=now()-interval '1 second'");
        int before=provider.confirmCalls;
        assertThatThrownBy(() -> gateway.confirm(change.session(),new ConfirmPaymentRequest(checkout.orderId(),"change-key",100000L)))
                .isInstanceOf(ReservationNotFoundException.class);
        assertThat(provider.confirmCalls).isEqualTo(before);
    }

    @Test void providerSuccessWithInternalFailureRecoversWithoutSecondApproval() {
        Change change=change(100000);
        var checkout=gateway.checkout(change.session());
        jdbc.execute("alter table payment_transaction add constraint task3_reject_change check(transaction_type <> 'CHANGE_CHARGE')");
        try {
            gateway.confirm(change.session(),new ConfirmPaymentRequest(checkout.orderId(),"change-key",100000L));
            assertThat(statusOf(change.id())).isEqualTo("RECONCILIATION_REQUIRED");
        } finally {jdbc.execute("alter table payment_transaction drop constraint task3_reject_change");}
        int before=provider.confirmCalls;
        gateway.confirm(change.session(),new ConfirmPaymentRequest(checkout.orderId(),"change-key",100000L));
        assertThat(provider.confirmCalls).isEqualTo(before);
        assertThat(countCharges()).isEqualTo(1);
        assertThat(statusOf(change.id())).isEqualTo("READY_TO_APPLY");
    }

    @Test void webhookOnlyQueuesKnownOrderAndDeduplicatesWithoutTrustingStatusOrSignature() throws Exception {
        Change change = change(100000);
        var checkout = gateway.checkout(change.session());
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        String body = "{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{\"orderId\":\"" + checkout.orderId() + "\",\"status\":\"DONE\"}}";
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/payments/toss/webhook").contentType(MediaType.APPLICATION_JSON)
                .header("tosspayments-webhook-transmission-id", "delivery-1").content(body)).andExpect(status().isAccepted());
        mvc.perform(post("/api/payments/toss/webhook").contentType(MediaType.APPLICATION_JSON)
                .header("tosspayments-webhook-transmission-id", "delivery-unknown")
                .content("{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{\"orderId\":\"unknown\"}}"))
                .andExpect(status().isAccepted());
        assertThat(jdbc.queryForObject("select count(*) from reservation_change_outbox where command_type='QUERY'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from toss_webhook_delivery", Integer.class)).isEqualTo(1);
        assertThat(countCharges()).isZero();
        assertThat(statusOf(change.id())).isEqualTo("AWAITING_PAYMENT");
    }

    @Test void webhookRejectsMissingTransmissionUnsupportedEventAndOversizedBody() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        String valid = "{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{\"orderId\":\"unknown\"}}";
        mvc.perform(post("/api/payments/toss/webhook").contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/payments/toss/webhook").contentType(MediaType.APPLICATION_JSON)
                .header("tosspayments-webhook-transmission-id", "delivery-event")
                .content("{\"eventType\":\"CANCEL_STATUS_CHANGED\",\"data\":{\"orderId\":\"unknown\"}}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/payments/toss/webhook").contentType(MediaType.APPLICATION_JSON)
                .header("tosspayments-webhook-transmission-id", "delivery-large")
                .content(new byte[65_537])).andExpect(status().isPayloadTooLarge());
    }

    @Test void webhookLookupRecoversKnownNewReservationWithoutCustomerSession() throws Exception {
        var r=reservations.create("new-unknown",TOKEN,new ReservationRequest(ROOM,RATE,DAY,DAY.plusDays(2),2,0,1,200000,new ReservationGuest("고객","toss@example.com")));
        var checkout=payments.checkout(r.id(),TOKEN,"checkout");
        provider.failConfirm=true;
        payments.confirm(r.id(),TOKEN,new ConfirmPaymentRequest(checkout.orderId(),"new-key",200000L));
        provider.lookupResult=key -> new ProviderPayment(key,checkout.orderId(),200000,"KRW",ProviderStatus.DONE,"new-event",null);
        MockMvcBuilders.webAppContextSetup(context).build().perform(post("/api/payments/toss/webhook").contentType(MediaType.APPLICATION_JSON)
                .header("tosspayments-webhook-transmission-id", "delivery-recovery")
                .content("{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{\"orderId\":\""+checkout.orderId()+"\",\"status\":\"FAILED\"}}")).andExpect(status().isAccepted());
        context.getBean(TossPaymentWebhookController.class).processLookups();
        assertThat(payments.status(r.id(),TOKEN).status()).isEqualTo("CONFIRMED");
        assertThat(provider.confirmCalls).isEqualTo(1);
    }

    @Test void wholeCancellationWaitsForEveryRemainingTransaction() {
        UUID reservation = original();
        jdbc.update("insert into payment_transaction(id,reservation_id,provider,merchant_account,gateway_transaction_id,transaction_type,captured_amount_krw,currency) values (?,?,'TOSS_TEST','hotel-test','extra-key','CHANGE_CHARGE',100000,'KRW')", UUID.randomUUID(), reservation);
        jdbc.update("insert into payment_provider_attempt(id,reservation_id,provider,merchant_account,order_id,payment_key,idempotency_key,amount_krw,currency,status) values (?,?,'TOSS_TEST','hotel-test','extra-order','extra-key','extra-idem',100000,'KRW','SUCCEEDED')", UUID.randomUUID(), reservation);
        provider.cancelResult = c -> c.paymentKey().equals("extra-key") ? new ProviderPayment(null,null,0,null,ProviderStatus.UNKNOWN,null,"HTTP_IO")
                : new ProviderPayment(c.paymentKey(), order(c.paymentKey()), c.amountKrw(),"KRW",ProviderStatus.DONE,"cancel-original",null);
        var result = cancellations.cancel(reservation, TOKEN, "cancel-all");
        assertThat(result.status()).isEqualTo("CANCELLATION_PENDING");
        cancellationWorker.processPending();
        assertThat(jdbc.queryForObject("select status from reservation where id=?",String.class,reservation)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("select sum(confirmed) from inventory_day",Long.class)).isEqualTo(2);
        provider.cancelResult = c -> new ProviderPayment(c.paymentKey(), order(c.paymentKey()), c.amountKrw(),"KRW",ProviderStatus.DONE,"cancel-extra",null);
        cancellationWorker.processPending();
        assertThat(cancellations.cancel(reservation, TOKEN, "cancel-all").status()).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select sum(refunded_amount_krw) from payment_transaction",Long.class)).isEqualTo(300000);
        assertThat(provider.cancelCalls.stream().filter(c -> c.paymentKey().equals("original-key")).count()).isEqualTo(1);
    }

    @Test void mixedCancellationCannotUseFakeRefund() {
        UUID reservation = original();
        jdbc.update("insert into payment_transaction(id,reservation_id,provider,merchant_account,gateway_transaction_id,transaction_type,captured_amount_krw,currency) values (?,?,'FAKE','LOCAL','fake-extra','CHANGE_CHARGE',100000,'KRW')", UUID.randomUUID(), reservation);
        assertThatThrownBy(() -> cancellations.cancel(reservation, TOKEN, "cancel-mixed")).isInstanceOf(BusinessConflictException.class);
        assertThat(provider.cancelCalls).isEmpty();
        assertThat(jdbc.queryForObject("select status from reservation where id=?",String.class,reservation)).isEqualTo("CONFIRMED");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void failedCancellationRetriesOnlyFailedCommandWithSameKey(boolean olderThanFifteenDays) {
        UUID reservation = original();
        var change = request(reservation, -100000);
        jdbc.update("insert into payment_transaction(id,reservation_id,provider,merchant_account,gateway_transaction_id,transaction_type,captured_amount_krw,currency) values (?,?,'TOSS_TEST','hotel-test','extra-key','CHANGE_CHARGE',100000,'KRW')", UUID.randomUUID(), reservation);
        jdbc.update("insert into payment_provider_attempt(id,reservation_id,provider,merchant_account,order_id,payment_key,idempotency_key,amount_krw,currency,status) values (?,?,'TOSS_TEST','hotel-test','extra-order','extra-key','extra-idem',100000,'KRW','SUCCEEDED')", UUID.randomUUID(), reservation);
        provider.cancelResult = c -> c.paymentKey().equals("extra-key") ? new ProviderPayment(null,null,0,null,ProviderStatus.FAILED,null,"REFUND_REJECTED")
                : new ProviderPayment(c.paymentKey(), order(c.paymentKey()), c.amountKrw(),"KRW",ProviderStatus.DONE,"cancel-original",null);
        cancellations.cancel(reservation, TOKEN, "cancel-retry");
        cancellationWorker.processPending();
        assertThat(jdbc.queryForObject("select refund_status from cancellation_attempt",String.class)).isEqualTo("FAILED");
        assertThatThrownBy(() -> settlements.startRefund(staffToken,change.id(),"blocked",new ReservationChangeVersionRequest(change.version())))
                .isInstanceOf(BusinessConflictException.class);
        var originalCommands = jdbc.queryForList("select id,idempotency_key from toss_refund_command order by id");
        if (olderThanFifteenDays) jdbc.update("update toss_refund_command set created_at=now()-interval '16 days'");
        provider.cancelResult = c -> new ProviderPayment(c.paymentKey(), order(c.paymentKey()), c.amountKrw(),"KRW",ProviderStatus.DONE,"cancel-extra",null);
        assertThat(cancellations.cancel(reservation,TOKEN,"fresh-client-retry").status()).isEqualTo("CANCELLATION_PENDING");
        assertThat(cancellations.cancel(reservation,TOKEN,"another-pending-key").status()).isEqualTo("CANCELLATION_PENDING");
        cancellationWorker.processPending();
        assertThat(cancellations.cancel(reservation,TOKEN,"fresh-completed-retry").status()).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select count(*) from cancellation_attempt",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("select id,idempotency_key from toss_refund_command order by id")).isEqualTo(originalCommands);
        assertThat(provider.cancelCalls.stream().filter(c -> c.paymentKey().equals("original-key")).count()).isEqualTo(1);
        var extraCalls = provider.cancelCalls.stream().filter(c -> c.paymentKey().equals("extra-key")).toList();
        assertThat(extraCalls).hasSize(olderThanFifteenDays ? 1 : 2);
        if (!olderThanFifteenDays) assertThat(extraCalls.get(1).idempotencyKey()).isEqualTo(extraCalls.getFirst().idempotencyKey());
        assertThat(jdbc.queryForObject("select sum(refunded_amount_krw) from payment_transaction",Long.class)).isEqualTo(300000);
    }

    @Test void freshCancellationRetryKeyCannotChangeStoredActor() {
        UUID reservation = original();
        provider.cancelResult = c -> new ProviderPayment(null,null,0,null,ProviderStatus.FAILED,null,"REFUND_REJECTED");
        cancellations.cancel(reservation,TOKEN,"original-actor");
        cancellationWorker.processPending();
        jdbc.update("update cancellation_attempt set request_hash='different-actor'");
        assertThatThrownBy(() -> cancellations.cancel(reservation,TOKEN,"fresh-actor-key"))
                .isInstanceOf(BusinessConflictException.class);
        assertThat(jdbc.queryForObject("select status from toss_refund_command",String.class)).isEqualTo("FAILED");
    }

    @Test void unknownCancellationBacklogDoesNotStarveLaterCommands() {
        UUID reservation = original();
        for (int i=0; i<21; i++) {
            UUID attempt = UUID.randomUUID(), transaction = UUID.randomUUID(), command = UUID.randomUUID();
            jdbc.update("insert into cancellation_attempt(id,reservation_id,idempotency_key,request_hash,refund_amount_krw,refund_status,reservation_status,created_at) values (?,?,?,'fixture',100,'PENDING','CANCELLATION_PENDING',now()-interval '1 day')",attempt,reservation,"queue-"+i);
            jdbc.update("insert into payment_transaction(id,reservation_id,provider,merchant_account,gateway_transaction_id,transaction_type,captured_amount_krw,currency) values (?,?,'TOSS_TEST','hotel-test',?,'CHANGE_CHARGE',100,'KRW')",transaction,reservation,"queue-key-"+i);
            jdbc.update("insert into toss_refund_command(id,transaction_id,cancellation_attempt_id,merchant_account,payment_key,order_id,amount_krw,idempotency_key,reason,created_at,updated_at) values (?,?,?,'hotel-test',?,?,100,?,'fixture',now()-interval '1 day',now()-interval '1 day')",command,transaction,attempt,"queue-key-"+i,"queue-order-"+i,"queue-refund-"+i);
        }
        provider.cancelResult = c -> new ProviderPayment(null,null,0,null,ProviderStatus.UNKNOWN,null,"HTTP_IO");
        cancellationWorker.processPending();
        cancellationWorker.processPending();
        assertThat(provider.cancelCalls).hasSize(21);
        assertThat(jdbc.queryForObject("select count(*) from toss_refund_command where status='NEW'",Long.class)).isZero();
    }

    @Test void tossModeDoesNotFakeRefundLegacyFakeTransaction() {
        UUID reservation=original();
        jdbc.update("update payment_transaction set provider='FAKE',merchant_account='LOCAL' where reservation_id=?",reservation);
        assertThatThrownBy(() -> cancellations.cancel(reservation,TOKEN,"legacy-fake")).isInstanceOf(BusinessConflictException.class);
        assertThat(jdbc.queryForObject("select status from reservation where id=?",String.class,reservation)).isEqualTo("CONFIRMED");
    }

    @Test void pendingCancellationPreventsNewChangeHold() {
        UUID reservation=original();
        var request=request(reservation,-100000);
        cancellations.cancel(reservation,TOKEN,"cancel-first");
        assertThatThrownBy(() -> settlements.startRefund(staffToken,request.id(),"refund-later",new ReservationChangeVersionRequest(request.version())))
                .isInstanceOf(BusinessConflictException.class);
        assertThat(held(request.id())).isZero();
    }

    UUID original() {
        var r = reservations.create("booking", TOKEN, new ReservationRequest(ROOM,RATE,DAY,DAY.plusDays(2),2,0,1,200000,new ReservationGuest("고객","toss@example.com")));
        var checkout = payments.checkout(r.id(),TOKEN,"original-order");
        payments.confirm(r.id(),TOKEN,new ConfirmPaymentRequest(checkout.orderId(),"original-key",200000L));
        return r.id();
    }
    ReservationChangeRequestView request(UUID r, long difference) {
        jdbc.update("update rate_day set amount_krw=? where rate_plan_id=? and stay_date>=?", (200000+difference)/2, RATE, DAY.plusDays(3));
        return requests.create(staffToken,r,"change",new CreateReservationChangeRequest(DAY.plusDays(3),DAY.plusDays(5),ROOM,RATE,200000+difference));
    }
    Change change(long difference) {
        provider.failConfirm = false;
        UUID r = original();
        var request = request(r,difference);
        String session = null;
        if (difference>0) {
            settlements.createPaymentLink(staffToken, request.id(), "link",new ReservationChangePaymentLinkRequest(request.version(),TOKEN));
            worker.processNext();
            session=settlements.exchangeCustomerToken(TOKEN).token();
        } else settlements.startRefund(staffToken,request.id(),"refund",new ReservationChangeVersionRequest(request.version()));
        UUID attempt=jdbc.queryForObject("select id from payment_adjustment_attempt where request_id=?",UUID.class,request.id());
        return new Change(r,request.id(),attempt,session);
    }
    void enqueue(Change c) {jdbc.update("insert into reservation_change_outbox(id,request_id,attempt_id,command_type,dedupe_key) values (?,?,?,'QUERY',?)",UUID.randomUUID(),c.id(),c.attempt(),UUID.randomUUID().toString());}
    String statusOf(UUID id) {return jdbc.queryForObject("select status from reservation_change_request where id=?",String.class,id);}
    long held(UUID id) {return jdbc.queryForObject("select count(*) from reservation_change_hold_day where request_id=? and hold_kind='NEW_HOLD' and status='HELD'",Long.class,id);}
    long refunded() {return jdbc.queryForObject("select refunded_amount_krw from payment_transaction where transaction_type='ORIGINAL_CHARGE'",Long.class);}
    long countCharges() {return jdbc.queryForObject("select count(*) from payment_transaction where transaction_type='CHANGE_CHARGE'",Long.class);}
    String order(String key) {
        List<String> found=jdbc.queryForList("select order_id from payment_provider_attempt where payment_key=?",String.class,key);
        if(!found.isEmpty()) return found.getFirst();
        return jdbc.queryForObject("select order_id from toss_adjustment_order where payment_key=?",String.class,key);
    }
    record Change(UUID reservation, UUID id, UUID attempt, String session) {}
    @TestConfiguration static class ProviderConfiguration {@Bean @Primary StubProvider provider(){return new StubProvider();}}
    static class StubProvider implements TossPaymentsClient {
        int confirmCalls; boolean failConfirm;
        List<CancelCommand> cancelCalls=new ArrayList<>();
        Function<CancelCommand,ProviderPayment> cancelResult;
        Function<String,ProviderPayment> lookupResult;
        public ProviderPayment confirm(ConfirmCommand c){ assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); confirmCalls++; if(failConfirm)return new ProviderPayment(null,null,0,null,ProviderStatus.UNKNOWN,null,"HTTP_IO"); return new ProviderPayment(c.paymentKey(),c.orderId(),c.amountKrw(),"KRW",ProviderStatus.DONE,"event-"+c.paymentKey(),null);}
        public ProviderPayment lookup(String key){assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); return lookupResult.apply(key);}
        public ProviderPayment cancel(CancelCommand c){assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();cancelCalls.add(c); return cancelResult.apply(c);}
        public ProviderPayment lookupCancel(CancelCommand c){assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); return cancelResult.apply(c);}
    }
}
