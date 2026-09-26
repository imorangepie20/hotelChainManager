package team.hotelchain.policy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.List;
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
    private final CancellationPolicyResolver cancellationPolicies;
    private final Clock clock;

    public PolicyCommandService(JdbcTemplate jdbc, StaffAccessService access, CurrentPolicy current,
            CancellationPolicyResolver cancellationPolicies, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.current = current;
        this.cancellationPolicies = cancellationPolicies;
        this.clock = clock;
    }

    @Transactional
    public CancellationPolicyUpdateResponse updateCancellation(String token, String idempotencyKey,
            CancellationPolicyUpdateRequest request) {
        StaffPrincipal staff = access.requireHeadquarters(token);
        requireIdempotencyKey(idempotencyKey);
        request.validate();
        ScopedPolicyResult result = updateScopedCancellation(
                staff, null, "SET", idempotencyKey, request.canonicalRules());
        if (result.created() && request.legacyRequest()) {
            UUID legacyRevisionId = insertLegacyCancellation(staff, idempotencyKey, request);
            jdbc.update("update cancellation_policy_revision set legacy_revision_id = ? where id = ?",
                    legacyRevisionId, result.id());
        }
        return response(result, null, request.legacyRequest() ? revisionNumber() : result.revision());
    }

    @Transactional
    public CancellationPolicyUpdateResponse updateHotelCancellation(
            String token, UUID hotelId, String idempotencyKey, CancellationPolicyUpdateRequest request) {
        StaffPrincipal staff = access.requireHeadquarters(token);
        requireIdempotencyKey(idempotencyKey);
        request.validate();
        cancellationPolicies.resolveForHotel(hotelId);
        ScopedPolicyResult result = updateScopedCancellation(
                staff, hotelId, "SET", idempotencyKey, request.canonicalRules());
        return response(result, hotelId, result.revision());
    }

    @Transactional
    public CancellationPolicyUpdateResponse inheritHotelCancellation(
            String token, UUID hotelId, String idempotencyKey) {
        StaffPrincipal staff = access.requireHeadquarters(token);
        requireIdempotencyKey(idempotencyKey);
        cancellationPolicies.resolveForHotel(hotelId);
        ScopedPolicyResult result = updateScopedCancellation(
                staff, hotelId, "INHERIT", idempotencyKey, List.of());
        return response(result, hotelId, result.revision());
    }

    private ScopedPolicyResult updateScopedCancellation(
            StaffPrincipal staff, UUID hotelId, String action, String idempotencyKey,
            List<CancellationRefundRule> rules) {
        String scopeKey = hotelId == null ? "cancellation:CHAIN" : "cancellation:HOTEL:" + hotelId;
        lockKey(scopeKey);
        String hash = scopedRequestHash(hotelId, action, rules);
        ScopedRevision existing = findScopedByIdempotencyKey(hotelId, idempotencyKey);
        if (existing != null) {
            if (!existing.requestHash().equals(hash)) throw new PolicyIdempotencyConflictException();
            return new ScopedPolicyResult(
                    existing.id(), existing.revision(), false, existing.action(), existing.rules());
        }

        CancellationPolicyRevisionView latest = hotelId == null
                ? latestChainRevision() : cancellationPolicies.latestHotelRevision(hotelId);
        ResolvedCancellationPolicy chainEffective = hotelId == null
                ? cancellationPolicies.resolveChain() : null;
        boolean sameSet = "SET".equals(action)
                && ((hotelId == null && chainEffective.rules().equals(rules))
                    || (hotelId != null && latest != null && "SET".equals(latest.action())
                        && latest.rules().equals(rules)));
        if (sameSet) {
            UUID revisionId = hotelId == null ? chainEffective.revisionId() : latest.revisionId();
            int revision = latest == null ? 0 : latest.revision();
            saveNoopReceipt(hotelId, idempotencyKey, hash, revisionId, revision, action, rules);
            return new ScopedPolicyResult(revisionId, revision, false, action, rules);
        }
        if (hotelId != null && "INHERIT".equals(action)
                && (latest == null || "INHERIT".equals(latest.action()))) {
            ResolvedCancellationPolicy effective = cancellationPolicies.resolveForHotel(hotelId);
            int revision = latest == null ? 0 : latest.revision();
            saveNoopReceipt(hotelId, idempotencyKey, hash, effective.revisionId(), revision, action, List.of());
            return new ScopedPolicyResult(effective.revisionId(), revision, false, action, List.of());
        }

        int revision = latest == null ? 1 : latest.revision() + 1;
        UUID revisionId = UUID.randomUUID();
        jdbc.update("""
                insert into cancellation_policy_revision
                    (id, hotel_id, action, revision_number, staff_id, idempotency_key, request_hash, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, revisionId, hotelId, action, revision, staff.id(), idempotencyKey, hash,
                java.sql.Timestamp.from(clock.instant()));
        for (int index = 0; index < rules.size(); index++) {
            CancellationRefundRule rule = rules.get(index);
            jdbc.update("""
                    insert into cancellation_refund_rule
                        (revision_id, rule_order, days_before, cutoff_local_time, refund_percent)
                    values (?, ?, ?, ?, ?)
                    """, revisionId, index, rule.daysBefore(), rule.cutoffLocalTime(), rule.refundPercent());
        }
        return new ScopedPolicyResult(revisionId, revision, true, action, rules);
    }

    private CancellationPolicyUpdateResponse response(
            ScopedPolicyResult result, UUID hotelId, int responseRevision) {
        if ("SET".equals(result.action())) {
            String timezone = hotelId == null
                    ? CurrentPolicy.DEFAULT_TIMEZONE
                    : jdbc.queryForObject("select timezone from hotel where id = ?", String.class, hotelId);
            ResolvedCancellationPolicy saved = new ResolvedCancellationPolicy(
                    result.id(), hotelId == null ? "CHAIN" : "HOTEL", hotelId,
                    timezone, result.rules(), false);
            return CancellationPolicyUpdateResponse.from(saved, responseRevision, result.created());
        }
        ResolvedCancellationPolicy policy = hotelId == null
                ? cancellationPolicies.resolveChain() : cancellationPolicies.resolveForHotel(hotelId);
        return CancellationPolicyUpdateResponse.from(policy, responseRevision, result.created());
    }

    private CancellationPolicyRevisionView latestChainRevision() {
        return jdbc.query("""
                select id, action, revision_number
                 from cancellation_policy_revision
                 where hotel_id is null
                 order by revision_number desc limit 1
                """, rs -> {
            if (!rs.next()) return null;
            UUID id = rs.getObject("id", UUID.class);
            return new CancellationPolicyRevisionView(id, null, rs.getString("action"),
                    rs.getInt("revision_number"), scopedRules(id));
        });
    }

    private List<CancellationRefundRule> scopedRules(UUID revisionId) {
        return jdbc.query("""
                select days_before, cutoff_local_time, refund_percent
                  from cancellation_refund_rule where revision_id = ? order by rule_order
                """, (rs, rowNumber) -> new CancellationRefundRule(
                rs.getInt(1), rs.getString(2), rs.getInt(3)), revisionId);
    }

    private ScopedRevision findScopedByIdempotencyKey(UUID hotelId, String idempotencyKey) {
        String sql = hotelId == null
                ? "select id, action, revision_number, request_hash from cancellation_policy_revision where hotel_id is null and idempotency_key = ?"
                : "select id, action, revision_number, request_hash from cancellation_policy_revision where hotel_id = ? and idempotency_key = ?";
        Object[] args = hotelId == null ? new Object[] { idempotencyKey } : new Object[] { hotelId, idempotencyKey };
        ScopedRevision revision = jdbc.query(sql, rs -> rs.next() ? new ScopedRevision(
                rs.getObject("id", UUID.class), rs.getString("action"),
                rs.getInt("revision_number"), rs.getString("request_hash"), List.of()) : null, args);
        if (revision != null) return new ScopedRevision(
                revision.id(), revision.action(), revision.revision(), revision.requestHash(),
                "SET".equals(revision.action()) ? scopedRules(revision.id()) : List.of());
        String receiptSql = hotelId == null
                ? "select revision_id, action, revision_number, request_hash from cancellation_policy_noop_command where hotel_id is null and idempotency_key = ?"
                : "select revision_id, action, revision_number, request_hash from cancellation_policy_noop_command where hotel_id = ? and idempotency_key = ?";
        ScopedRevision receipt = jdbc.query(receiptSql, rs -> rs.next() ? new ScopedRevision(
                rs.getObject("revision_id", UUID.class), rs.getString("action"),
                rs.getInt("revision_number"), rs.getString("request_hash"), List.of()) : null, args);
        if (receipt != null) {
            return new ScopedRevision(receipt.id(), receipt.action(), receipt.revision(), receipt.requestHash(),
                    receiptRules(hotelId, idempotencyKey));
        }
        if (hotelId != null) return null;
        return jdbc.query("""
                select id, refund_cutoff_days_before, refund_cutoff_local_time
                  from policy_revision
                 where key = ? and idempotency_key = ?
                """, rs -> {
            if (!rs.next()) return null;
            List<CancellationRefundRule> legacyRules = List.of(new CancellationRefundRule(
                    rs.getInt("refund_cutoff_days_before"),
                    rs.getString("refund_cutoff_local_time"), 100));
            return new ScopedRevision(rs.getObject("id", UUID.class), "SET", 0,
                    scopedRequestHash(null, "SET", legacyRules), legacyRules);
        }, CurrentPolicy.CANCELLATION_KEY, idempotencyKey);
    }

    private void saveNoopReceipt(UUID hotelId, String idempotencyKey, String requestHash,
            UUID revisionId, int revisionNumber, String action, List<CancellationRefundRule> rules) {
        jdbc.update("""
                insert into cancellation_policy_noop_command
                    (hotel_id, idempotency_key, request_hash, revision_id, revision_number, action,
                     rules_json, created_at)
                values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)
                """, hotelId, idempotencyKey, requestHash, revisionId, revisionNumber, action, rulesJson(rules),
                java.sql.Timestamp.from(clock.instant()));
    }

    private List<CancellationRefundRule> receiptRules(UUID hotelId, String idempotencyKey) {
        String sql = hotelId == null
                ? """
                  select (entry.value->>'daysBefore')::integer,
                         entry.value->>'cutoffLocalTime',
                         (entry.value->>'refundPercent')::integer
                    from cancellation_policy_noop_command receipt,
                         jsonb_array_elements(receipt.rules_json) with ordinality as entry(value, position)
                   where receipt.hotel_id is null and receipt.idempotency_key = ?
                   order by entry.position
                  """
                : """
                  select (entry.value->>'daysBefore')::integer,
                         entry.value->>'cutoffLocalTime',
                         (entry.value->>'refundPercent')::integer
                    from cancellation_policy_noop_command receipt,
                         jsonb_array_elements(receipt.rules_json) with ordinality as entry(value, position)
                   where receipt.hotel_id = ? and receipt.idempotency_key = ?
                   order by entry.position
                  """;
        Object[] args = hotelId == null ? new Object[] { idempotencyKey } : new Object[] { hotelId, idempotencyKey };
        return jdbc.query(sql, (rs, rowNumber) -> new CancellationRefundRule(
                rs.getInt(1), rs.getString(2), rs.getInt(3)), args);
    }

    private String rulesJson(List<CancellationRefundRule> rules) {
        return rules.stream()
                .map(rule -> "{\"daysBefore\":" + rule.daysBefore()
                        + ",\"cutoffLocalTime\":\"" + rule.cutoffLocalTime()
                        + "\",\"refundPercent\":" + rule.refundPercent() + "}")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private UUID insertLegacyCancellation(
            StaffPrincipal staff, String idempotencyKey, CancellationPolicyUpdateRequest request) {
        UUID revisionId = UUID.randomUUID();
        jdbc.update("""
                insert into policy_revision
                    (id, key, refund_cutoff_days_before, refund_cutoff_local_time, timezone,
                     staff_id, idempotency_key, request_hash, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, revisionId, CurrentPolicy.CANCELLATION_KEY,
                request.cutoffDays(), request.cutoffLocalTime(), CurrentPolicy.DEFAULT_TIMEZONE,
                staff.id(), idempotencyKey, requestHash(staff.id(), request),
                java.sql.Timestamp.from(clock.instant()));
        return revisionId;
    }

    private static String scopedRequestHash(
            UUID hotelId, String action, List<CancellationRefundRule> rules) {
        StringBuilder payload = new StringBuilder(hotelId == null ? "CHAIN" : hotelId.toString())
                .append('|').append(action);
        for (CancellationRefundRule rule : rules) {
            payload.append('|').append(rule.daysBefore())
                    .append('|').append(rule.cutoffLocalTime())
                    .append('|').append(rule.refundPercent());
        }
        return sha256(payload.toString());
    }

    private record ScopedRevision(UUID id, String action, int revision, String requestHash,
            List<CancellationRefundRule> rules) {}

    private record ScopedPolicyResult(UUID id, int revision, boolean created, String action,
            List<CancellationRefundRule> rules) {}

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

    // 본사가 예약 변경 승인 TTL을 변경한다. 진행 중인 변경 요청은 이미 저장된
    // approval_expires_at을 그대로 쓴다. 신규 요청부터 새 TTL이 적용된다.
    @Transactional
    public ChangeApprovalTtlUpdateResponse updateChangeApprovalTtl(String token, String idempotencyKey,
            ChangeApprovalTtlUpdateRequest request) {
        StaffPrincipal staff = access.requireHeadquarters(token);
        requireIdempotencyKey(idempotencyKey);
        request.validate();

        lockKey(CurrentPolicy.CHANGE_APPROVAL_TTL_KEY);

        UUID existing = findExistingRevision(CurrentPolicy.CHANGE_APPROVAL_TTL_KEY, staff.id(), idempotencyKey,
                ttlRequestHash(staff.id(), request));
        if (existing != null) {
            return loadExistingTtl(existing);
        }

        jdbc.update("""
                insert into policy_revision
                    (id, key, refund_cutoff_days_before, refund_cutoff_local_time, timezone,
                     value_seconds, staff_id, idempotency_key, request_hash, created_at)
                values (?, ?, 0, '00:00', ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), CurrentPolicy.CHANGE_APPROVAL_TTL_KEY, CurrentPolicy.DEFAULT_TIMEZONE,
                request.ttlSeconds(), staff.id(), idempotencyKey,
                ttlRequestHash(staff.id(), request), java.sql.Timestamp.from(clock.instant()));

        return new ChangeApprovalTtlUpdateResponse(request.ttlSeconds(), ttlRevisionNumber(), true);
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

    private ChangeApprovalTtlUpdateResponse loadExistingTtl(UUID revisionId) {
        return jdbc.query("""
                select value_seconds from policy_revision where id = ?
                """, rs -> rs.next()
                        ? new ChangeApprovalTtlUpdateResponse(rs.getInt(1), ttlRevisionNumber(), false)
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

    private int ttlRevisionNumber() {
        Integer count = jdbc.queryForObject(
                "select count(*) from policy_revision where key = ?", Integer.class,
                CurrentPolicy.CHANGE_APPROVAL_TTL_KEY);
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

    static String ttlRequestHash(UUID staffId, ChangeApprovalTtlUpdateRequest request) {
        String payload = staffId + "|" + CurrentPolicy.CHANGE_APPROVAL_TTL_KEY + "|" + request.ttlSeconds();
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
