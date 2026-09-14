package team.hotelchain.reservationchange;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.reservation.BusinessConflictException;
import team.hotelchain.reservation.ReservationAccess;
import team.hotelchain.reservation.ReservationNotFoundException;
import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

@Service
public class ReservationChangeSettlementService {
    private final JdbcTemplate jdbc;
    private final StaffAccessService staffAccess;
    private final ReservationAccess reservationAccess;
    private final ReservationChangeHoldService holds;
    private final ReservationChangePolicy policy;
    private final Clock clock;
    private final String customerBaseUrl;
    private final String gatewayMode;
    private final SecureRandom random = new SecureRandom();

    public ReservationChangeSettlementService(
            JdbcTemplate jdbc,
            StaffAccessService staffAccess,
            ReservationAccess reservationAccess,
            ReservationChangeHoldService holds,
            ReservationChangePolicy policy,
            Clock clock,
            @Value("${reservation.change.customer-base-url:http://127.0.0.1:4000}") String customerBaseUrl,
            @Value("${reservation.change.gateway:disabled}") String gatewayMode) {
        this.jdbc = jdbc;
        this.staffAccess = staffAccess;
        this.reservationAccess = reservationAccess;
        this.holds = holds;
        this.policy = policy;
        this.clock = clock;
        this.customerBaseUrl = customerBaseUrl.replaceAll("/+$", "");
        this.gatewayMode = gatewayMode;
    }

    @Transactional
    public ReservationChangePaymentLinkView createPaymentLink(
            String staffToken,
            UUID requestId,
            String idempotencyKey,
            ReservationChangePaymentLinkRequest input) {
        validateMutation(idempotencyKey, input);
        if (!policy.settlementEnabled()) {
            throw new BusinessConflictException(
                    "CHANGE_SETTLEMENT_DISABLED", "예약 변경 정산 기능이 비활성화되어 있습니다.");
        }
        StaffPrincipal staff = staffAccess.current(staffToken);
        String publicTokenHash = reservationAccess.hashToken(input.publicToken());
        PaymentContext context = paymentContext(requestId);
        staffAccess.requireHotel(staff, context.hotelId());
        team.hotelchain.reservation.PaymentProviderSafety.requireFakeSettlement(jdbc, context.reservationId());
        String requestHash = reservationAccess.sha256(String.join(":",
                staff.id().toString(), Long.toString(input.version()), publicTokenHash)
                .getBytes(StandardCharsets.UTF_8));

        ExistingAttempt existing = existingAttempt(requestId, idempotencyKey);
        if (existing != null) {
            if (!existing.requestHash().equals(requestHash)) throw idempotencyConflict();
            return paymentLinkView(requestId, input.publicToken());
        }
        if (!"CHARGE".equals(context.direction()) || context.differenceKrw() <= 0) {
            throw new BusinessConflictException(
                    "RESERVATION_CHANGE_ACTION_NOT_ALLOWED", "추가 결제가 필요한 요청만 고객 결제 링크를 만들 수 있습니다.");
        }

        long versionAfterHold;
        Instant expiresAt;
        if ("APPROVED".equals(context.status())) {
            ReservationChangeHoldService.HoldResult hold = holds.acquire(requestId, input.version());
            versionAfterHold = hold.requestVersion();
            expiresAt = hold.expiresAt();
        } else if ("AWAITING_PAYMENT".equals(context.status()) && context.version() == input.version()
                && replaceableAttempt(requestId)) {
            jdbc.update("""
                    update reservation_change_outbox set status = 'FAILED', last_error = 'REPLACED', updated_at = ?
                    where attempt_id in (select id from payment_adjustment_attempt where request_id = ?)
                      and status in ('PENDING', 'PROCESSING')
                    """, Timestamp.from(clock.instant()), requestId);
            jdbc.update("""
                    update payment_adjustment_attempt
                    set status = 'FAILED', public_token_hash = null, error_code = 'REPLACED', updated_at = ?
                    where request_id = ? and adjustment_type = 'CREATE_CHECKOUT'
                      and checkout_started_at is null and status in ('NEW', 'PROCESSING')
                    """, Timestamp.from(clock.instant()), requestId);
            versionAfterHold = context.version();
            expiresAt = context.settlementExpiresAt();
        } else {
            throw new BusinessConflictException(
                    "RESERVATION_CHANGE_STATE_CONFLICT", "현재 상태에서는 고객 결제 링크를 만들 수 없습니다.");
        }

        UUID attemptId = UUID.randomUUID();
        UUID outboxId = UUID.randomUUID();
        jdbc.update("""
                insert into payment_adjustment_attempt (
                    id, request_id, adjustment_type, provider, idempotency_key, request_hash,
                    amount_krw, currency, status, public_token_hash)
                values (?, ?, 'CREATE_CHECKOUT', 'FAKE', ?, ?, ?, ?, 'NEW', ?)
                """, attemptId, requestId, idempotencyKey, requestHash,
                context.differenceKrw(), context.currency(), publicTokenHash);
        jdbc.update("""
                insert into reservation_change_outbox (
                    id, request_id, attempt_id, command_type, dedupe_key, payload)
                values (?, ?, ?, 'CREATE_CHECKOUT', ?, '{}'::jsonb)
                """, outboxId, requestId, attemptId, "create-checkout:" + attemptId);
        int updated = jdbc.update("""
                update reservation_change_request
                set status = 'AWAITING_PAYMENT', settlement_expires_at = ?,
                    version = version + 1, updated_at = ?
                where id = ? and version = ?
                """, Timestamp.from(expiresAt), Timestamp.from(clock.instant()), requestId, versionAfterHold);
        if (updated != 1) {
            throw new BusinessConflictException(
                    "RESERVATION_CHANGE_VERSION_CONFLICT", "예약 변경 요청이 갱신되었습니다. 다시 확인해 주세요.");
        }
        insertEvent(requestId, "PAYMENT_LINK_REQUESTED", context.status(), "AWAITING_PAYMENT", staff.id(),
                "payment-link:" + requestId + ":" + staff.id() + ":" + idempotencyKey, null);
        return paymentLinkView(requestId, input.publicToken());
    }

