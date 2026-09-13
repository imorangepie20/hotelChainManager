package team.hotelchain.payment;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.reservation.BusinessConflictException;
import team.hotelchain.reservation.ReservationAccess;
import team.hotelchain.reservation.ReservationNotFoundException;

@Service
public class TestPaymentService {

    private final JdbcTemplate jdbc;
    private final ReservationAccess access;
    private final Clock clock;
    private final boolean changeSettlementEnabled;

    public TestPaymentService(
            JdbcTemplate jdbc,
            ReservationAccess access,
            Clock clock,
            @Value("${reservation.change.settlement-enabled:false}") boolean changeSettlementEnabled) {
        this.jdbc = jdbc;
        this.access = access;
        this.clock = clock;
        this.changeSettlementEnabled = changeSettlementEnabled;
    }

    @Transactional(noRollbackFor = ReservationExpiredException.class)
    public PaymentResult pay(UUID reservationId, String token, String idempotencyKey, PaymentOutcome outcome) {
        validate(idempotencyKey, outcome);
        String tokenHash = access.hashToken(token);
        PaymentReservation reservation = lockReservation(reservationId, tokenHash);

        ExistingPayment existing = findExisting(reservationId, idempotencyKey);
        String requestHash = access.sha256(outcome.name().getBytes(StandardCharsets.UTF_8));
        if (existing != null) {
            if (!existing.requestHash().equals(requestHash)) {
                throw new BusinessConflictException("IDEMPOTENCY_CONFLICT", "같은 요청 키에 다른 결제 결과가 사용되었습니다.");
            }
            return new PaymentResult(reservationId, existing.reservationStatus(), existing.paymentStatus());
        }

        if ("EXPIRED".equals(reservation.status())) {
            throw new ReservationExpiredException();
        }
        if ("CONFIRMED".equals(reservation.status())) {
            if (outcome != PaymentOutcome.SUCCESS) {
                throw new BusinessConflictException("RESERVATION_STATE_CONFLICT", "이미 결제가 완료된 예약입니다.");
            }
            insertOriginalTransaction(reservation);
            insertAttempt(reservationId, idempotencyKey, requestHash, outcome, "SUCCEEDED", "CONFIRMED");
            return new PaymentResult(reservationId, "CONFIRMED", "SUCCEEDED");
        }
        if (!"PENDING_PAYMENT".equals(reservation.status())) {
            throw new BusinessConflictException("RESERVATION_STATE_CONFLICT", "현재 상태에서 결제할 수 없습니다.");
        }

        lockInventory(reservation);
        if (!clock.instant().isBefore(reservation.expiresAt())) {
            releaseHold(reservation);
            jdbc.update("update reservation set status = 'EXPIRED' where id = ?", reservationId);
            insertAttempt(reservationId, idempotencyKey, requestHash, outcome, "EXPIRED", "EXPIRED");
            throw new ReservationExpiredException();
        }

        if (outcome == PaymentOutcome.FAILURE) {
            insertAttempt(reservationId, idempotencyKey, requestHash, outcome, "FAILED", "PENDING_PAYMENT");
            return new PaymentResult(reservationId, "PENDING_PAYMENT", "FAILED");
        }

        int updated = jdbc.update("""
                UPDATE inventory_day
                   SET held = held - ?, confirmed = confirmed + ?
                 WHERE room_type_id = ? AND stay_date >= ? AND stay_date < ? AND held >= ?
                """, reservation.rooms(), reservation.rooms(), reservation.roomTypeId(), reservation.checkIn(),
                reservation.checkOut(), reservation.rooms());
        if (updated != reservation.nights()) {
            throw new IllegalStateException("확보 재고가 예약 숙박일과 일치하지 않습니다.");
        }
        jdbc.update("update reservation set status = 'CONFIRMED' where id = ?", reservationId);
        insertOriginalTransaction(reservation);
        insertAttempt(reservationId, idempotencyKey, requestHash, outcome, "SUCCEEDED", "CONFIRMED");
        return new PaymentResult(reservationId, "CONFIRMED", "SUCCEEDED");
    }

