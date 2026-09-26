package team.hotelchain.policy;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.reservationchange.ReservationChangePolicy;
import team.hotelchain.staff.StaffAccessService;

/**
 * 본사가 체인 공통 정책을 읽기 전용으로 확인한다.
 * SELECT만 사용하고 정책을 변경하지 않는다.
 */
@Service
public class PolicyQueryService {

    static final int MAX_LIMIT = 100;
    static final int MAX_OFFSET = 1_000;

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;
    private final CurrentPolicy current;
    private final ReservationChangePolicy changePolicy;

    public PolicyQueryService(JdbcTemplate jdbc, StaffAccessService access, CurrentPolicy current,
            ReservationChangePolicy changePolicy) {
        this.jdbc = jdbc;
        this.access = access;
        this.current = current;
        this.changePolicy = changePolicy;
    }

    @Transactional(readOnly = true)
    public PolicyView current(String token) {
        access.requireHeadquarters(token);
        return new PolicyView(current.cancellation(), current.changeApprovalDirectLimitKrw(),
                current.changeApprovalTtlSeconds(), changePolicy.settlementEnabled(), revisionCount());
    }

    // 본사가 정책을 바꾼 이력을 최신순으로 보여준다. 진행 중인 승인·정산 상태는 그대로 둔다.
    @Transactional(readOnly = true)
    public PolicyRevisionsView revisions(String token, Integer limit, Integer offset) {
        access.requireHeadquarters(token);
        int resolvedLimit = limit == null ? 20 : limit;
        int resolvedOffset = offset == null ? 0 : offset;
        validatePaging(resolvedLimit, resolvedOffset);

        Integer total = jdbc.queryForObject("select count(*) from policy_revision", Integer.class);
        int totalCount = total == null ? 0 : total;

        List<PolicyRevisionView> revisions = jdbc.query("""
                select r.key, r.refund_cutoff_days_before, r.refund_cutoff_local_time, r.value_krw,
                       r.value_seconds,
                       s.email as staff_email, s.display_name as staff_display_name, s.role as staff_role,
                       to_char(r.created_at at time zone 'Asia/Seoul', 'YYYY-MM-DD"T"HH24:MI:SS') as created_at
                  from policy_revision r
                  join staff_member s on s.id = r.staff_id
                 order by r.created_at desc, r.id desc
                 limit ? offset ?
                """, this::mapRevision, resolvedLimit, resolvedOffset);

        return new PolicyRevisionsView(revisions, totalCount, resolvedLimit, resolvedOffset);
    }

    private PolicyRevisionView mapRevision(ResultSet rs, int rowNumber) throws SQLException {
        String key = rs.getString("key");
        String summary = switch (key) {
            case CurrentPolicy.CANCELLATION_KEY -> "취소 정책 체크인 " + rs.getInt("refund_cutoff_days_before")
                    + "일 전 " + rs.getString("refund_cutoff_local_time") + " 마감";
            case CurrentPolicy.CHANGE_APPROVAL_KEY ->
                    "예약 변경 승인 한도 " + String.format("%,d", rs.getLong("value_krw")) + "원";
            case CurrentPolicy.CHANGE_APPROVAL_TTL_KEY ->
                    "예약 변경 승인 TTL " + formatSeconds(rs.getInt("value_seconds"));
            default -> key;
        };
        return new PolicyRevisionView(
                key,
                summary,
                rs.getString("staff_email"),
                rs.getString("staff_display_name"),
                rs.getString("staff_role"),
                rs.getString("created_at"));
    }

    private static String formatSeconds(long seconds) {
        if (seconds <= 0) return seconds + "초";
        long days = seconds / 86_400;
        long hours = (seconds % 86_400) / 3_600;
        long minutes = (seconds % 3_600) / 60;
        StringBuilder builder = new StringBuilder();
        if (days > 0) builder.append(days).append("일 ");
        if (hours > 0) builder.append(hours).append("시간 ");
        if (minutes > 0) builder.append(minutes).append("분 ");
        builder.append(String.format("%,d", seconds)).append("초");
        return builder.toString().trim();
    }

    private void validatePaging(int limit, int offset) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("조회 건수는 1 이상 " + MAX_LIMIT + " 이하여야 합니다.");
        }
        if (offset < 0 || offset > MAX_OFFSET) {
            throw new IllegalArgumentException("조회 시작 위치는 0 이상 " + MAX_OFFSET + " 이하여야 합니다.");
        }
    }

    private int revisionCount() {
        Integer count = jdbc.queryForObject(
                "select count(*) from policy_revision where key = ?", Integer.class, CurrentPolicy.CANCELLATION_KEY);
        return count == null ? 0 : count;
    }
}
