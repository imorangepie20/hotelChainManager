package team.hotelchain.reservationchange;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import team.hotelchain.reservation.ReservationService;

@RestController
@RequestMapping("/api/reservations/{id}/change-summary")
public class CustomerReservationChangeSummaryController {
    private final ReservationService reservations;
    private final JdbcTemplate jdbc;
    public CustomerReservationChangeSummaryController(ReservationService reservations, JdbcTemplate jdbc) {
        this.reservations = reservations;
        this.jdbc = jdbc;
    }
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Summary> get(@PathVariable UUID id, @RequestHeader("X-Reservation-Token") String token) {
        reservations.get(id, token);
        Summary summary = jdbc.query("""
                select r.reservation_id, r.status, r.target_check_in, r.target_check_out,
                       rt.name room_type_name, rp.name rate_plan_name, q.difference_krw, q.currency,
                       coalesce(r.settlement_expires_at, r.approval_expires_at) expires_at,
                       (select a.status from payment_adjustment_attempt a where a.request_id=r.id
                         and a.adjustment_type='REFUND_ORIGINAL' order by a.created_at desc limit 1) refund_status
                from reservation_change_request r join reservation_change_quote q on q.id=r.current_quote_id
                join room_type rt on rt.id=r.target_room_type_id join rate_plan rp on rp.id=r.target_rate_plan_id
                where r.reservation_id=? order by r.created_at desc, r.id desc limit 1
                """, rs -> rs.next() ? new Summary(rs.getObject("reservation_id", UUID.class), rs.getString("status"),
                    rs.getDate("target_check_in").toLocalDate(), rs.getDate("target_check_out").toLocalDate(),
                    rs.getString("room_type_name"), rs.getString("rate_plan_name"), rs.getLong("difference_krw"),
                    rs.getString("currency").trim(), rs.getTimestamp("expires_at").toInstant(), rs.getString("refund_status")) : null, id);
        return summary == null ? ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()
            : ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(summary);
    }
    public record Summary(UUID reservationId, String status, LocalDate checkIn, LocalDate checkOut,
        String roomTypeName, String ratePlanName, long differenceKrw, String currency, Instant expiresAt, String refundStatus) {}
}
