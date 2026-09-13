package team.hotelchain.reservationchange;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name = "reservation.change.settlement-enabled", havingValue = "true")
public class ReservationChangeOutboxWorker {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final PaymentAdjustmentGateway gateway;
    private final ReservationChangeSettlementService settlements;
    private final Clock clock;
    private final Duration lease;

    public ReservationChangeOutboxWorker(
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            PaymentAdjustmentGateway gateway,
            ReservationChangeSettlementService settlements,
            Clock clock,
            @Value("${reservation.change.outbox-lease:30s}") Duration lease) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.gateway = gateway;
        this.settlements = settlements;
        this.clock = clock;
        this.lease = lease;
    }

    @Scheduled(fixedDelayString = "${reservation.change.outbox-scan-delay:2s}")
    public void processBatch() {
        for (int count = 0; count < 20 && processNext(); count++) {
            // bounded drain
        }
    }

    public boolean processNext() {
        SettlementCommandClaim claim = transactions.execute(status -> claimNext());
        if (claim == null) return false;

        try {
            AttemptCommand command = loadCommand(claim.attemptId());
            PaymentAdjustmentGateway.GatewayAdjustmentResult result = execute(claim.commandType(), command);
            Boolean accepted = transactions.execute(status -> completeClaim(claim, result));
            if (Boolean.TRUE.equals(accepted)
                    && result.status() != PaymentAdjustmentGateway.GatewayResultStatus.PENDING) {
                String eventId = result.providerEventId() == null
                        ? "outbox:" + claim.outboxId() + ":" + result.status()
                        : result.providerEventId();
                settlements.recordGatewayResult(claim.attemptId(), eventId, result.status());
            }
        } catch (RuntimeException exception) {
            transactions.executeWithoutResult(status -> failClaim(claim, exception));
        }
        return true;
    }

    private SettlementCommandClaim claimNext() {
        SettlementCommandClaim candidate = jdbc.query("""
                select id, request_id, attempt_id, command_type, attempt_count
                from reservation_change_outbox
                where ((status = 'PENDING' and next_attempt_at <= ?)
                       or (status = 'PROCESSING' and lease_expires_at <= ?))
                order by created_at, id
                limit 1 for update skip locked
                """, rs -> rs.next() ? new SettlementCommandClaim(
                        rs.getObject("id", UUID.class), rs.getObject("request_id", UUID.class),
                        rs.getObject("attempt_id", UUID.class), rs.getString("command_type"),
                        rs.getInt("attempt_count") + 1, UUID.randomUUID()) : null,
                Timestamp.from(clock.instant()), Timestamp.from(clock.instant()));
        if (candidate == null) return null;
        jdbc.update("""
                update reservation_change_outbox
                set status = 'PROCESSING', claim_token = ?, attempt_count = ?,
                    lease_expires_at = ?, updated_at = ?
                where id = ?
                """, candidate.claimToken(), candidate.attemptCount(),
                Timestamp.from(clock.instant().plus(lease)), Timestamp.from(clock.instant()), candidate.outboxId());
        return candidate;
    }

    private AttemptCommand loadCommand(UUID attemptId) {
        AttemptCommand command = jdbc.query("""
                select attempt.id, attempt.adjustment_type, attempt.idempotency_key,
                       attempt.amount_krw, attempt.currency,
                       attempt.original_payment_transaction_id,
                       original.gateway_transaction_id as original_gateway_transaction_id,
                       attempt.gateway_transaction_id
                from payment_adjustment_attempt attempt
                left join payment_transaction original on original.id = attempt.original_payment_transaction_id
                where attempt.id = ?
                """, rs -> rs.next() ? new AttemptCommand(
                        rs.getObject("id", UUID.class), rs.getString("adjustment_type"),
                        rs.getString("idempotency_key"), rs.getLong("amount_krw"),
                        rs.getString("currency").trim(),
                        rs.getObject("original_payment_transaction_id", UUID.class),
                        rs.getString("original_gateway_transaction_id"),
                        rs.getString("gateway_transaction_id")) : null, attemptId);
        if (command == null) throw new IllegalStateException("정산 시도 정보를 찾을 수 없습니다.");
        return command;
    }

    private PaymentAdjustmentGateway.GatewayAdjustmentResult execute(String commandType, AttemptCommand command) {
        return switch (commandType) {
            case "CREATE_CHECKOUT" -> gateway.createCheckout(new PaymentAdjustmentGateway.GatewayCheckoutCommand(
                    command.id(), command.idempotencyKey(), command.amountKrw(), command.currency(),
                    URI.create("http://127.0.0.1:4000/reservation-change-payment")));
            case "REFUND" -> gateway.refund(
                    new PaymentAdjustmentGateway.GatewayRefundCommand(
                            command.id(), command.idempotencyKey(), command.originalGatewayTransactionId(),
                            command.amountKrw(), command.currency()));
            case "QUERY" -> gateway.query(new PaymentAdjustmentGateway.GatewayQueryCommand(
                    command.id(), command.gatewayTransactionId()));
            default -> throw new IllegalStateException("지원하지 않는 정산 명령입니다: " + commandType);
        };
    }

    private boolean completeClaim(
            SettlementCommandClaim claim,
            PaymentAdjustmentGateway.GatewayAdjustmentResult result) {
        int updated = jdbc.update("""
                update reservation_change_outbox
                set status = 'DONE', claim_token = null, lease_expires_at = null,
                    last_error = null, updated_at = ?
                where id = ? and status = 'PROCESSING' and claim_token = ?
                """, Timestamp.from(clock.instant()), claim.outboxId(), claim.claimToken());
        if (updated != 1) return false;
        jdbc.update("""
                update payment_adjustment_attempt
                set status = 'PROCESSING', provider_event_id = ?, gateway_transaction_id = ?,
                    checkout_url = ?, error_code = ?, updated_at = ?
                where id = ? and status in ('NEW', 'PROCESSING')
                """, result.providerEventId(), result.gatewayTransactionId(),
                result.checkoutUrl() == null ? null : result.checkoutUrl().toString(),
                result.errorCode(), Timestamp.from(clock.instant()), claim.attemptId());
        return true;
    }

    private void failClaim(SettlementCommandClaim claim, RuntimeException exception) {
        boolean terminal = claim.attemptCount() >= 5;
        jdbc.update("""
                update reservation_change_outbox
                set status = ?, claim_token = null, lease_expires_at = null,
                    next_attempt_at = ?, last_error = ?, updated_at = ?
                where id = ? and status = 'PROCESSING' and claim_token = ?
                """, terminal ? "FAILED" : "PENDING",
                Timestamp.from(clock.instant().plusSeconds(Math.min(60, claim.attemptCount() * 2L))),
                abbreviate(exception.getMessage()), Timestamp.from(clock.instant()),
                claim.outboxId(), claim.claimToken());
    }

    private String abbreviate(String value) {
        String message = value == null ? "정산 명령 처리 실패" : value;
        return message.substring(0, Math.min(message.length(), 300));
    }

    private record AttemptCommand(
            UUID id,
            String adjustmentType,
            String idempotencyKey,
            long amountKrw,
            String currency,
            UUID originalPaymentTransactionId,
            String originalGatewayTransactionId,
            String gatewayTransactionId) {
    }
}
