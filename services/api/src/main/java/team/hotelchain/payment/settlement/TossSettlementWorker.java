package team.hotelchain.payment.settlement;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import team.hotelchain.payment.settlement.TossSettlementClient.SettlementException;
import team.hotelchain.payment.settlement.TossSettlementClient.SettlementPage;
import team.hotelchain.payment.settlement.TossSettlementClient.SettlementRecord;

@Component
@ConditionalOnExpression("${payment.toss.settlement-enabled:false} and '${payment.provider:fake}' == 'toss-live'")
public class TossSettlementWorker {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final TossSettlementClient client;
    private final Clock clock;
    private final int maxAttempts;

    public TossSettlementWorker(JdbcTemplate jdbc, TransactionTemplate transactions,
            TossSettlementClient client, Clock clock,
            @Value("${payment.toss.settlement-max-attempts:5}") int maxAttempts) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.client = client;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${payment.toss.settlement-scan-delay:1h}",
            initialDelayString = "${payment.toss.settlement-scan-delay:1h}")
    public void processBatch() {
        for (int count = 0; count < 5 && processNext(); count++) { }
    }

    public boolean processNext() {
        Claim claim = transactions.execute(status -> claim());
        if (claim == null) return false;
        try {
            SettlementPage page = client.fetch(claim.soldDate(), claim.page(), claim.pageSize());
            transactions.executeWithoutResult(status -> complete(claim, page));
        } catch (SettlementException exception) {
            transactions.executeWithoutResult(status -> fail(claim, exception.code(), exception.retryable()));
        } catch (RuntimeException exception) {
            transactions.executeWithoutResult(status -> fail(claim, "SETTLEMENT_UNEXPECTED", true));
        }
        return true;
    }

    private Claim claim() {
        Claim candidate = jdbc.query("""
                select id,current_sold_date,current_page,page_size,attempt_count
                from toss_settlement_run
                where (status='PENDING' and next_attempt_at<=?)
                   or (status='PROCESSING' and lease_expires_at<=?)
                order by created_at,id limit 1 for update skip locked
                """, rs -> rs.next() ? new Claim(rs.getObject("id", UUID.class),
                        rs.getObject("current_sold_date", LocalDate.class), rs.getInt("current_page"),
                        rs.getInt("page_size"), rs.getInt("attempt_count") + 1, UUID.randomUUID()) : null,
                Timestamp.from(clock.instant()), Timestamp.from(clock.instant()));
        if (candidate == null) return null;
        jdbc.update("""
                update toss_settlement_run set status='PROCESSING',claim_token=?,lease_expires_at=?,
                    attempt_count=?,error_code=null,updated_at=? where id=?
                """, candidate.token(), Timestamp.from(clock.instant().plusSeconds(90)), candidate.attempt(),
                Timestamp.from(clock.instant()), candidate.id());
        return candidate;
    }

    private void complete(Claim claim, SettlementPage page) {
        if (!owns(claim)) return;
        for (SettlementRecord record : page.records()) insert(claim.id(), record);
        if (page.hasNext()) {
            jdbc.update("""
                    update toss_settlement_run set status='PENDING',current_page=current_page+1,
                        snapshot_count=snapshot_count+?,claim_token=null,lease_expires_at=null,
                        attempt_count=0,next_attempt_at=?,updated_at=? where id=? and claim_token=?
                    """, page.records().size(), Timestamp.from(clock.instant()), Timestamp.from(clock.instant()),
                    claim.id(), claim.token());
            return;
        }
        LocalDate to = jdbc.queryForObject("select sold_date_to from toss_settlement_run where id=?", LocalDate.class, claim.id());
        LocalDate next = claim.soldDate().plusDays(1);
        if (next.isAfter(to)) {
            jdbc.update("""
                    update toss_settlement_run set status='SUCCEEDED',snapshot_count=snapshot_count+?,
                        attempt_count=0,claim_token=null,lease_expires_at=null,completed_at=?,updated_at=?
                    where id=? and claim_token=?
                    """, page.records().size(), Timestamp.from(clock.instant()), Timestamp.from(clock.instant()),
                    claim.id(), claim.token());
        } else {
            jdbc.update("""
                    update toss_settlement_run set status='PENDING',current_sold_date=?,current_page=1,
                        snapshot_count=snapshot_count+?,claim_token=null,lease_expires_at=null,
                        attempt_count=0,next_attempt_at=?,updated_at=? where id=? and claim_token=?
                    """, next, page.records().size(), Timestamp.from(clock.instant()), Timestamp.from(clock.instant()),
                    claim.id(), claim.token());
        }
    }

    private void insert(UUID runId, SettlementRecord record) {
        jdbc.update("""
                insert into toss_settlement_snapshot(id,first_run_id,merchant_account,payment_key,transaction_key,
                    order_id,currency,method,amount_krw,fee_krw,fee_supply_krw,fee_vat_krw,payout_krw,
                    approved_at,sold_date,paid_out_date,cancellation)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                on conflict(merchant_account,payment_key,transaction_key,sold_date) do nothing
                """, UUID.randomUUID(), runId, record.merchantAccount(), record.paymentKey(), record.transactionKey(),
                record.orderId(), record.currency(), record.method(), record.amountKrw(), record.feeKrw(),
                record.feeSupplyKrw(), record.feeVatKrw(), record.payoutKrw(), Timestamp.from(record.approvedAt()),
                record.soldDate(), record.paidOutDate(), record.cancellation());
    }

    private void fail(Claim claim, String code, boolean retryable) {
        if (!owns(claim)) return;
        boolean terminal = !retryable || claim.attempt() >= maxAttempts;
        long delay = Math.min(300, 1L << Math.min(8, claim.attempt()));
        jdbc.update("""
                update toss_settlement_run set status=?,error_code=?,claim_token=null,lease_expires_at=null,
                    next_attempt_at=?,updated_at=? where id=? and claim_token=?
                """, terminal ? "FAILED" : "PENDING", safe(code),
                Timestamp.from(clock.instant().plusSeconds(delay)), Timestamp.from(clock.instant()),
                claim.id(), claim.token());
    }

    private boolean owns(Claim claim) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from toss_settlement_run where id=? and claim_token=? and status='PROCESSING')",
                Boolean.class, claim.id(), claim.token()));
    }

    private String safe(String code) {
        return code != null && code.matches("[A-Z0-9_]{1,80}") ? code : "SETTLEMENT_ERROR";
    }

    private record Claim(UUID id, LocalDate soldDate, int page, int pageSize, int attempt, UUID token) { }
}
