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

import team.hotelchain.reservationchange.ReservationChangeMutationGuard;

@Service
public class CancellationService {

    private static final String REQUEST_HASH_SOURCE = "CANCEL";

    private final JdbcTemplate jdbc;
    private final ReservationAccess access;
    private final TestRefundGateway refundGateway;
    private final Clock clock;
    private final ReservationChangeMutationGuard mutationGuard;

    public CancellationService(
            JdbcTemplate jdbc,
            ReservationAccess access,
            TestRefundGateway refundGateway,
            Clock clock,
            ReservationChangeMutationGuard mutationGuard) {
        this.jdbc = jdbc;
        this.access = access;
        this.refundGateway = refundGateway;
        this.clock = clock;
        this.mutationGuard = mutationGuard;
    }

    @Transactional(noRollbackFor = RefundFailedException.class)
    public CancellationResult cancel(UUID reservationId, String token, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        CancellationReservation reservation = lockReservation(reservationId, access.hashToken(token));
        return cancelLocked(reservationId, idempotencyKey, reservation, null);
    }

    @Transactional(readOnly = true)
    public CancellationPreview preview(UUID reservationId, String token) {
        CancellationReservation reservation = findReservation(reservationId, access.hashToken(token));
        var cutoff = cutoff(reservation);
        boolean confirmed = "CONFIRMED".equals(reservation.status());
        boolean beforeCutoff = clock.instant().isBefore(cutoff);
        String unavailableReason = !confirmed
                ? "확정된 예약만 취소할 수 있습니다."
                : beforeCutoff ? null : "취소 가능 시간이 지났습니다.";
        return new CancellationPreview(reservationId, reservation.status(), confirmed && beforeCutoff,
                confirmed && beforeCutoff ? reservation.total() : 0, "KRW", cutoff, reservation.timezone(), unavailableReason);
    }

    StaffCancellationPreview previewForStaff(UUID reservationId) {
        CancellationReservation reservation = findReservationForStaff(reservationId, false);
        var cutoff = cutoff(reservation);
        boolean confirmed = "CONFIRMED".equals(reservation.status());
        boolean beforeCutoff = clock.instant().isBefore(cutoff);
        String unavailableReason = !confirmed
                ? "확정된 예약만 취소할 수 있습니다."
                : beforeCutoff ? null : "취소 가능 시간이 지났습니다.";
        return new StaffCancellationPreview(
                reservationId,
                reservation.status(),
                confirmed && beforeCutoff,
                confirmed && beforeCutoff ? reservation.total() : 0,
                "KRW",
                cutoff,
                unavailableReason
        );
    }

    @Transactional(noRollbackFor = RefundFailedException.class)
    CancellationResult cancelForStaff(UUID reservationId, UUID staffId, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        return cancelLocked(reservationId, idempotencyKey, findReservationForStaff(reservationId, true), staffId);
    }

    private CancellationResult cancelLocked(UUID reservationId, String idempotencyKey,
            CancellationReservation reservation, UUID staffId) {
        String requestHashSource = staffId == null ? REQUEST_HASH_SOURCE : REQUEST_HASH_SOURCE + ":STAFF:" + staffId;
        String requestHash = access.sha256(requestHashSource.getBytes(StandardCharsets.UTF_8));
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
            if (staffId != null) {
                throw new BusinessConflictException(
                        "RESERVATION_STATE_CONFLICT", "확정된 예약만 취소할 수 있습니다.");
            }
            insertAttempt(reservationId, idempotencyKey, requestHash, reservation.total(), "SUCCEEDED", "CANCELLED", staffId);
            return new CancellationResult(reservationId, "CANCELLED", reservation.total(), "KRW");
        }
        if (!"CONFIRMED".equals(reservation.status())) {
            throw new BusinessConflictException("RESERVATION_STATE_CONFLICT", "확정된 예약만 취소할 수 있습니다.");
        }

        var cutoff = cutoff(reservation);
        if (!clock.instant().isBefore(cutoff)) {
            throw new CancellationNotAllowedException();
        }
        mutationGuard.assertCriticalMutationAllowed(reservationId);
        if (!refundGateway.refund(reservationId, reservation.total())) {
            insertAttempt(reservationId, idempotencyKey, requestHash, reservation.total(), "FAILED", "CONFIRMED", staffId);
            throw new RefundFailedException();
        }

        mutationGuard.prepareCriticalMutation(reservationId);
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
        insertAttempt(reservationId, idempotencyKey, requestHash, reservation.total(), "SUCCEEDED", "CANCELLED", staffId);
        mutationGuard.incrementRevision(reservationId);
        return new CancellationResult(reservationId, "CANCELLED", reservation.total(), "KRW");
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new IllegalArgumentException("올바른 Idempotency-Key가 필요합니다.");
        }
    }

    private java.time.Instant cutoff(CancellationReservation reservation) {
        return LocalDateTime.of(reservation.checkIn().minusDays(reservation.cutoffDays()), reservation.cutoffTime())
                .atZone(ZoneId.of(reservation.timezone())).toInstant();
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

    private CancellationReservation findReservation(UUID id, String tokenHash) {
        CancellationReservation reservation = jdbc.query("""
                SELECT id, room_type_id, check_in, check_out, rooms, status, total_krw,
                       COALESCE(policy_snapshot->>'timezone', 'Asia/Seoul') AS timezone,
                       COALESCE((policy_snapshot->>'refundCutoffDaysBefore')::integer, 1) AS cutoff_days,
                       COALESCE((policy_snapshot->>'refundCutoffLocalTime')::time, '18:00'::time) AS cutoff_time,
                       (SELECT count(*) FROM reservation_night rn WHERE rn.reservation_id = r.id) AS nights
                  FROM reservation r
                 WHERE id = ? AND management_token_hash = ?
                """, rs -> rs.next() ? mapReservation(rs) : null, id, tokenHash);
        if (reservation == null) throw new ReservationNotFoundException();
        return reservation;
    }

    private CancellationReservation findReservationForStaff(UUID id, boolean lock) {
        String sql = """
                SELECT id, room_type_id, check_in, check_out, rooms, status, total_krw,
                       COALESCE(policy_snapshot->>'timezone', 'Asia/Seoul') AS timezone,
                       COALESCE((policy_snapshot->>'refundCutoffDaysBefore')::integer, 1) AS cutoff_days,
                       COALESCE((policy_snapshot->>'refundCutoffLocalTime')::time, '18:00'::time) AS cutoff_time,
                       (SELECT count(*) FROM reservation_night rn WHERE rn.reservation_id = r.id) AS nights
                  FROM reservation r
                 WHERE id = ?
                """ + (lock ? " FOR UPDATE" : "");
        CancellationReservation reservation = jdbc.query(
                sql, rs -> rs.next() ? mapReservation(rs) : null, id);
        if (reservation == null) throw new ReservationNotFoundException();
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
            String refundStatus, String reservationStatus, UUID staffId) {
        if (staffId == null) {
            jdbc.update("""
                    INSERT INTO cancellation_attempt
                        (id, reservation_id, idempotency_key, request_hash, refund_amount_krw, refund_status, reservation_status)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), reservationId, key, hash, refund, refundStatus, reservationStatus);
            return;
        }
        jdbc.update("""
                INSERT INTO cancellation_attempt
                    (id, reservation_id, idempotency_key, request_hash, refund_amount_krw, refund_status,
                     reservation_status, actor_type, staff_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'STAFF', ?)
                """, UUID.randomUUID(), reservationId, key, hash, refund, refundStatus, reservationStatus, staffId);
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
