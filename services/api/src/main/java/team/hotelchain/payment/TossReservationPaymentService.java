package team.hotelchain.payment;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import team.hotelchain.payment.TossPaymentsClient.ConfirmCommand;
import team.hotelchain.payment.TossPaymentsClient.ProviderPayment;
import team.hotelchain.payment.TossPaymentsClient.ProviderStatus;
import team.hotelchain.payment.TossReservationPaymentView.CheckoutView;
import team.hotelchain.payment.TossReservationPaymentView.ConfirmPaymentRequest;
import team.hotelchain.payment.TossReservationPaymentView.StatusView;
import team.hotelchain.reservation.BusinessConflictException;
import team.hotelchain.reservation.ReservationAccess;
import team.hotelchain.reservation.ReservationNotFoundException;

@Service
@ConditionalOnProperty(name = "payment.provider", havingValue = "toss-test")
public class TossReservationPaymentService {
    private final JdbcTemplate jdbc;
    private final ReservationAccess access;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final TossPaymentsProperties properties;
    private final TossPaymentsClient provider;

    public TossReservationPaymentService(JdbcTemplate jdbc, ReservationAccess access, TransactionTemplate transactions,
            Clock clock, TossPaymentsProperties properties, TossPaymentsClient provider) {
        this.jdbc = jdbc;
        this.access = access;
        this.transactions = transactions;
        this.clock = clock;
        this.properties = properties;
        this.provider = provider;
    }