    @Transactional
    public void startRefund(
            String staffToken,
            UUID requestId,
            String idempotencyKey,
            ReservationChangeVersionRequest input) {
        validateVersionMutation(idempotencyKey, input);
        if (!policy.settlementEnabled()) {
            throw new BusinessConflictException(
                    "CHANGE_SETTLEMENT_DISABLED", "예약 변경 정산 기능이 비활성화되어 있습니다.");
        }
        StaffPrincipal staff = staffAccess.current(staffToken);
        PaymentContext context = paymentContext(requestId);
        staffAccess.requireHotel(staff, context.hotelId());
        team.hotelchain.reservation.PaymentProviderSafety.requireFakeSettlement(jdbc, context.reservationId());
        String requestHash = reservationAccess.sha256(String.join(":",
                staff.id().toString(), Long.toString(input.version()), "REFUND")
                .getBytes(StandardCharsets.UTF_8));
        ExistingAttempt existing = existingAttempt(requestId, idempotencyKey);
        if (existing != null) {
            if (!existing.requestHash().equals(requestHash)) throw idempotencyConflict();
            return;
        }
        if (!"REFUND".equals(context.direction()) || context.differenceKrw() >= 0
                || !"APPROVED".equals(context.status())) {
            throw new BusinessConflictException(
                    "RESERVATION_CHANGE_ACTION_NOT_ALLOWED", "부분 환불이 필요한 승인 요청만 환불을 시작할 수 있습니다.");
        }

        jdbc.query("select id from reservation where id = ? for update", rs -> { }, context.reservationId());
        jdbc.query("select id from reservation_change_request where id = ? for update", rs -> { }, requestId);
        OriginalTransaction original = lockSettleableOriginalTransaction(context.reservationId());
        long refundAmount = Math.abs(context.differenceKrw());
        if (original.capturedAmountKrw() - original.refundedAmountKrw() < refundAmount) {
            throw notSettleable();
        }
        ReservationChangeHoldService.HoldResult hold = holds.acquire(requestId, input.version());
        UUID attemptId = UUID.randomUUID();
        jdbc.update("""
                insert into payment_adjustment_attempt (
                    id, request_id, original_payment_transaction_id, adjustment_type,
                    provider, idempotency_key, request_hash, amount_krw, currency, status)
                values (?, ?, ?, 'REFUND_ORIGINAL', ?, ?, ?, ?, ?, 'NEW')
                """, attemptId, requestId, original.id(), original.provider(), idempotencyKey,
                requestHash, refundAmount, context.currency());
        jdbc.update("""
                insert into reservation_change_outbox (
                    id, request_id, attempt_id, command_type, dedupe_key, payload)
                values (?, ?, ?, 'REFUND', ?, '{}'::jsonb)
                """, UUID.randomUUID(), requestId, attemptId, "refund:" + attemptId);
        int updated = jdbc.update("""
                update reservation_change_request
                set status = 'REFUND_PENDING', settlement_expires_at = ?,
                    version = version + 1, updated_at = ?
                where id = ? and version = ?
                """, Timestamp.from(hold.expiresAt()), Timestamp.from(clock.instant()),
                requestId, hold.requestVersion());
        if (updated != 1) {
            throw new BusinessConflictException(
                    "RESERVATION_CHANGE_VERSION_CONFLICT", "예약 변경 요청이 갱신되었습니다. 다시 확인해 주세요.");
        }
        insertEvent(requestId, "REFUND_REQUESTED", context.status(), "REFUND_PENDING", staff.id(),
                "refund:" + requestId + ":" + staff.id() + ":" + idempotencyKey, null);
    }

