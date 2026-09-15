package team.hotelchain.payment.settlement;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnExpression("${payment.toss.settlement-enabled:false} and '${payment.provider:fake}' == 'toss-live'")
public class TossSettlementReconciliationService {
    private static final ZoneId SETTLEMENT_ZONE = ZoneId.of("Asia/Seoul");
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final int delayDays;

    public TossSettlementReconciliationService(JdbcTemplate jdbc, Clock clock,
            @Value("${payment.toss.settlement-delay-days:2}") int delayDays) {
        if (delayDays < 0 || delayDays > 30) throw new IllegalArgumentException("정산 지연 일수가 올바르지 않습니다.");
        this.jdbc = jdbc;
        this.clock = clock;
        this.delayDays = delayDays;
    }

    @Transactional
    public ReconciliationSummary reconcile(UUID runId, UUID claimToken) {
        List<Map<String, Object>> claimedRuns = jdbc.queryForList("""
                select merchant_account,sold_date_from,sold_date_to from toss_settlement_run
                where id=? and status='PROCESSING' and claim_token=? for update
                """, runId, claimToken);
        if (claimedRuns.size() != 1) throw new IllegalStateException("정산 실행 소유권이 유효하지 않습니다.");
        Map<String, Object> run = claimedRuns.getFirst();
        String merchant = (String) run.get("merchant_account");
        LocalDate from = date(run.get("sold_date_from"));
        LocalDate to = date(run.get("sold_date_to"));
        jdbc.update("delete from toss_settlement_reconciliation where run_id=?", runId);

        List<Map<String, Object>> snapshots = jdbc.queryForList("""
                select * from toss_settlement_snapshot
                where merchant_account=? and sold_date between ? and ? order by sold_date,transaction_key
                """, merchant, from, to);
        for (Map<String, Object> snapshot : snapshots) reconcileSnapshot(runId, snapshot);
        reconcileMissingProvider(runId, merchant, from, to);

        int matched = count(runId, "MATCHED");
        int pending = count(runId, "PENDING");
        int total = jdbc.queryForObject(
                "select count(*) from toss_settlement_reconciliation where run_id=?", Integer.class, runId);
        int mismatch = total - matched - pending;
        jdbc.update("""
                update toss_settlement_run set matched_count=?,mismatch_count=?,pending_count=?,updated_at=? where id=?
                """, matched, mismatch, pending, Timestamp.from(clock.instant()), runId);
        return new ReconciliationSummary(matched, mismatch, pending);
    }

    private void reconcileSnapshot(UUID runId, Map<String, Object> snapshot) {
        boolean cancellation = (boolean) snapshot.get("cancellation");
        List<Map<String, Object>> internal = cancellation ? refund(snapshot) : approval(snapshot);
        String status = "MISSING_INTERNAL";
        String detail = internal.size() > 1 ? "AMBIGUOUS_INTERNAL_EVENT" : "INTERNAL_EVENT_NOT_FOUND";
        UUID transactionId = null;
        UUID refundId = null;
        Long expected = null;
        if (internal.size() == 1) {
            Map<String, Object> row = internal.getFirst();
            transactionId = (UUID) row.get("transaction_id");
            refundId = (UUID) row.get("refund_id");
            expected = ((Number) row.get("expected_amount")).longValue();
            status = classify(((Number) snapshot.get("amount_krw")).longValue(), expected,
                    ((Number) snapshot.get("fee_krw")).longValue(),
                    ((Number) snapshot.get("fee_supply_krw")).longValue(),
                    ((Number) snapshot.get("fee_vat_krw")).longValue(),
                    ((Number) snapshot.get("payout_krw")).longValue());
            detail = status.equals("MATCHED") ? null : status;
        }
        insert(runId, "provider:" + snapshot.get("id"), (UUID) snapshot.get("id"), transactionId, refundId,
                status, expected, ((Number) snapshot.get("amount_krw")).longValue(), snapshot, detail);
    }

    private List<Map<String, Object>> approval(Map<String, Object> snapshot) {
        return jdbc.queryForList("""
                select pt.id transaction_id,null::uuid refund_id,pt.captured_amount_krw expected_amount
                from payment_provider_attempt pa join payment_transaction pt
                  on pt.reservation_id=pa.reservation_id and pt.provider=pa.provider
                 and pt.merchant_account=pa.merchant_account and pt.gateway_transaction_id=pa.payment_key
                where pa.provider='TOSS_LIVE' and pa.status='SUCCEEDED' and pa.merchant_account=?
                  and pa.payment_key=? and pa.provider_event_id=?
                union all
                select pt.id,null::uuid,pt.captured_amount_krw
                from toss_adjustment_order o join payment_adjustment_attempt a on a.id=o.attempt_id
                join payment_transaction pt on pt.change_request_id=a.request_id and pt.provider=a.provider
                where a.provider='TOSS_LIVE' and a.status='SUCCEEDED' and o.merchant_account=?
                  and o.payment_key=? and o.provider_event_id=?
                """, snapshot.get("merchant_account"), snapshot.get("payment_key"), snapshot.get("transaction_key"),
                snapshot.get("merchant_account"), snapshot.get("payment_key"), snapshot.get("transaction_key"));
    }