    public CheckoutView checkout(UUID reservationId, String token, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new IllegalArgumentException("올바른 Idempotency-Key가 필요합니다.");
        }
        String tokenHash = access.hashToken(token);
        String storedKey = access.sha256((reservationId + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8));
        return transactions.execute(tx -> {
            Reservation reservation = lockReservation(reservationId, tokenHash);
            requirePending(reservation);
            requireUnexpired(reservation);
            requireAmount(reservation);
            Attempt attempt = findAttempt(reservationId, "idempotency_key = ?", storedKey);
            if (attempt == null) attempt = findAttempt(reservationId, "status in ('NEW','APPROVING','UNKNOWN')", null);
            if (attempt == null) {
                UUID id = UUID.randomUUID();
                String orderId = "hotel_" + id.toString().replace("-", "");
                jdbc.update("""
                        INSERT INTO payment_provider_attempt
                            (id, reservation_id, provider, merchant_account, order_id, idempotency_key, amount_krw, currency, status)
                        VALUES (?, ?, 'TOSS_TEST', ?, ?, ?, ?, 'KRW', 'NEW')
                        """, id, reservationId, properties.merchantAccount(), orderId, storedKey, reservation.totalKrw());
                attempt = findAttempt(reservationId, "order_id = ?", orderId);
            }
            String resultUrl = properties.customerOrigin() + "/reservations/" + reservationId + "/payment-result";
            return new CheckoutView(attempt.orderId(), attempt.amountKrw(), "KRW", properties.clientKey(),
                    resultUrl + "?result=success", resultUrl + "?result=fail", "토스 테스트 결제 · 실제 과금 없음");
        });
    }

    public StatusView status(UUID reservationId, String token) {
        String tokenHash = access.hashToken(token);
        return transactions.execute(tx -> {
            Reservation reservation = lockReservation(reservationId, tokenHash);
            return view(reservation, findAttempt(reservationId, "true", null));
        });
    }

    public StatusView confirm(UUID reservationId, String token, ConfirmPaymentRequest request) {
        validate(request);
        String tokenHash = access.hashToken(token);
        Claim claim = transactions.execute(tx -> claim(reservationId, tokenHash, request));
        if (!claim.execute()) return claim.view();

        // 승인/조회는 예약·재고 잠금을 보유하지 않은 상태에서만 호출한다.
        ProviderPayment result;
        try {
            result = claim.lookup() ? provider.lookup(claim.attempt().paymentKey())
                    : provider.confirm(new ConfirmCommand(claim.attempt().paymentKey(), claim.attempt().orderId(),
                            claim.attempt().amountKrw(), "KRW", claim.attempt().idempotencyKey()));
        } catch (RuntimeException exception) {
            return markUnknown(reservationId, tokenHash, request.orderId());
        }
        try {
            return transactions.execute(tx -> apply(reservationId, tokenHash, request.orderId(), result));
        } catch (RuntimeException exception) {
            // PG 성공 후 내부 적용 실패도 원 거래를 조회하여 복구하며 새 승인 키를 만들지 않는다.
            return markUnknown(reservationId, tokenHash, request.orderId());
        }
    }

    /** 저장 주문의 webhook 힌트는 서버 인증 조회만 실행한다. 새 승인은 시작하지 않는다. */
    public void reconcileStoredOrder(String orderId) {
        var rows=jdbc.queryForList("""
                select a.reservation_id,a.payment_key,r.management_token_hash
                from payment_provider_attempt a join reservation r on r.id=a.reservation_id
                where a.provider='TOSS_TEST' and a.order_id=? and a.payment_key is not null
                  and a.status in ('APPROVING','UNKNOWN')
                """,orderId);
        if(rows.isEmpty())return;
        var row=rows.getFirst();
        UUID reservationId=(UUID)row.get("reservation_id");
        String tokenHash=(String)row.get("management_token_hash");
        try {
            ProviderPayment result=provider.lookup((String)row.get("payment_key"));
            transactions.executeWithoutResult(t -> apply(reservationId,tokenHash,orderId,result));
        } catch(RuntimeException ex) {markUnknown(reservationId,tokenHash,orderId);}
    }

    private Claim claim(UUID reservationId, String tokenHash, ConfirmPaymentRequest request) {
        Reservation reservation = lockReservation(reservationId, tokenHash);
        Attempt attempt = findAttempt(reservationId, "order_id = ?", request.orderId());
        if (attempt == null) throw conflict("PAYMENT_ORDER_CONFLICT", "저장된 결제 주문과 일치하지 않습니다.");
        if (request.amountKrw() != attempt.amountKrw()) throw conflict("PAYMENT_AMOUNT_CONFLICT", "결제 금액이 저장된 금액과 다릅니다.");
        if (attempt.paymentKey() != null && !attempt.paymentKey().equals(request.paymentKey())) {
            throw conflict("PAYMENT_KEY_CONFLICT", "저장된 결제 키와 일치하지 않습니다.");
        }
        if ("SUCCEEDED".equals(attempt.status())) return new Claim(attempt, false, false, view(reservation, attempt));
        requirePending(reservation);
        requireAmount(reservation);
        if (reservation.totalKrw() != attempt.amountKrw() || !properties.merchantAccount().equals(attempt.merchant())) {
            throw conflict("PAYMENT_SNAPSHOT_CONFLICT", "저장된 결제 금액 또는 상점 설정이 일치하지 않습니다.");
        }
        if ("APPROVING".equals(attempt.status()) || "FAILED".equals(attempt.status())) {
            return new Claim(attempt, false, false, view(reservation, attempt));
        }
        boolean lookup = "UNKNOWN".equals(attempt.status());
        if (!lookup) requireUnexpired(reservation);
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> { }, "toss-payment-key:" + request.paymentKey());
        Boolean keyTaken = jdbc.queryForObject("""
                select exists (select 1 from payment_provider_attempt
                 where provider = 'TOSS_TEST' and payment_key = ? and id <> ?)
                """, Boolean.class, request.paymentKey(), attempt.id());
        if (Boolean.TRUE.equals(keyTaken)) throw conflict("PAYMENT_KEY_CONFLICT", "다른 주문에 사용된 결제 키입니다.");
        jdbc.update("update payment_provider_attempt set status = 'APPROVING', payment_key = ?, updated_at = CURRENT_TIMESTAMP where id = ?",
                request.paymentKey(), attempt.id());
        Attempt approving = findAttempt(reservationId, "order_id = ?", request.orderId());
        return new Claim(approving, true, lookup, view(reservation, approving));
    }

    private StatusView apply(UUID reservationId, String tokenHash, String orderId, ProviderPayment result) {
        Reservation reservation = lockReservation(reservationId, tokenHash);
        Attempt attempt = findAttempt(reservationId, "order_id = ?", orderId);
        if ("SUCCEEDED".equals(attempt.status())) return view(reservation, attempt);
        String status = "UNKNOWN";
        if (result != null && result.status() == ProviderStatus.FAILED) status = "FAILED";
        if (matches(attempt, result) && "PENDING_PAYMENT".equals(reservation.status())
                && reservation.totalKrw() == attempt.amountKrw() && "KRW".equals(reservation.currency())) {
            jdbc.query("""
                    SELECT stay_date FROM inventory_day
                     WHERE room_type_id = ? AND stay_date >= ? AND stay_date < ?
                     ORDER BY stay_date FOR UPDATE
                    """, rs -> { }, reservation.roomTypeId(), reservation.checkIn(), reservation.checkOut());
            int updated = jdbc.update("""
                    UPDATE inventory_day SET held = held - ?, confirmed = confirmed + ?
                     WHERE room_type_id = ? AND stay_date >= ? AND stay_date < ? AND held >= ?
                    """, reservation.rooms(), reservation.rooms(), reservation.roomTypeId(), reservation.checkIn(),
                    reservation.checkOut(), reservation.rooms());
            if (updated != reservation.nights()) throw new IllegalStateException("확보 재고가 예약 숙박일과 일치하지 않습니다.");
            jdbc.update("""
                    INSERT INTO payment_transaction (id, reservation_id, provider, merchant_account, gateway_transaction_id,
                        transaction_type, captured_amount_krw, refunded_amount_krw, currency)
                    VALUES (?, ?, 'TOSS_TEST', ?, ?, 'ORIGINAL_CHARGE', ?, 0, 'KRW')
                    """, UUID.randomUUID(), reservationId, attempt.merchant(), attempt.paymentKey(), attempt.amountKrw());
            jdbc.update("update reservation set status = 'CONFIRMED' where id = ?", reservationId);
            status = "SUCCEEDED";
        }
        jdbc.update("""
                update payment_provider_attempt set status = ?, provider_event_id = ?, updated_at = CURRENT_TIMESTAMP where id = ?
                """, status, result == null ? null : result.transactionKey(), attempt.id());
        return view(lockReservation(reservationId, tokenHash), findAttempt(reservationId, "order_id = ?", orderId));
    }

    private boolean matches(Attempt attempt, ProviderPayment result) {
        return result != null && result.status() == ProviderStatus.DONE
                && Objects.equals(attempt.paymentKey(), result.paymentKey())
                && Objects.equals(attempt.orderId(), result.orderId()) && attempt.amountKrw() == result.amountKrw()
                && "KRW".equals(result.currency()) && properties.merchantAccount().equals(attempt.merchant());
    }

    private StatusView markUnknown(UUID reservationId, String tokenHash, String orderId) {
        return transactions.execute(tx -> {
            Reservation reservation = lockReservation(reservationId, tokenHash);
            Attempt attempt = findAttempt(reservationId, "order_id = ?", orderId);
            if (!"SUCCEEDED".equals(attempt.status())) {
                jdbc.update("update payment_provider_attempt set status = 'UNKNOWN', updated_at = CURRENT_TIMESTAMP where id = ?", attempt.id());
            }
            return view(reservation, findAttempt(reservationId, "order_id = ?", orderId));
        });
    }

    private Reservation lockReservation(UUID reservationId, String tokenHash) {
        Reservation reservation = jdbc.query("""
                SELECT id, room_type_id, check_in, check_out, rooms, status, expires_at, total_krw, currency,
                    (SELECT count(*) FROM reservation_night rn WHERE rn.reservation_id = r.id) AS nights
                  FROM reservation r WHERE id = ? AND management_token_hash = ? FOR UPDATE
                """, rs -> rs.next() ? new Reservation(rs.getObject("id", UUID.class), rs.getObject("room_type_id", UUID.class),
                        rs.getDate("check_in").toLocalDate(), rs.getDate("check_out").toLocalDate(), rs.getInt("rooms"),
                        rs.getString("status"), rs.getTimestamp("expires_at").toInstant(), rs.getLong("total_krw"),
                        rs.getString("currency").trim(), rs.getInt("nights")) : null, reservationId, tokenHash);
        if (reservation == null) throw new ReservationNotFoundException();
        return reservation;
    }

    private Attempt findAttempt(UUID reservationId, String predicate, String value) {
        String sql = "select * from payment_provider_attempt where reservation_id = ? and provider = 'TOSS_TEST' and "
                + predicate + " order by created_at desc, id desc limit 1";
        return value == null ? jdbc.query(sql, rs -> rs.next() ? mapAttempt(rs) : null, reservationId)
                : jdbc.query(sql, rs -> rs.next() ? mapAttempt(rs) : null, reservationId, value);
    }

    private Attempt mapAttempt(ResultSet rs) throws SQLException {
        return new Attempt(rs.getObject("id", UUID.class), rs.getString("order_id"), rs.getString("payment_key"),
                rs.getString("idempotency_key"), rs.getLong("amount_krw"), rs.getString("merchant_account"), rs.getString("status"));
    }
    private StatusView view(Reservation reservation, Attempt attempt) {
        return new StatusView(reservation.id(), attempt == null ? null : attempt.orderId(), reservation.status(),
                attempt == null ? "NOT_STARTED" : attempt.status());
    }
    private void requirePending(Reservation reservation) {
        if ("EXPIRED".equals(reservation.status())) throw new ReservationExpiredException();
        if (!"PENDING_PAYMENT".equals(reservation.status())) throw conflict("RESERVATION_STATE_CONFLICT", "현재 상태에서 결제할 수 없습니다.");
    }
    private void requireUnexpired(Reservation reservation) {
        if (!clock.instant().isBefore(reservation.expiresAt())) throw new ReservationExpiredException();
    }
    private void requireAmount(Reservation reservation) {
        if (reservation.totalKrw() <= 0 || !"KRW".equals(reservation.currency())) throw conflict("PAYMENT_AMOUNT_CONFLICT", "양수 KRW 결제 금액이 필요합니다.");
    }
    private void validate(ConfirmPaymentRequest request) {
        if (request == null || request.orderId() == null || request.orderId().isBlank() || request.orderId().length() > 64
                || request.paymentKey() == null || !request.paymentKey().matches("[A-Za-z0-9_-]{1,160}") || request.amountKrw() == null) {
            throw new IllegalArgumentException("결제 주문, 결제 키와 결제 금액이 필요합니다.");
        }
    }
    private BusinessConflictException conflict(String code, String message) { return new BusinessConflictException(code, message); }
    private record Reservation(UUID id, UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int rooms,
            String status, Instant expiresAt, long totalKrw, String currency, int nights) { }
    private record Attempt(UUID id, String orderId, String paymentKey, String idempotencyKey, long amountKrw, String merchant, String status) { }
    private record Claim(Attempt attempt, boolean execute, boolean lookup, StatusView view) { }
}
