package team.hotelchain.payment.settlement;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 본사 읽기 전용 정산·대사 조회. 테이블을 변경하지 않고 이미 저장된 snapshot·대사 결과만 제공한다.
 * 결제·정산·재고에 영향을 주는 동작은 이 클래스에 없다.
 * worker·대사 서비스와 같은 조건으로 토스 결제 환경(fake 이외)에서만 로드된다.
 */
@Service
@ConditionalOnExpression("${payment.toss.settlement-enabled:false} and '${payment.provider:fake}' != 'fake'")
public class TossSettlementQueryService {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public TossSettlementQueryService(JdbcTemplate jdbc, Clock clock,
            @Value("${payment.toss.settlement-delay-days:2}") int delayDays) {
        if (delayDays < 0 || delayDays > 30) throw new IllegalArgumentException("정산 지연 일수가 올바르지 않습니다.");
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public SettlementRunsView runs(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<SettlementRunSummary> runs = jdbc.query("""
                select id,provider,merchant_account,sold_date_from,sold_date_to,status,
                    current_sold_date,current_page,page_size,snapshot_count,matched_count,
                    mismatch_count,pending_count,attempt_count,next_attempt_at,error_code,
                    created_at,updated_at,completed_at
                from toss_settlement_run
                order by created_at desc,id desc
                limit ?
                """, (resultSet, row) -> new SettlementRunSummary(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("provider"),
                        resultSet.getString("merchant_account"),
                        resultSet.getObject("sold_date_from", LocalDate.class),
                        resultSet.getObject("sold_date_to", LocalDate.class),
                        resultSet.getString("status"),
                        resultSet.getObject("current_sold_date", LocalDate.class),
                        resultSet.getInt("current_page"),
                        resultSet.getInt("page_size"),
                        resultSet.getInt("snapshot_count"),
                        resultSet.getInt("matched_count"),
                        resultSet.getInt("mismatch_count"),
                        resultSet.getInt("pending_count"),
                        resultSet.getInt("attempt_count"),
                        resultSet.getTimestamp("next_attempt_at"),
                        resultSet.getString("error_code"),
                        resultSet.getTimestamp("created_at"),
                        resultSet.getTimestamp("updated_at"),
                        resultSet.getTimestamp("completed_at")),
                safeLimit);
        return new SettlementRunsView(runs, clock.instant());
    }

    public SettlementRunDetailView run(UUID runId, String status, int limit) {
        if (runId == null) throw new IllegalArgumentException("정산 실행 ID가 필요합니다.");
        SettlementRunSummary summary = jdbc.query("""
                select id,provider,merchant_account,sold_date_from,sold_date_to,status,
                    current_sold_date,current_page,page_size,snapshot_count,matched_count,
                    mismatch_count,pending_count,attempt_count,next_attempt_at,error_code,
                    created_at,updated_at,completed_at
                from toss_settlement_run
                where id=?
                """, resultSet -> resultSet.next() ? new SettlementRunSummary(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("provider"),
                        resultSet.getString("merchant_account"),
                        resultSet.getObject("sold_date_from", LocalDate.class),
                        resultSet.getObject("sold_date_to", LocalDate.class),
                        resultSet.getString("status"),
                        resultSet.getObject("current_sold_date", LocalDate.class),
                        resultSet.getInt("current_page"),
                        resultSet.getInt("page_size"),
                        resultSet.getInt("snapshot_count"),
                        resultSet.getInt("matched_count"),
                        resultSet.getInt("mismatch_count"),
                        resultSet.getInt("pending_count"),
                        resultSet.getInt("attempt_count"),
                        resultSet.getTimestamp("next_attempt_at"),
                        resultSet.getString("error_code"),
                        resultSet.getTimestamp("created_at"),
                        resultSet.getTimestamp("updated_at"),
                        resultSet.getTimestamp("completed_at")) : null,
                runId);
        if (summary == null) return new SettlementRunDetailView(null, List.of(), clock.instant());

        int safeLimit = Math.max(1, Math.min(limit, 500));
        String filter = normalizeStatus(status);
        List<ReconciliationRow> rows;
        if (filter == null) {
            rows = jdbc.query(Rows.SELECT + Rows.WHERE_RUN + Rows.ORDER + Rows.LIMIT,
                    Rows.reader(), runId, safeLimit);
        } else {
            rows = jdbc.query(Rows.SELECT + Rows.WHERE_RUN_STATUS + Rows.ORDER + Rows.LIMIT,
                    Rows.reader(), runId, filter, safeLimit);
        }
        return new SettlementRunDetailView(summary, rows, clock.instant());
    }

    private String normalizeStatus(String status) {
        if (status == null) return null;
        String trimmed = status.trim();
        return java.util.Set.of(
                "MATCHED", "AMOUNT_MISMATCH", "FEE_MISMATCH",
                "MISSING_INTERNAL", "MISSING_PROVIDER", "PENDING").contains(trimmed)
                ? trimmed : null;
    }

    public record SettlementRunsView(List<SettlementRunSummary> runs, java.time.Instant serverAt) {
        public SettlementRunsView {
            runs = List.copyOf(runs);
        }
    }

    public record SettlementRunDetailView(SettlementRunSummary run, List<ReconciliationRow> rows,
            java.time.Instant serverAt) {
        public SettlementRunDetailView {
            rows = List.copyOf(rows);
        }
    }

    public record SettlementRunSummary(
            UUID id,
            String provider,
            String merchantAccount,
            LocalDate soldDateFrom,
            LocalDate soldDateTo,
            String status,
            LocalDate currentSoldDate,
            int currentPage,
            int pageSize,
            int snapshotCount,
            int matchedCount,
            int mismatchCount,
            int pendingCount,
            int attemptCount,
            java.sql.Timestamp nextAttemptAt,
            String errorCode,
            java.sql.Timestamp createdAt,
            java.sql.Timestamp updatedAt,
            java.sql.Timestamp completedAt) {
    }

    public record ReconciliationRow(
            UUID id,
            UUID runId,
            String reconciliationKey,
            UUID snapshotId,
            UUID paymentTransactionId,
            UUID refundCommandId,
            String status,
            Long expectedAmountKrw,
            Long providerAmountKrw,
            Long feeKrw,
            Long feeSupplyKrw,
            Long feeVatKrw,
            Long payoutKrw,
            String detailCode,
            String snapshotOrderId,
            String snapshotPaymentKey,
            String snapshotTransactionKey,
            String snapshotMethod,
            Boolean snapshotCancellation,
            LocalDate snapshotSoldDate,
            java.sql.Timestamp createdAt) {
    }

    private static final class Rows {
        private static final String SELECT = """
                select r.id,r.run_id,r.reconciliation_key,r.snapshot_id,r.payment_transaction_id,r.refund_command_id,
                    r.status,r.expected_amount_krw,r.provider_amount_krw,r.fee_krw,r.fee_supply_krw,r.fee_vat_krw,
                    r.payout_krw,r.detail_code,r.created_at,
                    s.order_id snapshot_order_id,s.payment_key snapshot_payment_key,
                    s.transaction_key snapshot_transaction_key,s.method snapshot_method,
                    s.cancellation snapshot_cancellation,s.sold_date snapshot_sold_date
                from toss_settlement_reconciliation r
                left join toss_settlement_snapshot s on s.id=r.snapshot_id
                """;
        private static final String WHERE_RUN = " where r.run_id=?";
        private static final String WHERE_RUN_STATUS = " where r.run_id=? and r.status=?";
        private static final String ORDER = " order by r.created_at desc,r.id desc";
        private static final String LIMIT = " limit ?";

        private Rows() {
        }

        private static org.springframework.jdbc.core.RowMapper<ReconciliationRow> reader() {
            return (resultSet, row) -> new ReconciliationRow(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("run_id", UUID.class),
                    resultSet.getString("reconciliation_key"),
                    resultSet.getObject("snapshot_id", UUID.class),
                    resultSet.getObject("payment_transaction_id", UUID.class),
                    resultSet.getObject("refund_command_id", UUID.class),
                    resultSet.getString("status"),
                    getNullableLong(resultSet, "expected_amount_krw"),
                    getNullableLong(resultSet, "provider_amount_krw"),
                    getNullableLong(resultSet, "fee_krw"),
                    getNullableLong(resultSet, "fee_supply_krw"),
                    getNullableLong(resultSet, "fee_vat_krw"),
                    getNullableLong(resultSet, "payout_krw"),
                    resultSet.getString("detail_code"),
                    resultSet.getString("snapshot_order_id"),
                    resultSet.getString("snapshot_payment_key"),
                    resultSet.getString("snapshot_transaction_key"),
                    resultSet.getString("snapshot_method"),
                    getNullableBoolean(resultSet, "snapshot_cancellation"),
                    resultSet.getObject("snapshot_sold_date", LocalDate.class),
                    resultSet.getTimestamp("created_at"));
        }

        private static Long getNullableLong(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
            long value = resultSet.getLong(column);
            return resultSet.wasNull() ? null : value;
        }

        private static Boolean getNullableBoolean(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
            boolean value = resultSet.getBoolean(column);
            return resultSet.wasNull() ? null : value;
        }
    }
}
