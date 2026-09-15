package team.hotelchain.reservationchange;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.reservation.BusinessConflictException;
import team.hotelchain.reservation.ReservationNotFoundException;

@Service
public class ReservationChangeHoldService {
    private final JdbcTemplate jdbc;
    private final ReservationChangePolicy policy;
    private final Clock clock;

    public ReservationChangeHoldService(JdbcTemplate jdbc, ReservationChangePolicy policy, Clock clock) {
        this.jdbc = jdbc;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional
    public HoldResult acquire(UUID requestId, long expectedVersion) {
        UUID reservationId = reservationId(requestId);
        ReservationLock reservation = lockReservation(reservationId);
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from cancellation_attempt where reservation_id=? and (refund_status in ('PENDING','UNKNOWN') or (refund_status='FAILED' and exists(select 1 from toss_refund_command c where c.cancellation_attempt_id=cancellation_attempt.id))))",
                Boolean.class, reservationId))) {
            throw new BusinessConflictException("CANCELLATION_RECONCILIATION_REQUIRED", "예약 취소 환불을 먼저 완료하거나 조정해 주세요.");
        }
        ChangeLock request = lockRequest(requestId);
        if (request.version() != expectedVersion) {
            throw new BusinessConflictException(
                    "RESERVATION_CHANGE_VERSION_CONFLICT", "예약 변경 요청이 갱신되었습니다. 다시 확인해 주세요.");
        }
        if (!"APPROVED".equals(request.status())) {
            throw new BusinessConflictException(
                    "RESERVATION_CHANGE_STATE_CONFLICT", "승인된 예약 변경 요청만 재고를 확보할 수 있습니다.");
        }
        if (reservation.operationRevision() != request.baseOperationRevision()
                || !"CONFIRMED".equals(reservation.status())) {
            throw new BusinessConflictException(
                    "RESERVATION_REVISION_CONFLICT", "예약 조건이 변경되었습니다. 새 요청을 만들어 주세요.");
        }
        Integer existingRows = jdbc.queryForObject(
                "select count(*) from reservation_change_hold_day where request_id = ?", Integer.class, requestId);
        if (existingRows != null && existingRows > 0) {
            return currentResult(requestId, request.version());
        }

        List<InventoryRow> inventory = lockTargetInventory(request);
        int nights = Math.toIntExact(ChronoUnit.DAYS.between(request.targetCheckIn(), request.targetCheckOut()));
        if (inventory.size() != nights) {
            throw soldOut();
        }
        for (InventoryRow row : inventory) {
            if (!isExistingConfirmed(request, row.stayDate())
                    && row.capacity() - row.held() - row.confirmed() < request.rooms()) {
                throw soldOut();
            }
        }

        Instant expiresAt = clock.instant().plus(policy.holdTtl());
        int newHeldNights = 0;
        for (InventoryRow row : inventory) {
            boolean existingConfirmed = isExistingConfirmed(request, row.stayDate());
            String kind = existingConfirmed ? "EXISTING_CONFIRMED" : "NEW_HOLD";
            jdbc.update("""
                    insert into reservation_change_hold_day (
                        request_id, room_type_id, stay_date, rooms, hold_kind, status, expires_at)
                    values (?, ?, ?, ?, ?, 'HELD', ?)
                    """, requestId, request.targetRoomTypeId(), row.stayDate(), request.rooms(), kind,
                    Timestamp.from(expiresAt));
            if (!existingConfirmed) {
                int updated = jdbc.update("""
                        update inventory_day set held = held + ?
                        where room_type_id = ? and stay_date = ?
                          and capacity - held - confirmed >= ?
                        """, request.rooms(), request.targetRoomTypeId(), row.stayDate(), request.rooms());
                if (updated != 1) throw soldOut();
                newHeldNights++;
            }
        }
        jdbc.update("""
                update reservation_change_request
                set settlement_expires_at = ?, version = version + 1, updated_at = ?
                where id = ?
                """, Timestamp.from(expiresAt), Timestamp.from(clock.instant()), requestId);
        return new HoldResult(requestId, request.version() + 1, expiresAt, newHeldNights);
    }

    @Transactional
    public void release(UUID requestId, String reason) {
        UUID reservationId = reservationId(requestId);
        lockReservation(reservationId);
        lockRequest(requestId);
        List<HeldDay> heldDays = jdbc.query("""
                select room_type_id, stay_date, rooms, hold_kind, status
                from reservation_change_hold_day where request_id = ?
                order by room_type_id, stay_date for update
                """, this::mapHeldDay, requestId);
        Instant now = clock.instant();
        for (HeldDay day : heldDays) {
            if (!"HELD".equals(day.status())) continue;
            if ("NEW_HOLD".equals(day.holdKind())) {
                int updated = jdbc.update("""
                        update inventory_day set held = held - ?
                        where room_type_id = ? and stay_date = ? and held >= ?
                        """, day.rooms(), day.roomTypeId(), day.stayDate(), day.rooms());
                if (updated != 1) {
                    throw new IllegalStateException("반환할 변경 재고가 일치하지 않습니다.");
                }
            }
            jdbc.update("""
                    update reservation_change_hold_day
                    set status = 'RELEASED', released_at = ?
                    where request_id = ? and stay_date = ? and status = 'HELD'
                    """, Timestamp.from(now), requestId, day.stayDate());
        }
    }

    @Transactional
    public boolean expireNext() {
        DueRequest candidate = jdbc.query("""
                select request.id, request.reservation_id
                from reservation_change_request request
                join reservation booking on booking.id = request.reservation_id
                where (
                    request.status in ('PENDING_APPROVAL', 'APPROVED')
                        and request.approval_expires_at <= ?
                    or request.status in ('AWAITING_PAYMENT', 'REFUND_PENDING', 'READY_TO_APPLY', 'APPLYING')
                        and request.settlement_expires_at <= ?
                )
                order by coalesce(request.settlement_expires_at, request.approval_expires_at), request.id
                for update of booking skip locked
                limit 1
                """, rs -> rs.next() ? new DueRequest(
                        rs.getObject("id", UUID.class), rs.getObject("reservation_id", UUID.class)) : null,
                Timestamp.from(clock.instant()), Timestamp.from(clock.instant()));
        if (candidate == null) return false;
        ChangeLock request = lockRequest(candidate.requestId());
        if (!isDue(request)) return false;

        boolean uncertainFinancialResult = Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(
                    select 1 from payment_adjustment_attempt a
                    where request_id = ? and status in ('PROCESSING', 'SUCCEEDED', 'UNKNOWN')
                      and (provider not in ('TOSS_TEST','TOSS_LIVE') or adjustment_type <> 'CREATE_CHECKOUT'
                        or exists(select 1 from toss_adjustment_order o where o.attempt_id=a.id
                          and o.status in ('APPROVING','SUCCEEDED','UNKNOWN'))))
                """, Boolean.class, request.id()));
        String nextStatus = uncertainFinancialResult ? "RECONCILIATION_REQUIRED" : "EXPIRED";
        if (!uncertainFinancialResult) release(request.id(), "REQUEST_EXPIRED");
        jdbc.update("""
                update reservation_change_request
                set status = ?, version = version + 1, updated_at = ?
                where id = ?
                """, nextStatus, Timestamp.from(clock.instant()), request.id());
        jdbc.update("""
                insert into reservation_change_event (
                    id, request_id, event_type, from_status, to_status, payload)
                values (?, ?, ?, ?, ?, jsonb_build_object('reason', ?::text))
                """, UUID.randomUUID(), request.id(),
                uncertainFinancialResult ? "EXPIRY_REQUIRES_RECONCILIATION" : "REQUEST_EXPIRED",
                request.status(), nextStatus,
                uncertainFinancialResult ? "정산 결과를 확인해야 재고를 해제할 수 있습니다." : "요청 유효 시간이 만료되었습니다.");
        return true;
    }

    private boolean isDue(ChangeLock request) {
        Instant now = clock.instant();
        if (List.of("PENDING_APPROVAL", "APPROVED").contains(request.status())) {
            return !request.approvalExpiresAt().isAfter(now);
        }
        return request.settlementExpiresAt() != null && !request.settlementExpiresAt().isAfter(now)
                && List.of("AWAITING_PAYMENT", "REFUND_PENDING", "READY_TO_APPLY", "APPLYING")
                        .contains(request.status());
    }

    private HoldResult currentResult(UUID requestId, long version) {
        return jdbc.query("""
                select max(expires_at) as expires_at,
                       count(*) filter (where hold_kind = 'NEW_HOLD' and status = 'HELD') as new_nights
                from reservation_change_hold_day where request_id = ?
                """, rs -> {
            if (!rs.next() || rs.getTimestamp("expires_at") == null) throw new IllegalStateException("변경 재고 정보를 찾을 수 없습니다.");
            return new HoldResult(requestId, version, rs.getTimestamp("expires_at").toInstant(), rs.getInt("new_nights"));
        }, requestId);
    }

    private UUID reservationId(UUID requestId) {
        UUID reservationId = jdbc.query("select reservation_id from reservation_change_request where id = ?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, requestId);
        if (reservationId == null) throw new ReservationNotFoundException();
        return reservationId;
    }

    private ReservationLock lockReservation(UUID reservationId) {
        ReservationLock reservation = jdbc.query("""
                select id, status, operation_revision from reservation where id = ? for update
                """, rs -> rs.next() ? new ReservationLock(
                        rs.getObject("id", UUID.class), rs.getString("status"), rs.getLong("operation_revision")) : null,
                reservationId);
        if (reservation == null) throw new ReservationNotFoundException();
        return reservation;
    }

    private ChangeLock lockRequest(UUID requestId) {
        ChangeLock request = jdbc.query("""
                select id, reservation_id, status, version, base_operation_revision,
                       previous_check_in, previous_check_out, previous_room_type_id,
                       target_check_in, target_check_out, target_room_type_id, rooms,
                       approval_expires_at, settlement_expires_at
                from reservation_change_request where id = ? for update
                """, rs -> rs.next() ? mapChangeLock(rs) : null, requestId);
        if (request == null) throw new ReservationNotFoundException();
        return request;
    }

    private List<InventoryRow> lockTargetInventory(ChangeLock request) {
        return jdbc.query("""
                select stay_date, capacity, held, confirmed from inventory_day
                where room_type_id = ? and stay_date >= ? and stay_date < ?
                order by room_type_id, stay_date for update
                """, (rs, rowNumber) -> new InventoryRow(
                        rs.getDate("stay_date").toLocalDate(), rs.getInt("capacity"),
                        rs.getInt("held"), rs.getInt("confirmed")),
                request.targetRoomTypeId(), request.targetCheckIn(), request.targetCheckOut());
    }

    private boolean isExistingConfirmed(ChangeLock request, LocalDate date) {
        return request.previousRoomTypeId().equals(request.targetRoomTypeId())
                && !date.isBefore(request.previousCheckIn()) && date.isBefore(request.previousCheckOut());
    }

    private ChangeLock mapChangeLock(ResultSet rs) throws SQLException {
        Timestamp settlementExpiry = rs.getTimestamp("settlement_expires_at");
        return new ChangeLock(
                rs.getObject("id", UUID.class), rs.getObject("reservation_id", UUID.class),
                rs.getString("status"), rs.getLong("version"), rs.getLong("base_operation_revision"),
                rs.getDate("previous_check_in").toLocalDate(), rs.getDate("previous_check_out").toLocalDate(),
                rs.getObject("previous_room_type_id", UUID.class),
                rs.getDate("target_check_in").toLocalDate(), rs.getDate("target_check_out").toLocalDate(),
                rs.getObject("target_room_type_id", UUID.class), rs.getInt("rooms"),
                rs.getTimestamp("approval_expires_at").toInstant(),
                settlementExpiry == null ? null : settlementExpiry.toInstant());
    }

    private HeldDay mapHeldDay(ResultSet rs, int rowNumber) throws SQLException {
        return new HeldDay(
                rs.getObject("room_type_id", UUID.class), rs.getDate("stay_date").toLocalDate(),
                rs.getInt("rooms"), rs.getString("hold_kind"), rs.getString("status"));
    }

    private BusinessConflictException soldOut() {
        return new BusinessConflictException("SOLD_OUT", "선택한 숙박일의 객실 재고가 부족합니다.");
    }

    public record HoldResult(UUID requestId, long requestVersion, Instant expiresAt, int newHeldNights) {
    }

    private record ReservationLock(UUID id, String status, long operationRevision) {
    }

    private record ChangeLock(
            UUID id,
            UUID reservationId,
            String status,
            long version,
            long baseOperationRevision,
            LocalDate previousCheckIn,
            LocalDate previousCheckOut,
            UUID previousRoomTypeId,
            LocalDate targetCheckIn,
            LocalDate targetCheckOut,
            UUID targetRoomTypeId,
            int rooms,
            Instant approvalExpiresAt,
            Instant settlementExpiresAt) {
    }

    private record InventoryRow(LocalDate stayDate, int capacity, int held, int confirmed) {
    }

    private record HeldDay(UUID roomTypeId, LocalDate stayDate, int rooms, String holdKind, String status) {
    }

    private record DueRequest(UUID requestId, UUID reservationId) {
    }
}
