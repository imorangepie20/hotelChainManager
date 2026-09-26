package team.hotelchain.policy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.hotel.HotelNotFoundException;

/** 체인 기본값과 지점 revision을 합쳐 한 개의 유효 취소 정책을 만든다. */
@Component
public class CancellationPolicyResolver {

    private final JdbcTemplate jdbc;
    private final CurrentPolicy currentPolicy;

    public CancellationPolicyResolver(JdbcTemplate jdbc, CurrentPolicy currentPolicy) {
        this.jdbc = jdbc;
        this.currentPolicy = currentPolicy;
    }

    @Transactional(readOnly = true)
    public ResolvedCancellationPolicy resolveChain() {
        RevisionRef scoped = latestScopedChain();
        LegacyRef legacy = latestLegacy();
        boolean pairedLegacy = scoped != null && legacy != null
                && legacy.id().equals(scoped.legacyRevisionId());
        if (scoped != null && (legacy == null || pairedLegacy || !scoped.createdAt().isBefore(legacy.createdAt()))) {
            return new ResolvedCancellationPolicy(scoped.id(), "CHAIN", null, CurrentPolicy.DEFAULT_TIMEZONE,
                    rules(scoped.id()), false);
        }
        CancellationPolicy policy = currentPolicy.cancellation();
        UUID revisionId = legacy == null ? null : legacy.id();
        return new ResolvedCancellationPolicy(revisionId, "CHAIN", null, policy.timezone(),
                List.of(new CancellationRefundRule(
                        policy.refundCutoffDaysBefore(), policy.refundCutoffLocalTime(), 100)), false);
    }

    @Transactional(readOnly = true)
    public ResolvedCancellationPolicy resolveForHotel(UUID hotelId) {
        String timezone = jdbc.query("select timezone from hotel where id = ?",
                rs -> rs.next() ? rs.getString(1) : null, hotelId);
        if (timezone == null) throw new HotelNotFoundException(hotelId);

        CancellationPolicyRevisionView branch = latestHotelRevision(hotelId);
        if (branch != null && "SET".equals(branch.action())) {
            return new ResolvedCancellationPolicy(branch.revisionId(), "HOTEL", hotelId, timezone,
                    branch.rules(), false);
        }
        ResolvedCancellationPolicy chain = resolveChain();
        return new ResolvedCancellationPolicy(chain.revisionId(), "CHAIN", null, timezone, chain.rules(), true);
    }

    @Transactional(readOnly = true)
    public CancellationPolicyRevisionView latestHotelRevision(UUID hotelId) {
        return jdbc.query("""
                select id, action, revision_number
                 from cancellation_policy_revision
                 where hotel_id = ?
                 order by revision_number desc
                 limit 1
                """, rs -> {
            if (!rs.next()) return null;
            UUID id = rs.getObject("id", UUID.class);
            String action = rs.getString("action");
            return new CancellationPolicyRevisionView(id, hotelId, action, rs.getInt("revision_number"),
                    "SET".equals(action) ? rules(id) : List.of());
        }, hotelId);
    }

    private RevisionRef latestScopedChain() {
        return jdbc.query("""
                select id, legacy_revision_id, created_at
                 from cancellation_policy_revision
                 where hotel_id is null and action = 'SET'
                 order by revision_number desc
                 limit 1
                """, rs -> rs.next()
                        ? new RevisionRef(
                                rs.getObject("id", UUID.class),
                                rs.getObject("legacy_revision_id", UUID.class),
                                rs.getTimestamp("created_at").toInstant())
                        : null);
    }

    private LegacyRef latestLegacy() {
        return jdbc.query("""
                select id, created_at
                  from policy_revision
                 where key = ?
                 order by created_at desc, id desc
                 limit 1
                """, rs -> rs.next()
                        ? new LegacyRef(rs.getObject("id", UUID.class), timestampInstant(rs.getTimestamp("created_at")))
                        : null, CurrentPolicy.CANCELLATION_KEY);
    }

    private List<CancellationRefundRule> rules(UUID revisionId) {
        return jdbc.query("""
                select days_before, cutoff_local_time, refund_percent
                  from cancellation_refund_rule
                 where revision_id = ?
                 order by rule_order
                """, (rs, rowNumber) -> new CancellationRefundRule(
                rs.getInt("days_before"), rs.getString("cutoff_local_time"), rs.getInt("refund_percent")), revisionId);
    }

    private Instant timestampInstant(Timestamp timestamp) {
        return timestamp.toInstant();
    }

    private record RevisionRef(UUID id, UUID legacyRevisionId, Instant createdAt) {}

    private record LegacyRef(UUID id, Instant createdAt) {}
}
