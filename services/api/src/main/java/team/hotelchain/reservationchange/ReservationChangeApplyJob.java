package team.hotelchain.reservationchange;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "reservation.change.settlement-enabled", havingValue = "true")
public class ReservationChangeApplyJob {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ReservationChangeApplyService applyService;
    private final Clock clock;
    private final Duration lease;
    private final int maxAttempts;

    public ReservationChangeApplyJob(
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            ReservationChangeApplyService applyService,
            Clock clock,
            @Value("${reservation.change.apply-lease:30s}") Duration lease,
            @Value("${reservation.change.apply-max-attempts:5}") int maxAttempts) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.applyService = applyService;
        this.clock = clock;
        this.lease = lease;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${reservation.change.apply-scan-delay:2s}")
    public void runScheduled() {
        for (int count = 0; count < 20 && claimAndApplyNext(); count++) {
            // bounded drain
        }
    }

    public boolean claimAndApplyNext() {
        ApplyClaim claim = transactions.execute(status -> claim());
        if (claim == null) return false;
        try {
            applyService.apply(claim.requestId(), claim.claimToken());
        } catch (RuntimeException exception) {
            transactions.executeWithoutResult(status -> recover(claim, exception));
        }
        return true;
    }

    private ApplyClaim claim() {
        ApplyCandidate candidate = jdbc.query("""
                select id, apply_attempt_count from reservation_change_request
                where status = 'READY_TO_APPLY'
                   or (status = 'APPLYING' and apply_lease_expires_at <= ?)
                order by updated_at, id limit 1 for update skip locked
                """, rs -> rs.next() ? new ApplyCandidate(
                        rs.getObject("id", UUID.class), rs.getInt("apply_attempt_count") + 1) : null,
                Timestamp.from(clock.instant()));
        if (candidate == null) return null;
        UUID token = UUID.randomUUID();
        jdbc.update("""
                update reservation_change_request
                set status = 'APPLYING', apply_claim_token = ?, apply_lease_expires_at = ?,
                    apply_attempt_count = ?, version = version + 1, updated_at = ?
                where id = ?
                """, token, Timestamp.from(clock.instant().plus(lease)), candidate.attemptCount(),
                Timestamp.from(clock.instant()), candidate.requestId());
        return new ApplyClaim(candidate.requestId(), token, candidate.attemptCount());
    }

    private void recover(ApplyClaim claim, RuntimeException exception) {
        boolean terminal = claim.attemptCount() >= maxAttempts;
        int updated = jdbc.update("""
                update reservation_change_request
                set status = ?, apply_claim_token = null, apply_lease_expires_at = null,
                    error_code = ?, error_summary = ?, version = version + 1, updated_at = ?
                where id = ? and status = 'APPLYING' and apply_claim_token = ?
                """, terminal ? "RECONCILIATION_REQUIRED" : "READY_TO_APPLY",
                terminal ? "APPLY_RETRY_EXHAUSTED" : "APPLY_RETRY_PENDING",
                abbreviate(exception.getMessage()), Timestamp.from(clock.instant()),
                claim.requestId(), claim.claimToken());
        if (updated == 1 && terminal) {
            jdbc.update("""
                    insert into reservation_change_event (
                        id, request_id, event_type, from_status, to_status, payload)
                    values (?, ?, 'APPLY_RETRY_EXHAUSTED', 'APPLYING', 'RECONCILIATION_REQUIRED',
                            jsonb_build_object('reason', ?::text))
                    """, UUID.randomUUID(), claim.requestId(), abbreviate(exception.getMessage()));
        }
    }

    private String abbreviate(String value) {
        String message = value == null ? "예약 변경 적용 실패" : value;
        return message.substring(0, Math.min(message.length(), 300));
    }

    private record ApplyCandidate(UUID requestId, int attemptCount) {
    }

    private record ApplyClaim(UUID requestId, UUID claimToken, int attemptCount) {
    }
}
