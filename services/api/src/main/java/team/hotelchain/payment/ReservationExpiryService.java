package team.hotelchain.payment;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ReservationExpiryService {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public ReservationExpiryService(JdbcTemplate jdbc, TransactionTemplate transactions, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.clock = clock;
    }

    public int expireDue(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("만료 처리 건수는 1~100이어야 합니다.");
        }
        List<UUID> ids = jdbc.queryForList("""
                SELECT id FROM reservation r
                 WHERE status = 'PENDING_PAYMENT' AND expires_at <= ?
                   AND NOT EXISTS (SELECT 1 FROM payment_provider_attempt p
                    WHERE p.reservation_id = r.id AND p.status IN ('APPROVING', 'UNKNOWN'))
                 ORDER BY expires_at, id LIMIT ?
                """, UUID.class, Timestamp.from(clock.instant()), limit);
        int expired = 0;
        for (UUID id : ids) {
            Boolean changed = transactions.execute(status -> expireOne(id));
            if (Boolean.TRUE.equals(changed)) {
                expired++;
            }
        }
        return expired;
    }

    private boolean expireOne(UUID id) {
        ExpiringReservation reservation = jdbc.query("""
                SELECT room_type_id, check_in, check_out, rooms, expires_at,
                       (SELECT count(*) FROM reservation_night rn WHERE rn.reservation_id = r.id) AS nights
                  FROM reservation r
                 WHERE id = ? AND status = 'PENDING_PAYMENT'
                 FOR UPDATE
                """, rs -> rs.next() ? new ExpiringReservation(rs.getObject(1, UUID.class),
                        rs.getDate(2).toLocalDate(), rs.getDate(3).toLocalDate(), rs.getInt(4),
                        rs.getTimestamp(5).toInstant(), rs.getInt(6)) : null, id);
        if (reservation == null || clock.instant().isBefore(reservation.expiresAt())) {
            return false;
        }
        Boolean protectedPayment = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM payment_provider_attempt
                 WHERE reservation_id = ? AND status IN ('APPROVING', 'UNKNOWN'))
                """, Boolean.class, id);
        if (Boolean.TRUE.equals(protectedPayment)) {
            return false;
        }
        jdbc.query("""
                SELECT stay_date FROM inventory_day
                 WHERE room_type_id = ? AND stay_date >= ? AND stay_date < ?
                 ORDER BY stay_date FOR UPDATE
                """, rs -> { }, reservation.roomTypeId(), reservation.checkIn(), reservation.checkOut());
        int updated = jdbc.update("""
                UPDATE inventory_day SET held = held - ?
                 WHERE room_type_id = ? AND stay_date >= ? AND stay_date < ? AND held >= ?
                """, reservation.rooms(), reservation.roomTypeId(), reservation.checkIn(), reservation.checkOut(), reservation.rooms());
        if (updated != reservation.nights()) {
            throw new IllegalStateException("만료할 확보 재고가 예약 숙박일과 일치하지 않습니다.");
        }
        jdbc.update("update reservation set status = 'EXPIRED' where id = ?", id);
        return true;
    }

    private record ExpiringReservation(UUID roomTypeId, java.time.LocalDate checkIn, java.time.LocalDate checkOut,
            int rooms, java.time.Instant expiresAt, int nights) {
    }
}