    @Transactional
    public CustomerSession exchangeCustomerToken(String publicToken) {
        String publicTokenHash = reservationAccess.hashToken(publicToken);
        TokenContext context = jdbc.query("""
                select attempt.request_id, request.settlement_expires_at
                from payment_adjustment_attempt attempt
                join reservation_change_request request on request.id = attempt.request_id
                where attempt.public_token_hash = ?
                  and attempt.adjustment_type = 'CREATE_CHECKOUT'
                  and attempt.status in ('NEW', 'PROCESSING', 'SUCCEEDED')
                  and request.status in ('AWAITING_PAYMENT', 'READY_TO_APPLY')
                  and request.settlement_expires_at > ?
                """, rs -> rs.next() ? new TokenContext(
                        rs.getObject("request_id", UUID.class),
                        rs.getTimestamp("settlement_expires_at").toInstant()) : null,
                publicTokenHash, Timestamp.from(clock.instant()));
        if (context == null) throw new ReservationNotFoundException();
        String sessionToken = newToken();
        jdbc.update("""
                insert into reservation_change_customer_session (id, request_id, token_hash, expires_at)
                values (?, ?, ?, ?)
                """, UUID.randomUUID(), context.requestId(), reservationAccess.hashToken(sessionToken),
                Timestamp.from(context.expiresAt()));
        return new CustomerSession(sessionToken, context.expiresAt());
    }

    @Transactional
    public CustomerReservationChangePaymentView current(String sessionToken) {
        CustomerContext context = customerContext(sessionToken, true);
        team.hotelchain.reservation.PaymentProviderSafety.requireFakeSettlement(jdbc, context.reservationId());
        return new CustomerReservationChangePaymentView(
                context.reservationId(), suffix(context.reservationId()),
                context.previousCheckIn(), context.previousCheckOut(), context.previousRoomTypeName(), context.previousRatePlanName(), context.previousTotalKrw(),
                context.checkIn(), context.checkOut(), context.roomTypeName(), context.ratePlanName(), context.totalKrw(), context.differenceKrw(),
                context.additionalAmountKrw(), context.currency(),
                context.expiresAt(), "테스트 결제", context.status());
    }

