package team.hotelchain.policy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 체인 공통 정책의 현재값을 제공한다.
 * <p>
 * {@code policy_revision}의 가장 최근 행을 읽고, 없으면 코드 기본값을 쓴다.
 * 읽기 전용이므로 정책을 변경하지 않는다.
 */
@Component
public class CurrentPolicy {

    static final String CANCELLATION_KEY = "cancellation";
    static final String CHANGE_APPROVAL_KEY = "change-approval";
    static final int DEFAULT_REFUND_CUTOFF_DAYS_BEFORE = 1;
    static final String DEFAULT_REFUND_CUTOFF_LOCAL_TIME = "18:00";
    static final String DEFAULT_TIMEZONE = "Asia/Seoul";
    static final long DEFAULT_CHANGE_APPROVAL_DIRECT_LIMIT_KRW = 100_000L;
    static final long MAX_CHANGE_APPROVAL_DIRECT_LIMIT_KRW = 10_000_000L;

    private final JdbcTemplate jdbc;
    private final int defaultRefundCutoffDaysBefore;
    private final String defaultRefundCutoffLocalTime;
    private final long defaultChangeApprovalDirectLimitKrw;

    public CurrentPolicy(
            JdbcTemplate jdbc,
            @Value("${policy.cancellation.refund-cutoff-days-before:1}") int defaultRefundCutoffDaysBefore,
            @Value("${policy.cancellation.refund-cutoff-local-time:18:00}") String defaultRefundCutoffLocalTime,
            @Value("${reservation.change.direct-limit-krw:100000}") long defaultChangeApprovalDirectLimitKrw) {
        this.jdbc = jdbc;
        this.defaultRefundCutoffDaysBefore = defaultRefundCutoffDaysBefore;
        this.defaultRefundCutoffLocalTime = defaultRefundCutoffLocalTime;
        this.defaultChangeApprovalDirectLimitKrw = defaultChangeApprovalDirectLimitKrw;
    }

    public CancellationPolicy cancellation() {
        return jdbc.query("""
                SELECT refund_cutoff_days_before, refund_cutoff_local_time, timezone
                  FROM policy_revision
                 WHERE key = ?
                 ORDER BY created_at DESC, id DESC
                 LIMIT 1
                """, rs -> rs.next()
                        ? new CancellationPolicy(rs.getInt(1), rs.getString(2), rs.getString(3))
                        : defaults(),
                CANCELLATION_KEY);
    }

    // 지점이 직접 승인할 수 있는 차액 한도. 본사가 변경하지 않았으면 코드 기본값을 쓴다.
    public long changeApprovalDirectLimitKrw() {
        Long value = jdbc.query("""
                SELECT value_krw
                  FROM policy_revision
                 WHERE key = ? AND value_krw IS NOT NULL
                 ORDER BY created_at DESC, id DESC
                 LIMIT 1
                """, rs -> rs.next() ? rs.getLong(1) : null, CHANGE_APPROVAL_KEY);
        return value == null ? defaultChangeApprovalDirectLimitKrw : value;
    }

    private CancellationPolicy defaults() {
        return new CancellationPolicy(defaultRefundCutoffDaysBefore, defaultRefundCutoffLocalTime, DEFAULT_TIMEZONE);
    }
}