    private List<Map<String, Object>> refund(Map<String, Object> snapshot) {
        return jdbc.queryForList("""
                select c.transaction_id,c.id refund_id,c.amount_krw expected_amount
                from toss_refund_command c join payment_transaction pt on pt.id=c.transaction_id
                where pt.provider='TOSS_LIVE' and c.status='SUCCEEDED' and c.merchant_account=?
                  and c.payment_key=? and c.provider_event_id=?
                """, snapshot.get("merchant_account"), snapshot.get("payment_key"), snapshot.get("transaction_key"));
    }

    private void reconcileMissingProvider(UUID runId, String merchant, LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select * from (
                  select 'approval:'||pa.id reconciliation_key,pt.id transaction_id,null::uuid refund_id,
                    pa.amount_krw expected_amount,pa.payment_key,pa.provider_event_id transaction_key,pa.updated_at event_at
                  from payment_provider_attempt pa join payment_transaction pt
                    on pt.reservation_id=pa.reservation_id and pt.provider=pa.provider
                   and pt.merchant_account=pa.merchant_account and pt.gateway_transaction_id=pa.payment_key
                  where pa.provider='TOSS_LIVE' and pa.status='SUCCEEDED' and pa.merchant_account=?
                  union all
                  select 'adjustment:'||o.attempt_id,pt.id,null::uuid,o.amount_krw,
                    o.payment_key,o.provider_event_id,o.updated_at
                  from toss_adjustment_order o join payment_adjustment_attempt a on a.id=o.attempt_id
                  join payment_transaction pt on pt.change_request_id=a.request_id and pt.provider=a.provider
                  where a.provider='TOSS_LIVE' and a.status='SUCCEEDED' and o.status='SUCCEEDED'
                    and o.merchant_account=?
                  union all
                  select 'refund:'||c.id,c.transaction_id,c.id,c.amount_krw,c.payment_key,c.provider_event_id,c.updated_at
                  from toss_refund_command c join payment_transaction pt on pt.id=c.transaction_id
                  where pt.provider='TOSS_LIVE' and c.status='SUCCEEDED' and c.merchant_account=?
                ) internal
                where internal.event_at>=? and internal.event_at<?
                  and not exists(select 1 from toss_settlement_snapshot s where s.merchant_account=?
                    and s.payment_key=internal.payment_key and s.transaction_key=internal.transaction_key
                    and s.sold_date between ? and ?)
                """, merchant, merchant, merchant,
                Timestamp.from(from.atStartOfDay(SETTLEMENT_ZONE).toInstant()),
                Timestamp.from(to.plusDays(1).atStartOfDay(SETTLEMENT_ZONE).toInstant()),
                merchant, from, to);
        for (Map<String, Object> row : rows) {
            boolean pending = ((Timestamp) row.get("event_at")).toInstant()
                    .isAfter(clock.instant().minusSeconds(delayDays * 86400L));
            insert(runId, (String) row.get("reconciliation_key"), null,
                    (UUID) row.get("transaction_id"), (UUID) row.get("refund_id"),
                    pending ? "PENDING" : "MISSING_PROVIDER",
                    ((Number) row.get("expected_amount")).longValue(), null, null,
                    pending ? "SETTLEMENT_DELAY_WINDOW" : "PROVIDER_EVENT_NOT_FOUND");
        }
    }

    private void insert(UUID runId, String key, UUID snapshotId, UUID transactionId, UUID refundId,
            String status, Long expected, Long provider, Map<String, Object> snapshot, String detail) {
        jdbc.update("""
                insert into toss_settlement_reconciliation(id,run_id,reconciliation_key,snapshot_id,
                    payment_transaction_id,refund_command_id,status,expected_amount_krw,provider_amount_krw,
                    fee_krw,fee_supply_krw,fee_vat_krw,payout_krw,detail_code)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), runId, key, snapshotId, transactionId, refundId, status, expected, provider,
                value(snapshot, "fee_krw"), value(snapshot, "fee_supply_krw"), value(snapshot, "fee_vat_krw"),
                value(snapshot, "payout_krw"), detail);
    }

    private Long value(Map<String, Object> row, String key) {
        return row == null ? null : ((Number) row.get(key)).longValue();
    }

    private LocalDate date(Object value) {
        return value instanceof java.sql.Date sqlDate ? sqlDate.toLocalDate() : (LocalDate) value;
    }

    private int count(UUID runId, String status) {
        return jdbc.queryForObject(
                "select count(*) from toss_settlement_reconciliation where run_id=? and status=?",
                Integer.class, runId, status);
    }

    static String classify(long providerAmount, long expectedAmount, long fee, long supply, long vat, long payout) {
        if (Math.abs(providerAmount) != expectedAmount) return "AMOUNT_MISMATCH";
        if (fee != supply + vat || payout != providerAmount - fee) return "FEE_MISMATCH";
        return "MATCHED";
    }

    public record ReconciliationSummary(int matched, int mismatched, int pending) { }
}
