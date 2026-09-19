package team.hotelchain.policy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

/**
 * 본사가 체인 공통 정책을 변경한다.
 * <p>
 * 변경은 항상 새 {@code policy_revision} 행을 append 한다. 기존 revision을 덮어쓰지 않으므로
 * 이미 확정된 예약의 {@code policy_snapshot}은 그대로 남고, 신규 예약부터 새 정책이 적용된다.
 * 같은 요청의 중복 변경은 멱원 키와 요청 지문으로 막는다.
 */
@Service
public class PolicyCommandService {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 100;

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;
    private final CurrentPolicy current;
    private final Clock clock;

    public PolicyCommandService(JdbcTemplate jdbc, StaffAccessService access, CurrentPolicy current, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.current = current;
        this.clock = clock;
    }

    @Transactional
    public CancellationPolicyUpdateResponse updateCancellation(String token, String idempotencyKey,
            CancellationPolicyUpdateRequest request) {
        StaffPrincipal staff = access.requireHeadquarters(token);
        requireIdempotencyKey(idempotencyKey);
        request.validate();

        // 정책은 체인에 하나이므로 키 단위로 쓰기를 직렬화해 동시 변경을 막는다.
        lockKey(CurrentPolicy.CANCELLATION_KEY);

        UUID existing = findExistingRevision(CurrentPolicy.CANCELLATION_KEY, staff.id(), idempotencyKey,
                requestHash(staff.id(), request));
        if (existing != null) {
            return loadExisting(existing);
        }

        UUID revisionId = UUID.randomUUID();
        jdbc.update("""
                insert into policy_revision
                    (id, key, refund_cutoff_days_before, refund_cutoff_local_time, timezone,
                     staff_id, idempotency_key, request_hash, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, revisionId, CurrentPolicy.CANCELLATION_KEY, request.cutoffDays(), request.cutoffLocalTime(),
                CurrentPolicy.DEFAULT_TIMEZONE, staff.id(), idempotencyKey,
                requestHash(staff.id(), request), java.sql.Timestamp.from(clock.instant()));

        return new CancellationPolicyUpdateResponse(
                request.cutoffDays(), request.cutoffLocalTime(), CurrentPolicy.DEFAULT_TIMEZONE, revisionNumber(), true);
    }

    // 본사가 지점 직접 승인 한도를 변경한다. 진행 중인 변경 요청은 저장된 한도를 그대로 쓴다.
    @Transactional
    public ChangeApprovalLimitUpdateResponse updateChangeApprovalLimit(String token, String idempotencyKey,
            ChangeApprovalLimitUpdateRequest request) {
        StaffPrincipal staff = access.requireHeadquarters(token);
        requireIdempotencyKey(idempotencyKey);
        request.validate();

        lockKey(CurrentPolicy.CHANGE_APPROVAL_KEY);

        UUID existing = findExistingRevision(CurrentPolicy.CHANGE_APPROVAL_KEY, staff.id(), idempotencyKey,
                limitRequestHash(staff.id(), request));
        if (existing != null) {
            return loadExistingLimit(existing);
        }

        jdbc.update("""
                insert into policy_revision
                    (id, key, refund_cutoff_days_before, refund_cutoff_local_time, timezone,
                     value_krw, staff_id, idempotency_key, request_hash, created_at)
                values (?, ?, 0, '00:00', ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), CurrentPolicy.CHANGE_APPROVAL_KEY, CurrentPolicy.DEFAULT_TIMEZONE,
                request.limitKrw(), staff.id(), idempotencyKey,
                limitRequestHash(staff.id(), request), java.sql.Timestamp.from(clock.instant()));

        return new ChangeApprovalLimitUpdateResponse(request.limitKrw(), limitRevisionNumber(), true);
    }

    private void lockKey(String key) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0)) is null", rs -> { }, key);
    }

    private UUID findExistingRevision(String key, UUID staffId, String idempotencyKey, String requestHash) {
        // 같은 멱원 키로 이미 처리됐으면 저장된 revision을 그대로 돌려준다.
        UUID byKey = jdbc.query("""
                select id from policy_revision
                 where key = ? and staff_id = ? and idempotency_key = ?
                """, rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
                key, staffId, idempotencyKey);
        if (byKey != null) return byKey;
        // 멱원 키는 달라도 같은 직원의 같은 내용 재시도면 같은 결과를 돌려준다.
        return jdbc.query("""
                select id from policy_revision
                 where key = ? and staff_id = ? and request_hash = ?
                """, rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
                key, staffId, requestHash);
    }

    private CancellationPolicyUpdateResponse loadExisting(UUID revisionId) {
        return jdbc.query("""
                select refund_cutoff_days_before, refund_cutoff_local_time, timezone
                  from policy_revision where id = ?
                """, rs -> rs.next() ? new CancellationPolicyUpdateResponse(
                        rs.getInt(1), rs.getString(2), rs.getString(3), revisionNumber(), false) : null,
                revisionId);
    }

    private ChangeApprovalLimitUpdateResponse loadExistingLimit(UUID revisionId) {
        return jdbc.query("""
                select value_krw from policy_revision where id = ?
                """, rs -> rs.next()
                        ? new ChangeApprovalLimitUpdateResponse(rs.getLong(1), limitRevisionNumber(), false)
                        : null,
                revisionId);
    }

    private int revisionNumber() {
        Integer count = jdbc.queryForObject(
                "select count(*) from policy_revision where key = ?", Integer.class, CurrentPolicy.CANCELLATION_KEY);
        return count == null ? 0 : count;
    }

    private int limitRevisionNumber() {
        Integer count = jdbc.queryForObject(
                "select count(*) from policy_revision where key = ?", Integer.class, CurrentPolicy.CHANGE_APPROVAL_KEY);
        return count == null ? 0 : count;
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("올바른 Idempotency-Key가 필요합니다.");
        }
    }

    static String requestHash(UUID staffId, CancellationPolicyUpdateRequest request) {
        String payload = staffId + "|" + CurrentPolicy.CANCELLATION_KEY + "|"
                + request.cutoffDays() + "|" + request.cutoffLocalTime();
        return sha256(payload);
    }

    static String limitRequestHash(UUID staffId, ChangeApprovalLimitUpdateRequest request) {
        String payload = staffId + "|" + CurrentPolicy.CHANGE_APPROVAL_KEY + "|" + request.limitKrw();
        return sha256(payload);
    }

    private static String sha256(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
