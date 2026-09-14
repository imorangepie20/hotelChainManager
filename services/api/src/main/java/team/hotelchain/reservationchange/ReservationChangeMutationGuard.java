package team.hotelchain.reservationchange;

import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import team.hotelchain.reservation.BusinessConflictException;

@Component
public class ReservationChangeMutationGuard {
    private static final Set<String> SETTLEMENT_ACTIVE = Set.of(
            "AWAITING_PAYMENT",
            "REFUND_PENDING",
            "READY_TO_APPLY",
            "APPLYING",
            "RECONCILIATION_REQUIRED");

    private final JdbcTemplate jdbc;

    public ReservationChangeMutationGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void prepareCriticalMutation(UUID reservationId) {
        ActiveChange active = lockActiveChange(reservationId);
        if (active == null) return;
        if (SETTLEMENT_ACTIVE.contains(active.status())) {
            throw activeSettlementConflict();
        }
        jdbc.update("""
                update reservation_change_request
                set status = 'EXPIRED', error_code = 'RESERVATION_CHANGED',
                    error_summary = '예약 조건이 변경되어 요청이 만료되었습니다.',
                    version = version + 1, updated_at = current_timestamp
                where id = ?
                """, active.id());
        jdbc.update("""
                insert into reservation_change_event (
                    id, request_id, event_type, from_status, to_status, payload)
                values (?, ?, 'REQUEST_EXPIRED', ?, 'EXPIRED',
                        jsonb_build_object('reason', 'RESERVATION_CHANGED'))
                """, UUID.randomUUID(), active.id(), active.status());
    }

    public void assertCriticalMutationAllowed(UUID reservationId) {
        ActiveChange active = lockActiveChange(reservationId);
        if (active != null && SETTLEMENT_ACTIVE.contains(active.status())) {
            throw activeSettlementConflict();
        }
    }

    public void rejectOperationalTransitionWhenActive(UUID reservationId) {
        if (lockActiveChange(reservationId) != null) {
            throw activeSettlementConflict();
        }
    }

    public void incrementRevision(UUID reservationId) {
        int updated = jdbc.update("""
                update reservation set operation_revision = operation_revision + 1 where id = ?
                """, reservationId);
        if (updated != 1) {
            throw new IllegalStateException("예약 operation revision을 갱신할 수 없습니다.");
        }
    }

    private ActiveChange lockActiveChange(UUID reservationId) {
        if (Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from cancellation_attempt where reservation_id=? and refund_status in ('PENDING','UNKNOWN'))",
                Boolean.class,reservationId))) {
            throw new BusinessConflictException("CANCELLATION_RECONCILIATION_REQUIRED", "예약 취소 환불을 먼저 완료하거나 조정해 주세요.");
        }
        return jdbc.query("""
                select id, status
                from reservation_change_request
                where reservation_id = ?
                  and status not in ('COMPLETED', 'REJECTED', 'CANCELLED', 'EXPIRED')
                for update
                """, rs -> rs.next()
                        ? new ActiveChange(rs.getObject("id", UUID.class), rs.getString("status"))
                        : null, reservationId);
    }

    private BusinessConflictException activeSettlementConflict() {
        return new BusinessConflictException(
                "RESERVATION_CHANGE_SETTLEMENT_ACTIVE",
                "예약 변경 정산을 먼저 완료하거나 조정해 주세요.");
    }

    private record ActiveChange(UUID id, String status) {
    }
}