    @Transactional
    public String checkout(String sessionToken) {
        CustomerContext context = customerContext(sessionToken, true);
        team.hotelchain.reservation.PaymentProviderSafety.requireFakeSettlement(jdbc, context.reservationId());
        if (!"AWAITING_PAYMENT".equals(context.status()) || context.checkoutUrl() == null) {
            throw new BusinessConflictException(
                    "CHECKOUT_NOT_READY", "결제 화면을 준비 중입니다. 잠시 후 다시 시도해 주세요.");
        }
        jdbc.update("""
                update payment_adjustment_attempt set checkout_started_at = coalesce(checkout_started_at, ?), updated_at = ?
                where id = ?
                """, Timestamp.from(clock.instant()), Timestamp.from(clock.instant()), context.attemptId());
        return context.checkoutUrl();
    }

    @Transactional
    public void recordGatewayResult(
            UUID attemptId,
            String providerEventId,
            PaymentAdjustmentGateway.GatewayResultStatus resultStatus) {
        if (providerEventId == null || providerEventId.isBlank() || resultStatus == null) {
            throw new IllegalArgumentException("gateway 결과 식별자와 상태가 필요합니다.");
        }
        String dedupeKey = "gateway-result:" + providerEventId;
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from reservation_change_event where dedupe_key = ?)", Boolean.class, dedupeKey))) {
            return;
        }
        GatewayAttempt candidate = gatewayAttempt(attemptId, false);
        jdbc.query("select id from reservation where id = ? for update", rs -> { }, candidate.reservationId());
        jdbc.query("select id from reservation_change_request where id = ? for update", rs -> { }, candidate.requestId());
        GatewayAttempt attempt = gatewayAttempt(attemptId, true);
        team.hotelchain.reservation.PaymentProviderSafety.requireFakeSettlement(jdbc, attempt.reservationId());
        if (!"CREATE_CHECKOUT".equals(attempt.adjustmentType())
                && !"REFUND_ORIGINAL".equals(attempt.adjustmentType())
                && !"REFUND_ADJUSTMENT".equals(attempt.adjustmentType())) {
            throw new BusinessConflictException("GATEWAY_RESULT_CONFLICT", "결제 결과 대상이 올바르지 않습니다.");
        }
        if ("SUCCEEDED".equals(attempt.status()) || "FAILED".equals(attempt.status())) return;

        String attemptStatus = switch (resultStatus) {
            case SUCCEEDED -> "SUCCEEDED";
            case FAILED -> "FAILED";
            case UNKNOWN -> "UNKNOWN";
            case PENDING -> "PROCESSING";
        };
        jdbc.update("""
                update payment_adjustment_attempt
                set status = ?, provider_event_id = ?, updated_at = ?
                where id = ?
                """, attemptStatus, providerEventId, Timestamp.from(clock.instant()), attemptId);

        String requestStatus;
        if (resultStatus == PaymentAdjustmentGateway.GatewayResultStatus.SUCCEEDED) {
            if ("CREATE_CHECKOUT".equals(attempt.adjustmentType())) {
                jdbc.update("""
                        insert into payment_transaction (
                            id, reservation_id, change_request_id, provider, merchant_account,
                            gateway_transaction_id, transaction_type, captured_amount_krw,
                            refunded_amount_krw, currency)
                        values (?, ?, ?, ?, 'LOCAL', ?, 'CHANGE_CHARGE', ?, 0, ?)
                        on conflict (change_request_id) do nothing
                        """, UUID.randomUUID(), attempt.reservationId(), attempt.requestId(), attempt.provider(),
                        attempt.gatewayTransactionId(), attempt.amountKrw(), attempt.currency());
            } else {
                int refunded = jdbc.update("""
                        update payment_transaction
                        set refunded_amount_krw = refunded_amount_krw + ?, updated_at = ?
                        where id = ? and captured_amount_krw - refunded_amount_krw >= ?
                        """, attempt.amountKrw(), Timestamp.from(clock.instant()),
                        attempt.originalPaymentTransactionId(), attempt.amountKrw());
                if (refunded != 1) {
                    throw new BusinessConflictException(
                            "PAYMENT_TRANSACTION_NOT_SETTLEABLE", "원 결제 거래의 환불 가능 금액이 부족합니다.");
                }
            }
            if ("REFUND_ADJUSTMENT".equals(attempt.adjustmentType())) {
                holds.release(attempt.requestId(), "ADJUSTMENT_REFUNDED");
                requestStatus = "CANCELLED";
            } else {
                requestStatus = "READY_TO_APPLY";
            }
        } else if (resultStatus == PaymentAdjustmentGateway.GatewayResultStatus.FAILED) {
            holds.release(attempt.requestId(), "PAYMENT_FAILED");
            requestStatus = "CANCELLED";
        } else if (resultStatus == PaymentAdjustmentGateway.GatewayResultStatus.UNKNOWN) {
            requestStatus = "RECONCILIATION_REQUIRED";
        } else {
            requestStatus = "AWAITING_PAYMENT";
        }
        if (!requestStatus.equals(attempt.requestStatus())) {
            jdbc.update("""
                    update reservation_change_request set status = ?, version = version + 1, updated_at = ?
                    where id = ?
                    """, requestStatus, Timestamp.from(clock.instant()), attempt.requestId());
        }
        insertEvent(attempt.requestId(), "GATEWAY_RESULT_RECORDED", attempt.requestStatus(), requestStatus,
                null, dedupeKey, resultStatus.name());
    }

    PaymentAdjustmentGateway.GatewayResultStatus mapResultStatus(String value) {
        try {
            return PaymentAdjustmentGateway.GatewayResultStatus.valueOf(value.toUpperCase());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("지원하지 않는 fake 결제 결과입니다.");
        }
    }

    private CustomerContext customerContext(String sessionToken, boolean touch) {
        if (sessionToken == null || sessionToken.isBlank()) throw new ReservationNotFoundException();
        String tokenHash;
        try { tokenHash = reservationAccess.hashToken(sessionToken); }
        catch (IllegalArgumentException invalid) { throw new ReservationNotFoundException(); }
        CustomerContext context = jdbc.query("""
                select session.id as session_id, request.reservation_id,
                       request.previous_check_in, request.previous_check_out,
                       previous_room_type.name as previous_room_type_name,
                       previous_rate_plan.name as previous_rate_plan_name,
                       quote.previous_total_krw,
                       request.target_check_in, request.target_check_out, room_type.name as room_type_name,
                       rate_plan.name as rate_plan_name, quote.total_krw, quote.difference_krw, quote.currency,
                       request.settlement_expires_at, request.status,
                       attempt.id as attempt_id, attempt.checkout_url
                from reservation_change_customer_session session
                join reservation_change_request request on request.id = session.request_id
                join reservation_change_quote quote on quote.id = request.current_quote_id
                join room_type previous_room_type on previous_room_type.id = request.previous_room_type_id
                join rate_plan previous_rate_plan on previous_rate_plan.id = request.previous_rate_plan_id
                join room_type on room_type.id = request.target_room_type_id
                join rate_plan on rate_plan.id = request.target_rate_plan_id
                join payment_adjustment_attempt attempt on attempt.request_id = request.id
                    and attempt.adjustment_type = 'CREATE_CHECKOUT'
                    and attempt.public_token_hash is not null
                where session.token_hash = ? and session.expires_at > ?
                order by attempt.created_at desc limit 1
                """, rs -> rs.next() ? mapCustomerContext(rs) : null,
                tokenHash, Timestamp.from(clock.instant()));
        if (context == null) throw new ReservationNotFoundException();
        if (touch) jdbc.update("update reservation_change_customer_session set last_seen_at = ? where id = ?",
                Timestamp.from(clock.instant()), context.sessionId());
        return context;
    }

    private PaymentContext paymentContext(UUID requestId) {
        PaymentContext context = jdbc.query("""
                select request.reservation_id, request.hotel_id, request.status, request.settlement_direction, request.version,
                       quote.difference_krw, quote.currency, request.settlement_expires_at
                from reservation_change_request request
                join reservation_change_quote quote on quote.id = request.current_quote_id
                where request.id = ?
                """, rs -> rs.next() ? new PaymentContext(
                        rs.getObject("reservation_id", UUID.class), rs.getObject("hotel_id", UUID.class), rs.getString("status"),
                        rs.getString("settlement_direction"), rs.getLong("version"),
                        rs.getLong("difference_krw"), rs.getString("currency").trim(),
                        rs.getTimestamp("settlement_expires_at") == null ? null
                                : rs.getTimestamp("settlement_expires_at").toInstant()) : null, requestId);
        if (context == null) throw new ReservationNotFoundException();
        return context;
    }

    private ExistingAttempt existingAttempt(UUID requestId, String idempotencyKey) {
        return jdbc.query("""
                select request_hash from payment_adjustment_attempt
                where request_id = ? and idempotency_key = ?
                """, rs -> rs.next() ? new ExistingAttempt(rs.getString("request_hash")) : null,
                requestId, idempotencyKey);
    }

    private OriginalTransaction lockSettleableOriginalTransaction(UUID reservationId) {
        OriginalTransaction transaction = jdbc.query("""
                select id, provider, captured_amount_krw, refunded_amount_krw,
                       currency, gateway_transaction_id
                from payment_transaction
                where reservation_id = ? and transaction_type = 'ORIGINAL_CHARGE'
                order by created_at limit 1 for update
                """, rs -> rs.next() ? new OriginalTransaction(
                        rs.getObject("id", UUID.class), rs.getString("provider"),
                        rs.getLong("captured_amount_krw"), rs.getLong("refunded_amount_krw"),
                        rs.getString("currency").trim(), rs.getString("gateway_transaction_id")) : null,
                reservationId);
        if (transaction == null || !transaction.currency().equals("KRW")
                || ("fake".equals(gatewayMode) && !"FAKE".equals(transaction.provider()))) {
            throw notSettleable();
        }
        return transaction;
    }

    private boolean replaceableAttempt(UUID requestId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(
                    select 1 from payment_adjustment_attempt
                    where request_id = ? and adjustment_type = 'CREATE_CHECKOUT'
                      and checkout_started_at is null and status in ('NEW', 'PROCESSING'))
                """, Boolean.class, requestId));
    }

    private ReservationChangePaymentLinkView paymentLinkView(UUID requestId, String publicToken) {
        return jdbc.query("""
                select request.status, request.version, request.settlement_expires_at, attempt.created_at
                from reservation_change_request request
                join payment_adjustment_attempt attempt on attempt.request_id = request.id
                    and attempt.adjustment_type = 'CREATE_CHECKOUT' and attempt.public_token_hash = ?
                where request.id = ?
                """, rs -> {
            if (!rs.next()) throw new ReservationNotFoundException();
            return new ReservationChangePaymentLinkView(
                    requestId, rs.getString("status"), rs.getLong("version"),
                    customerBaseUrl + "/reservation-change-payment#" + publicToken,
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("settlement_expires_at").toInstant());
        }, reservationAccess.hashToken(publicToken), requestId);
    }

    private GatewayAttempt gatewayAttempt(UUID attemptId, boolean forUpdate) {
        String sql = """
                select attempt.id, attempt.request_id, request.reservation_id, request.status as request_status,
                       attempt.adjustment_type, attempt.provider, attempt.status, attempt.amount_krw,
                       attempt.currency, attempt.gateway_transaction_id,
                       attempt.original_payment_transaction_id
                from payment_adjustment_attempt attempt
                join reservation_change_request request on request.id = attempt.request_id
                where attempt.id = ?
                """ + (forUpdate ? " for update of attempt" : "");
        GatewayAttempt attempt = jdbc.query(sql, rs -> rs.next() ? new GatewayAttempt(
                rs.getObject("id", UUID.class), rs.getObject("request_id", UUID.class),
                rs.getObject("reservation_id", UUID.class), rs.getString("request_status"),
                rs.getString("adjustment_type"), rs.getString("provider"), rs.getString("status"),
                rs.getLong("amount_krw"), rs.getString("currency").trim(),
                rs.getString("gateway_transaction_id"),
                rs.getObject("original_payment_transaction_id", UUID.class)) : null, attemptId);
        if (attempt == null) throw new ReservationNotFoundException();
        return attempt;
    }

    private CustomerContext mapCustomerContext(ResultSet rs) throws SQLException {
        return new CustomerContext(
                rs.getObject("session_id", UUID.class), rs.getObject("reservation_id", UUID.class),
                rs.getDate("previous_check_in").toLocalDate(), rs.getDate("previous_check_out").toLocalDate(),
                rs.getString("previous_room_type_name"), rs.getString("previous_rate_plan_name"), rs.getLong("previous_total_krw"),
                rs.getDate("target_check_in").toLocalDate(), rs.getDate("target_check_out").toLocalDate(),
                rs.getString("room_type_name"), rs.getString("rate_plan_name"),
                rs.getLong("total_krw"), rs.getLong("difference_krw"), rs.getLong("difference_krw"), rs.getString("currency").trim(),
                rs.getTimestamp("settlement_expires_at").toInstant(), rs.getString("status"),
                rs.getObject("attempt_id", UUID.class), rs.getString("checkout_url"));
    }

    private void insertEvent(UUID requestId, String eventType, String fromStatus, String toStatus,
            UUID actor, String dedupeKey, String reason) {
        jdbc.update("""
                insert into reservation_change_event (
                    id, request_id, event_type, from_status, to_status,
                    actor_staff_id, dedupe_key, payload)
                values (?, ?, ?, ?, ?, ?, ?, jsonb_build_object('reason', ?::text))
                """, UUID.randomUUID(), requestId, eventType, fromStatus, toStatus, actor, dedupeKey, reason);
    }

    private void validateMutation(String idempotencyKey, ReservationChangePaymentLinkRequest input) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100
                || input == null || input.version() < 0 || input.publicToken() == null
                || !input.publicToken().matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("올바른 요청 version과 Idempotency-Key가 필요합니다.");
        }
    }

    private void validateVersionMutation(String idempotencyKey, ReservationChangeVersionRequest input) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100
                || input == null || input.version() < 0) {
            throw new IllegalArgumentException("올바른 요청 version과 Idempotency-Key가 필요합니다.");
        }
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String suffix(UUID reservationId) {
        String value = reservationId.toString();
        return value.substring(value.length() - 8);
    }

    private BusinessConflictException idempotencyConflict() {
        return new BusinessConflictException("IDEMPOTENCY_CONFLICT", "같은 요청 키에 다른 결제 링크 요청이 사용되었습니다.");
    }

    private BusinessConflictException notSettleable() {
        return new BusinessConflictException(
                "PAYMENT_TRANSACTION_NOT_SETTLEABLE", "환불 가능한 원 결제 거래를 찾을 수 없습니다.");
    }

    public record CustomerSession(String token, Instant expiresAt) {
    }

    private record PaymentContext(
            UUID reservationId,
            UUID hotelId,
            String status,
            String direction,
            long version,
            long differenceKrw,
            String currency,
            Instant settlementExpiresAt) {
    }

    private record ExistingAttempt(String requestHash) {
    }

    private record OriginalTransaction(
            UUID id,
            String provider,
            long capturedAmountKrw,
            long refundedAmountKrw,
            String currency,
            String gatewayTransactionId) {
    }

    private record TokenContext(UUID requestId, Instant expiresAt) {
    }

    private record CustomerContext(
            UUID sessionId,
            UUID reservationId,
            LocalDate previousCheckIn,
            LocalDate previousCheckOut,
            String previousRoomTypeName,
            String previousRatePlanName,
            long previousTotalKrw,
            LocalDate checkIn,
            LocalDate checkOut,
            String roomTypeName,
            String ratePlanName,
            long totalKrw,
            long differenceKrw,
            long additionalAmountKrw,
            String currency,
            Instant expiresAt,
            String status,
            UUID attemptId,
            String checkoutUrl) {
    }

    private record GatewayAttempt(
            UUID id,
            UUID requestId,
            UUID reservationId,
            String requestStatus,
            String adjustmentType,
            String provider,
            String status,
            long amountKrw,
            String currency,
            String gatewayTransactionId,
            UUID originalPaymentTransactionId) {
    }
}
