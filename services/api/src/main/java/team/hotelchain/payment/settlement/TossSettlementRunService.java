package team.hotelchain.payment.settlement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import team.hotelchain.payment.TossPaymentEnvironment;
import team.hotelchain.payment.TossPaymentsProperties;

@Service
@ConditionalOnExpression("${payment.toss.settlement-enabled:false} and '${payment.provider:fake}' != 'fake'")
public class TossSettlementRunService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final String merchantAccount;
    private final String providerCode;
    private final int pageSize;

    public TossSettlementRunService(JdbcTemplate jdbc, Clock clock, TossPaymentsProperties properties,
            @Value("${payment.provider:fake}") String providerMode,
            @Value("${payment.toss.settlement-page-size:500}") int pageSize) {
        if (pageSize < 1 || pageSize > 5000) throw new IllegalArgumentException("정산 페이지 크기가 올바르지 않습니다.");
        this.jdbc = jdbc;
        this.clock = clock;
        this.merchantAccount = properties.merchantAccount();
        this.providerCode = TossPaymentEnvironment.from(providerMode).providerCode();
        this.pageSize = pageSize;
    }

    public UUID create(LocalDate from, LocalDate to, UUID requestedBy) {
        validate(from, to, requestedBy);
        UUID id = UUID.randomUUID();
        String key = "auto-" + id;
        jdbc.update("""
                insert into toss_settlement_run(id,provider,merchant_account,sold_date_from,sold_date_to,
                    current_sold_date,page_size,requested_by,idempotency_key,request_hash)
                values (?,?,?,?,?,?,?,?,?,?)
                """, id, providerCode, merchantAccount, from, to, from, pageSize, requestedBy, key, hash(from + ":" + to));
        return id;
    }

    /**
     * 실패한 정산 실행을 worker가 다시 집을 수 있도록 대기 상태로 되돌린다.
     * 기존 snapshot·대사 결과는 유지하고 시도 횟수와 다음 실행 시각만 초기화한다.
     */
    public void retry(UUID runId) {
        if (runId == null) throw new IllegalArgumentException("정산 실행 ID가 필요합니다.");
        String status = jdbc.query("""
                select status from toss_settlement_run where id=?
                """, rs -> rs.next() ? rs.getString("status") : null, runId);
        if (status == null) throw new SettlementRunNotFoundException(runId);
        if (!"FAILED".equals(status)) throw new SettlementNotRetryableException(status);
        jdbc.update("""
                update toss_settlement_run set status='PENDING',attempt_count=0,error_code=null,
                    claim_token=null,lease_expires_at=null,next_attempt_at=?,updated_at=?
                where id=? and status='FAILED'
                """, Timestamp.from(clock.instant()), Timestamp.from(clock.instant()), runId);
    }

    private void validate(LocalDate from, LocalDate to, UUID requestedBy) {
        if (from == null || to == null || requestedBy == null || from.isAfter(to)
                || to.isAfter(LocalDate.now(clock)) || ChronoUnit.DAYS.between(from, to) >= 31) {
            throw new IllegalArgumentException("정산 조회 기간은 과거 31일 이내여야 합니다.");
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
