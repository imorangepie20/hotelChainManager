package team.hotelchain.reservation;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancellationService {

    private static final String REQUEST_HASH_SOURCE = "CANCEL";

    private final JdbcTemplate jdbc;
    private final ReservationAccess access;
    private final TestRefundGateway refundGateway;
    private final Clock clock;

    public CancellationService(JdbcTemplate jdbc, ReservationAccess access, TestRefundGateway refundGateway, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.refundGateway = refundGateway;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = RefundFailedException.class)
    public CancellationResult cancel(UUID reservationId, String token, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new IllegalArgumentException("올바른 Idempotency-Key가 필요합니다.");
        }
        CancellationReservation reservation = lockReservation(reservationId, access.hashToken(token));
        String requestHash = access.sha256(REQUEST_HASH_SOURCE.getBytes(StandardCharsets.UTF_8));
        ExistingCancellation existing = findExisting(reservationId, idempotencyKey);
        if (existing != null) {
            if (!existing.requestHash().equals(requestHash)) {
                throw new BusinessConflictException("IDEMPOTENCY_CONFLICT", "같은 요청 키에 다른 취소 내용이 사용되었습니다.");
            }
            if ("FAILED".equals(existing.refundStatus())) {
                throw new RefundFailedException();
            }
            return new CancellationResult(reservationId, existing.reservationStatus(), existing.refundAmount(), "KRW");
        }

        if ("CANCELLED".equals(reservation.status())) {
            insertAttempt(reservationId, idempotencyKey, requestHash, reservation.total(), "SUCCEEDED", "CANCELLED");
            return new CancellationResult(reservationId, "CANCELLED", reservation.total(), "KRW");
        }
        if (!"CONFIRMED".equals(reservation.status())) {
            throw new BusinessConflictException("RESERVATION_STATE_CONFLICT", "확정된 예약만 취소할 수 있습니다.");
        }

        var cutoff = LocalDateTime.of(reservation.checkIn().minusDays(reservation.cutoffDays()), reservation.cutoffTime())
                .atZone(ZoneId.of(reservation.timezone())).toInstant();
        if (!clock.instant().isBefore(cutoff)) {
            throw new CancellationNotAllowedException();
        }
        if (!refundGateway.refund(reservationId, reservation.total())) {
            insertAttempt(reservationId, idempotencyKey, requestHash, reservation.total(), "FAILED", "CONFIRMED");
            throw new RefundFailedException();
        }

        jdbc.query("""
                SELECT stay_date FROM inventory_day
                 WHERE room_type_id = ? AND stay_date >= ? AND stay_date < ?
                 ORDER BY stay_date FOR UPDATE
                """, rs -> { }, reservation.roomTypeId(), reservation.checkIn(), reservation.checkOut());
        int updated = jdbc.update("""
                UPDATE inventory_day SET confirmed = confirmed - ?
                 WHERE room_type_id = ? AND stay_date >= ? AND stay_date < ? AND confirmed >= ?
                """, reservation.rooms(), reservation.roomTypeId(), reservation.checkIn(), reservation.checkOut(), reservation.rooms());
        if (updated != reservation.nights()) {
            throw new IllegalStateException("취소할 확정 재고가 예약 숙박일과 일치하지 않습니다.");
        }
        jdbc.update("update reservation set status = 'CANCELLED' where id = ?", reservationId);
        insertAttempt(reservationId, idempotencyKey, requestHash, reservation.total(), "SUCCEEDED", "CANCELLED");
        return new CancellationResult(reservationId, "CANCELLED", reservation.total(), "KRW");
    }

    private CancellationReservation lockReservation(UUID id, String tokenHash) {
        CancellationReservation reservation = jdbc.query("""
                SELECT id, room_type_id, check_in, check_out, rooms, status, total_krw,
                       COALESCE(policy_snapshot->>'timezone', 'Asia/Seoul') AS timezone,
                       COALESCE((policy_snapshot->>'refundCutoffDaysBefore')::integer, 1) AS cutoff_days,
                       COALESCE((policy_snapshot->>'refundCutoffLocalTime')::time, '18:00'::time) AS cutoff_time,
                       (SELECT count(*) FROM reservation_night rn WHERE rn.reservation_id = r.id) AS nights
                  FROM reservation r
                 WHERE id = ? AND management_token_hash = ?
                 FOR UPDATE
                """, rs -> rs.next() ? mapReservation(rs) : null, id, tokenHash);
        if (reservation == null) {
            throw new ReservationNotFoundException();
        }
        return reservation;
    }

    private ExistingCancellation findExisting(UUID reservationId, String idempotencyKey) {
        return jdbc.query("""
                SELECT request_hash, refund_amount_krw, refund_status, reservation_status
                  FROM cancellation_attempt WHERE reservation_id = ? AND idempotency_key = ?
                """, rs -> rs.next() ? new ExistingCancellation(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getString(4)) : null,
                reservationId, idempotencyKey);
    }

    private void insertAttempt(UUID reservationId, String key, String hash, long refund,
            String refundStatus, String reservationStatus) {
        jdbc.update("""
                INSERT INTO cancellation_attempt
                    (id, reservation_id, idempotency_key, request_hash, refund_amount_krw, refund_status, reservation_status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), reservationId, key, hash, refund, refundStatus, reservationStatus);
    }

    private CancellationReservation mapReservation(ResultSet rs) throws SQLException {
        return new CancellationReservation(rs.getObject("id", UUID.class), rs.getObject("room_type_id", UUID.class),
                rs.getDate("check_in").toLocalDate(), rs.getDate("check_out").toLocalDate(), rs.getInt("rooms"),
                rs.getString("status"), rs.getLong("total_krw"), rs.getString("timezone"), rs.getInt("cutoff_days"),
                rs.getTime("cutoff_time").toLocalTime(), rs.getInt("nights"));
    }

    private record CancellationReservation(UUID id, UUID roomTypeId, LocalDate checkIn, LocalDate checkOut,
            int rooms, String status, long total, String timezone, int cutoffDays, LocalTime cutoffTime, int nights) {
    }

    private record ExistingCancellation(String requestHash, long refundAmount, String refundStatus,
            String reservationStatus) {
    }
}