    private PaymentReservation lockReservation(UUID reservationId, String tokenHash) {
        PaymentReservation reservation = jdbc.query("""
                SELECT id, room_type_id, check_in, check_out, rooms, status, expires_at,
                       total_krw, currency,
                       (SELECT count(*) FROM reservation_night rn WHERE rn.reservation_id = r.id) AS nights
                  FROM reservation r
                 WHERE id = ? AND management_token_hash = ?
                 FOR UPDATE
                """, rs -> rs.next() ? mapReservation(rs) : null, reservationId, tokenHash);
        if (reservation == null) {
            throw new ReservationNotFoundException();
        }
        return reservation;
    }

    private void lockInventory(PaymentReservation reservation) {
        jdbc.query("""
                SELECT stay_date FROM inventory_day
                 WHERE room_type_id = ? AND stay_date >= ? AND stay_date < ?
                 ORDER BY stay_date FOR UPDATE
                """, rs -> { }, reservation.roomTypeId(), reservation.checkIn(), reservation.checkOut());
    }

    private void releaseHold(PaymentReservation reservation) {
        int updated = jdbc.update("""
                UPDATE inventory_day SET held = held - ?
                 WHERE room_type_id = ? AND stay_date >= ? AND stay_date < ? AND held >= ?
                """, reservation.rooms(), reservation.roomTypeId(), reservation.checkIn(), reservation.checkOut(), reservation.rooms());
        if (updated != reservation.nights()) {
            throw new IllegalStateException("만료할 확보 재고가 예약 숙박일과 일치하지 않습니다.");
        }
    }

    private ExistingPayment findExisting(UUID reservationId, String idempotencyKey) {
        return jdbc.query("""
                SELECT request_hash, payment_status, reservation_status FROM payment_attempt
                 WHERE reservation_id = ? AND idempotency_key = ?
                """, rs -> rs.next() ? new ExistingPayment(rs.getString(1), rs.getString(2), rs.getString(3)) : null,
                reservationId, idempotencyKey);
    }

    private void insertAttempt(UUID reservationId, String idempotencyKey, String requestHash,
            PaymentOutcome outcome, String paymentStatus, String reservationStatus) {
        jdbc.update("""
                INSERT INTO payment_attempt
                    (id, reservation_id, idempotency_key, request_hash, outcome, payment_status, reservation_status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), reservationId, idempotencyKey, requestHash, outcome.name(), paymentStatus, reservationStatus);
    }

    private void insertOriginalTransaction(PaymentReservation reservation) {
        if (!changeSettlementEnabled) return;
        jdbc.update("""
                INSERT INTO payment_transaction (
                    id, reservation_id, provider, merchant_account, gateway_transaction_id,
                    transaction_type, captured_amount_krw, refunded_amount_krw, currency)
                VALUES (?, ?, 'FAKE', 'LOCAL', ?, 'ORIGINAL_CHARGE', ?, 0, ?)
                ON CONFLICT (provider, merchant_account, gateway_transaction_id) DO NOTHING
                """, UUID.randomUUID(), reservation.id(), "test-payment-" + reservation.id(),
                reservation.totalKrw(), reservation.currency());
    }

    private void validate(String idempotencyKey, PaymentOutcome outcome) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100 || outcome == null) {
            throw new IllegalArgumentException("결제 결과와 올바른 Idempotency-Key가 필요합니다.");
        }
    }

    private PaymentReservation mapReservation(ResultSet rs) throws SQLException {
        return new PaymentReservation(rs.getObject("id", UUID.class), rs.getObject("room_type_id", UUID.class),
                rs.getDate("check_in").toLocalDate(), rs.getDate("check_out").toLocalDate(), rs.getInt("rooms"),
                rs.getString("status"), rs.getTimestamp("expires_at").toInstant(), rs.getLong("total_krw"),
                rs.getString("currency").trim(), rs.getInt("nights"));
    }

    record PaymentReservation(UUID id, UUID roomTypeId, java.time.LocalDate checkIn, java.time.LocalDate checkOut,
            int rooms, String status, Instant expiresAt, long totalKrw, String currency, int nights) {
    }

    private record ExistingPayment(String requestHash, String paymentStatus, String reservationStatus) {
    }
}
