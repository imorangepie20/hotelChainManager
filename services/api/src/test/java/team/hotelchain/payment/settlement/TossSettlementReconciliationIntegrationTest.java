package team.hotelchain.payment.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "payment.provider=toss-live", "payment.toss.settlement-enabled=true",
        "payment.toss.settlement-scan-delay=1h", "payment.toss.settlement-delay-days=2",
        "payment.toss.client-key=live_gck_fixture", "payment.toss.secret-key=live_gsk_fixture",
        "payment.toss.merchant-account=hotel-live", "payment.toss.customer-origin=https://hotel.example"
})
@Import(TossSettlementReconciliationIntegrationTest.ClockConfiguration.class)
@Transactional
class TossSettlementReconciliationIntegrationTest {
    private static final String MERCHANT = "hotel-live";
    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 9);
    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");
    private static final UUID HOTEL = UUID.fromString("94400000-0000-0000-0000-000000000001");
    private static final UUID ROOM = UUID.fromString("94400000-0000-0000-0000-000000000002");
    private static final UUID RATE = UUID.fromString("94400000-0000-0000-0000-000000000003");
    private static final UUID STAFF = UUID.fromString("94400000-0000-0000-0000-000000000004");
    private static final UUID CLAIM = UUID.fromString("94400000-0000-0000-0000-000000000005");

    @Autowired JdbcTemplate jdbc;
    @Autowired TossSettlementReconciliationService reconciliation;

    @Test
    void classifies_all_internal_event_types_without_mutating_financial_ledgers() {
        seedFoundation();
        UUID runId = insertRun();

        UUID approval = insertApproval("approval", "pay-approval", "txn-approval", 100_000, FROM);
        insertSnapshot(runId, "pay-approval", "txn-approval", 100_000, 3_000, 2_727, 273, 97_000, false, FROM);

        UUID adjustment = insertAdjustment("adjustment", "pay-adjustment", "txn-adjustment", 40_000, FROM);
        insertSnapshot(runId, "pay-adjustment", "txn-adjustment", 40_000, 1_200, 1_091, 109, 38_800, false, FROM);

        UUID refund = insertRefund("refund", "pay-refund", "txn-refund", 30_000, FROM);
        insertSnapshot(runId, "pay-refund", "approval-txn-refund", 100_000,
                3_000, 2_727, 273, 97_000, false, FROM);
        insertSnapshot(runId, "pay-refund", "txn-refund", 30_000, 0, 0, 0, 30_000, true, FROM);

        insertApproval("amount", "pay-amount", "txn-amount", 50_000, FROM);
        insertSnapshot(runId, "pay-amount", "txn-amount", 49_000, 1_470, 1_336, 134, 47_530, false, FROM);

        insertApproval("fee", "pay-fee", "txn-fee", 60_000, FROM);
        insertSnapshot(runId, "pay-fee", "txn-fee", 60_000, 1_801, 1_636, 164, 58_199, false, FROM);

        insertSnapshot(runId, "pay-provider-only", "txn-provider-only", 70_000,
                2_100, 1_909, 191, 67_900, false, FROM);

        insertAdjustment("old-missing", "pay-old-missing", "txn-old-missing", 80_000, FROM);
        insertAdjustment("recent-pending", "pay-recent-pending", "txn-recent-pending", 90_000, FROM, TO);

        List<Map<String, Object>> transactionsBefore = financialTransactions();
        List<Map<String, Object>> attemptsBefore = providerAttempts();
        List<Map<String, Object>> refundsBefore = refundCommands();

        assertThatThrownBy(() -> reconciliation.reconcile(runId, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
        TossSettlementReconciliationService.ReconciliationSummary summary = reconciliation.reconcile(runId, CLAIM);

        assertThat(statuses(runId)).containsExactlyInAnyOrder(
                "MATCHED", "MATCHED", "MATCHED", "MATCHED", "AMOUNT_MISMATCH", "FEE_MISMATCH",
                "MISSING_INTERNAL", "MISSING_PROVIDER", "PENDING");
        assertThat(summary).isEqualTo(new TossSettlementReconciliationService.ReconciliationSummary(4, 4, 1));
        assertThat(linkedTransaction("provider:" + snapshotId("txn-approval"))).isEqualTo(approval);
        assertThat(linkedTransaction("provider:" + snapshotId("txn-adjustment"))).isEqualTo(adjustment);
        assertThat(linkedRefund("provider:" + snapshotId("txn-refund"))).isEqualTo(refund);
        assertThat(financialTransactions()).isEqualTo(transactionsBefore);
        assertThat(providerAttempts()).isEqualTo(attemptsBefore);
        assertThat(refundCommands()).isEqualTo(refundsBefore);

        List<UUID> resultIds = jdbc.queryForList(
                "select id from toss_settlement_reconciliation where run_id=? order by id", UUID.class, runId);
        jdbc.update("update toss_settlement_run set status='SUCCEEDED',claim_token=null where id=?", runId);
        assertThatThrownBy(() -> reconciliation.reconcile(runId, CLAIM))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForList(
                "select id from toss_settlement_reconciliation where run_id=? order by id", UUID.class, runId))
                .isEqualTo(resultIds);
    }

    @Test
    void uses_korean_settlement_date_at_utc_day_boundary() {
        seedFoundation();
        jdbc.execute("set local time zone 'UTC'");
        UUID runId = insertRun(TO, TO);
        insertAdjustment("kst-boundary", "pay-kst-boundary", "txn-kst-boundary", 90_000, TO);
        Timestamp kstSeptemberNinth = Timestamp.from(Instant.parse("2026-09-08T15:30:00Z"));
        jdbc.update("update toss_adjustment_order set updated_at=? where payment_key=?",
                kstSeptemberNinth, "pay-kst-boundary");
        jdbc.update("update payment_adjustment_attempt set updated_at=? where gateway_transaction_id=?",
                kstSeptemberNinth, "pay-kst-boundary");

        reconciliation.reconcile(runId, CLAIM);

        assertThat(statuses(runId)).containsExactly("PENDING");
    }

    private void seedFoundation() {
        jdbc.update("insert into hotel values (?,?,?,?)", HOTEL, "정산 테스트", "속초", "Asia/Seoul");
        jdbc.update("insert into room_type values (?,?,?,?)", ROOM, HOTEL, "스탠다드", 2);
        jdbc.update("insert into rate_plan values (?,?,?,?,?)", RATE, ROOM, "기본 요금", false, "FLEX-1");
        jdbc.update("insert into staff_member(id,email,display_name,password_hash,role) values (?,?,?,?, 'HQ_ADMIN')",
                STAFF, "reconciliation@example.com", "정산 담당", "unused");
    }

    private UUID insertRun() {
        return insertRun(FROM, TO);
    }

    private UUID insertRun(LocalDate from, LocalDate to) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into toss_settlement_run(id,provider,merchant_account,sold_date_from,sold_date_to,
                    status,current_sold_date,claim_token,lease_expires_at,requested_by,idempotency_key,request_hash)
                values (?,'TOSS_LIVE',?,?,?,'PROCESSING',?,?,?,?, 'reconciliation-test',?)
                """, id, MERCHANT, from, to, from, CLAIM, Timestamp.from(NOW.plusSeconds(90)),
                STAFF, "a".repeat(64));
        return id;
    }

    private UUID insertApproval(String suffix, String paymentKey, String eventKey, long amount, LocalDate createdDate) {
        UUID reservationId = insertReservation(suffix, amount);
        UUID transactionId = UUID.randomUUID();
        jdbc.update("""
                insert into payment_provider_attempt(id,reservation_id,provider,merchant_account,order_id,payment_key,
                    idempotency_key,amount_krw,currency,status,provider_event_id,created_at,updated_at)
                values (?,?,'TOSS_LIVE',?,?,?, ?,?,'KRW','SUCCEEDED',?,?,?)
                """, UUID.randomUUID(), reservationId, MERCHANT, "order-" + suffix, paymentKey,
                "idem-" + suffix, amount, eventKey, at(createdDate), at(createdDate));
        jdbc.update("""
                insert into payment_transaction(id,reservation_id,provider,merchant_account,gateway_transaction_id,
                    transaction_type,captured_amount_krw,refunded_amount_krw,currency,created_at,updated_at)
                values (?,?,'TOSS_LIVE',?,?,'ORIGINAL_CHARGE',?,0,'KRW',?,?)
                """, transactionId, reservationId, MERCHANT, paymentKey, amount, at(createdDate), at(createdDate));
        return transactionId;
    }

    private UUID insertAdjustment(String suffix, String paymentKey, String eventKey, long amount, LocalDate createdDate) {
        return insertAdjustment(suffix, paymentKey, eventKey, amount, createdDate, createdDate);
    }

    private UUID insertAdjustment(String suffix, String paymentKey, String eventKey, long amount,
            LocalDate createdDate, LocalDate succeededDate) {
        UUID reservationId = insertReservation(suffix, amount);
        UUID requestId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        jdbc.update("""
                insert into reservation_change_request(id,reservation_id,hotel_id,base_operation_revision,status,
                    settlement_direction,requested_by,idempotency_key,request_hash,previous_check_in,previous_check_out,
                    previous_room_type_id,previous_rate_plan_id,target_check_in,target_check_out,target_room_type_id,
                    target_rate_plan_id,rooms,adults,children,approval_expires_at,created_at,updated_at)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, requestId, reservationId, HOTEL, 0, "COMPLETED", "CHARGE",
                STAFF, "change-" + suffix, "b".repeat(64),
                FROM, FROM.plusDays(1), ROOM, RATE, FROM, FROM.plusDays(1), ROOM, RATE,
                1, 2, 0, Timestamp.from(NOW.plusSeconds(3600)), at(createdDate), at(createdDate));
        jdbc.update("""
                insert into payment_adjustment_attempt(id,request_id,adjustment_type,provider,idempotency_key,
                    request_hash,amount_krw,currency,status,gateway_transaction_id,provider_event_id,created_at,updated_at)
                values (?,?,'CREATE_CHECKOUT','TOSS_LIVE',?,?,?,'KRW','SUCCEEDED',?,?,?,?)
                """, attemptId, requestId, "adjust-" + suffix, "c".repeat(64), amount,
                paymentKey, eventKey, at(createdDate), at(succeededDate));
        jdbc.update("""
                insert into toss_adjustment_order(attempt_id,reservation_id,merchant_account,order_id,payment_key,
                    idempotency_key,amount_krw,status,provider_event_id,created_at,updated_at)
                values (?,?,?,?,?,?,?,'SUCCEEDED',?,?,?)
                """, attemptId, reservationId, MERCHANT, "adjust-order-" + suffix, paymentKey,
                "adjust-order-idem-" + suffix, amount, eventKey, at(createdDate), at(succeededDate));
        jdbc.update("""
                insert into payment_transaction(id,reservation_id,change_request_id,provider,merchant_account,
                    gateway_transaction_id,transaction_type,captured_amount_krw,refunded_amount_krw,currency,created_at,updated_at)
                values (?,?,?,'TOSS_LIVE',?,?,'CHANGE_CHARGE',?,0,'KRW',?,?)
                """, transactionId, reservationId, requestId, MERCHANT, paymentKey, amount,
                at(createdDate), at(createdDate));
        return transactionId;
    }

    private UUID insertRefund(String suffix, String paymentKey, String eventKey, long amount, LocalDate createdDate) {
        UUID transactionId = insertApproval(suffix, paymentKey, "approval-" + eventKey, 100_000, createdDate);
        UUID reservationId = jdbc.queryForObject(
                "select reservation_id from payment_transaction where id=?", UUID.class, transactionId);
        UUID cancellationId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        jdbc.update("""
                insert into cancellation_attempt(id,reservation_id,idempotency_key,request_hash,refund_amount_krw,
                    refund_status,reservation_status,created_at)
                values (?,?,?,?,?,'SUCCEEDED','CANCELLED',?)
                """, cancellationId, reservationId, "cancel-" + suffix, "d".repeat(64), amount, at(createdDate));
        jdbc.update("""
                insert into toss_refund_command(id,transaction_id,cancellation_attempt_id,merchant_account,payment_key,
                    order_id,amount_krw,idempotency_key,reason,status,provider_event_id,created_at,updated_at)
                values (?,?,?,?,?,?,?,?,?,'SUCCEEDED',?,?,?)
                """, refundId, transactionId, cancellationId, MERCHANT, paymentKey, "refund-order-" + suffix,
                amount, "refund-" + suffix, "고객 취소", eventKey, at(createdDate), at(createdDate));
        return refundId;
    }

    private UUID insertReservation(String suffix, long amount) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into reservation(id,room_type_id,rate_plan_id,check_in,check_out,adults,children,rooms,status,
                    total_krw,currency,expires_at,guest_name,guest_email,management_token_hash,policy_snapshot,created_at)
                values (?,?,?,?,?,2,0,1,'CONFIRMED',?,'KRW',?,?,?,?,'{}'::jsonb,?)
                """, id, ROOM, RATE, FROM, FROM.plusDays(1), amount, Timestamp.from(NOW.plusSeconds(3600)),
                "테스트 고객", suffix + "@example.com", hash(suffix), at(FROM));
        return id;
    }

    private void insertSnapshot(UUID runId, String paymentKey, String transactionKey, long amount,
            long fee, long supply, long vat, long payout, boolean cancellation, LocalDate soldDate) {
        jdbc.update("""
                insert into toss_settlement_snapshot(id,first_run_id,merchant_account,payment_key,transaction_key,
                    order_id,currency,method,amount_krw,fee_krw,fee_supply_krw,fee_vat_krw,payout_krw,
                    approved_at,sold_date,paid_out_date,cancellation)
                values (?,?,?,?,?,?,'KRW','카드',?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), runId, MERCHANT, paymentKey, transactionKey, "snapshot-" + transactionKey,
                amount, fee, supply, vat, payout, at(soldDate), soldDate, soldDate.plusDays(7), cancellation);
    }

    private List<String> statuses(UUID runId) {
        return jdbc.queryForList(
                "select status from toss_settlement_reconciliation where run_id=?", String.class, runId);
    }

    private UUID snapshotId(String transactionKey) {
        return jdbc.queryForObject(
                "select id from toss_settlement_snapshot where transaction_key=?", UUID.class, transactionKey);
    }

    private UUID linkedTransaction(String key) {
        return jdbc.queryForObject("select payment_transaction_id from toss_settlement_reconciliation where reconciliation_key=?",
                UUID.class, key);
    }

    private UUID linkedRefund(String key) {
        return jdbc.queryForObject("select refund_command_id from toss_settlement_reconciliation where reconciliation_key=?",
                UUID.class, key);
    }

    private List<Map<String, Object>> financialTransactions() {
        return jdbc.queryForList("""
                select id,reservation_id,change_request_id,provider,merchant_account,gateway_transaction_id,
                    transaction_type,captured_amount_krw,refunded_amount_krw,currency,created_at,updated_at
                from payment_transaction where merchant_account=? order by id
                """, MERCHANT);
    }

    private List<Map<String, Object>> providerAttempts() {
        return jdbc.queryForList("""
                select id,reservation_id,status,payment_key,provider_event_id,amount_krw,created_at,updated_at
                from payment_provider_attempt where merchant_account=? order by id
                """, MERCHANT);
    }

    private List<Map<String, Object>> refundCommands() {
        return jdbc.queryForList("""
                select id,transaction_id,status,payment_key,provider_event_id,amount_krw,created_at,updated_at
                from toss_refund_command where merchant_account=? order by id
                """, MERCHANT);
    }

    private Timestamp at(LocalDate day) {
        return Timestamp.from(day.atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    private String hash(String value) {
        return String.format("%64s", value).replace(' ', '0').substring(0, 64);
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean @Primary Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
