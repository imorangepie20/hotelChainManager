package team.hotelchain.reservationchange;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.reservation.BusinessConflictException;
import team.hotelchain.reservation.ReservationAccess;
import team.hotelchain.reservation.ReservationNotFoundException;
import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

@Service
public class ReservationChangeReconciliationService {
    private final JdbcTemplate jdbc;
    private final StaffAccessService staffAccess;
    private final ReservationAccess access;
    private final ReservationChangeHoldService holds;
    private final Clock clock;

    public ReservationChangeReconciliationService(
            JdbcTemplate jdbc,
            StaffAccessService staffAccess,
            ReservationAccess access,
            ReservationChangeHoldService holds,
            Clock clock) {
        this.jdbc = jdbc;
        this.staffAccess = staffAccess;
        this.access = access;
        this.holds = holds;
        this.clock = clock;
    }

    @Transactional
    public void reconcile(
            String staffToken,
            UUID requestId,
            String idempotencyKey,
            ReservationChangeReconciliationRequest input) {
        validate(idempotencyKey, input);
        StaffPrincipal staff = staffAccess.requireHeadquarters(staffToken);
        ReconciliationContext context = lockContext(requestId);
        if (context.version() != input.version()) throw conflict("RESERVATION_CHANGE_VERSION_CONFLICT");
        if (!"RECONCILIATION_REQUIRED".equals(context.requestStatus())) {
            throw conflict("RESERVATION_CHANGE_STATE_CONFLICT");
        }
        String dedupeKey = "reconcile:" + requestId + ":" + staff.id() + ":" + idempotencyKey;
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from reservation_change_event where dedupe_key = ?)",
                Boolean.class, dedupeKey))) return;

        switch (input.action()) {
            case "QUERY_GATEWAY" -> enqueueQuery(context, idempotencyKey);
            case "RETRY_APPLY" -> retryApply(context);
            case "RELEASE_AFTER_CONFIRMED_FAILURE" -> releaseAfterFailure(context);
            case "REFUND_ADJUSTMENT_AND_CANCEL" -> refundAdjustment(context, idempotencyKey, staff.id(), input.reason());
            default -> throw new IllegalArgumentException("지원하지 않는 조정 작업입니다.");
        }
        jdbc.update("""
                insert into reservation_change_event (
                    id, request_id, event_type, from_status, to_status,
                    actor_staff_id, dedupe_key, payload)
                values (?, ?, 'RECONCILIATION_ACTION', 'RECONCILIATION_REQUIRED',
                        (select status from reservation_change_request where id = ?),
                        ?, ?, jsonb_build_object('action', ?::text, 'reason', ?::text))
                """, UUID.randomUUID(), requestId, requestId, staff.id(), dedupeKey,
                input.action(), input.reason().trim());
    }

    private void enqueueQuery(ReconciliationContext context, String idempotencyKey) {
        jdbc.update("""
                insert into reservation_change_outbox (
                    id, request_id, attempt_id, command_type, dedupe_key, payload)
                values (?, ?, ?, 'QUERY', ?, '{}'::jsonb)
                on conflict (dedupe_key) do nothing
                """, UUID.randomUUID(), context.requestId(), context.attemptId(),
                "query:" + context.attemptId() + ":" + idempotencyKey);
    }

    private void retryApply(ReconciliationContext context) {
        if (!"SUCCEEDED".equals(context.attemptStatus())) throw conflict("RECONCILIATION_ACTION_NOT_ALLOWED");
        jdbc.update("""
                update reservation_change_request
                set status = 'READY_TO_APPLY', apply_claim_token = null,
                    apply_lease_expires_at = null, error_code = null, error_summary = null,
                    version = version + 1, updated_at = ? where id = ?
                """, Timestamp.from(clock.instant()), context.requestId());
    }

    private void releaseAfterFailure(ReconciliationContext context) {
        if (!"FAILED".equals(context.attemptStatus())) throw conflict("RECONCILIATION_ACTION_NOT_ALLOWED");
        holds.release(context.requestId(), "RECONCILIATION_CONFIRMED_FAILURE");
        jdbc.update("""
                update reservation_change_request
                set status = 'CANCELLED', version = version + 1, updated_at = ? where id = ?
                """, Timestamp.from(clock.instant()), context.requestId());
    }

    private void refundAdjustment(
            ReconciliationContext context,
            String idempotencyKey,
            UUID staffId,
            String reason) {
        if (!"CREATE_CHECKOUT".equals(context.adjustmentType()) || !"SUCCEEDED".equals(context.attemptStatus())
                || context.paymentTransactionId() == null) {
            throw conflict("RECONCILIATION_ACTION_NOT_ALLOWED");
        }
        String requestHash = access.sha256(String.join(":", staffId.toString(), idempotencyKey, reason.trim())
                .getBytes(StandardCharsets.UTF_8));
        UUID attemptId = UUID.randomUUID();
        jdbc.update("""
                insert into payment_adjustment_attempt (
                    id, request_id, original_payment_transaction_id, adjustment_type,
                    provider, idempotency_key, request_hash, amount_krw, currency, status)
                values (?, ?, ?, 'REFUND_ADJUSTMENT', ?, ?, ?, ?, ?, 'NEW')
                """, attemptId, context.requestId(), context.paymentTransactionId(), context.provider(),
                idempotencyKey, requestHash, context.amountKrw(), context.currency());
        jdbc.update("""
                insert into reservation_change_outbox (
                    id, request_id, attempt_id, command_type, dedupe_key, payload)
                values (?, ?, ?, 'REFUND', ?, '{}'::jsonb)
                """, UUID.randomUUID(), context.requestId(), attemptId, "refund-adjustment:" + attemptId);
    }

    private ReconciliationContext lockContext(UUID requestId) {
        ReconciliationContext context = jdbc.query("""
                select request.id as request_id, request.status as request_status, request.version,
                       attempt.id as attempt_id, attempt.adjustment_type, attempt.status as attempt_status,
                       attempt.provider, attempt.amount_krw, attempt.currency,
                       payment.id as payment_transaction_id
                from reservation_change_request request
                left join lateral (
                    select * from payment_adjustment_attempt candidate
                    where candidate.request_id = request.id
                    order by candidate.created_at desc limit 1
                ) attempt on true
                left join payment_transaction payment
                  on payment.change_request_id = request.id and payment.transaction_type = 'CHANGE_CHARGE'
                where request.id = ? for update of request
                """, rs -> rs.next() ? new ReconciliationContext(
                        rs.getObject("request_id", UUID.class), rs.getString("request_status"),
                        rs.getLong("version"), rs.getObject("attempt_id", UUID.class),
                        rs.getString("adjustment_type"), rs.getString("attempt_status"), rs.getString("provider"),
                        rs.getLong("amount_krw"), rs.getString("currency"),
                        rs.getObject("payment_transaction_id", UUID.class)) : null, requestId);
        if (context == null) throw new ReservationNotFoundException();
        return context;
    }

    private void validate(String key, ReservationChangeReconciliationRequest input) {
        if (key == null || key.isBlank() || key.length() > 100 || input == null || input.version() < 0
                || input.action() == null || input.reason() == null || input.reason().isBlank()) {
            throw new IllegalArgumentException("조정 작업, version, 사유와 Idempotency-Key가 필요합니다.");
        }
    }

    private BusinessConflictException conflict(String code) {
        return new BusinessConflictException(code, "현재 정산 상태에서는 선택한 조정 작업을 실행할 수 없습니다.");
    }

    private record ReconciliationContext(
            UUID requestId, String requestStatus, long version, UUID attemptId,
            String adjustmentType, String attemptStatus, String provider,
            long amountKrw, String currency, UUID paymentTransactionId) {
    }
}
