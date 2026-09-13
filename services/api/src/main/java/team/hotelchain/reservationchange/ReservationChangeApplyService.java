package team.hotelchain.reservationchange;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.reservation.BusinessConflictException;
import team.hotelchain.reservation.ReservationNotFoundException;

@Service
public class ReservationChangeApplyService {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ReservationChangeApplyService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public ReservationChangeApplyResult apply(UUID requestId, UUID claimToken) {
        UUID reservationId = jdbc.query(
                "select reservation_id from reservation_change_request where id = ?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, requestId);
        if (reservationId == null) throw new ReservationNotFoundException();
        LockedReservation reservation = lockReservation(reservationId);
        LockedChange request = lockRequest(requestId, claimToken);
        verifyInvariants(reservation, request);

        List<HeldDay> holds = lockHolds(requestId);
        Map<InventoryKey, InventoryRow> inventory = lockInventory(reservation, request);
        applyInventory(reservation, request, holds, inventory);
        replaceReservation(reservation, request);

        Instant completedAt = clock.instant();
        jdbc.update("""
                update reservation_change_hold_day
                set status = 'APPLIED'
                where request_id = ? and status = 'HELD'
                """, requestId);
        jdbc.update("""
                insert into reservation_stay_change (
                    id, reservation_id, idempotency_key, request_hash, staff_id,
                    previous_room_type_id, room_type_id, previous_rate_plan_id, rate_plan_id,
                    previous_check_in, previous_check_out, check_in, check_out,
                    previous_total_krw, total_krw, difference_krw, change_request_id)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (change_request_id) do nothing
                """, UUID.randomUUID(), reservation.id(), "change-request:" + request.id(), request.requestHash(),
                request.requestedBy(), request.previousRoomTypeId(), request.targetRoomTypeId(),
                request.previousRatePlanId(), request.targetRatePlanId(), request.previousCheckIn(),
                request.previousCheckOut(), request.targetCheckIn(), request.targetCheckOut(),
                request.previousTotalKrw(), request.totalKrw(), request.differenceKrw(), request.id());
        jdbc.update("""
                update reservation_change_request
                set status = 'COMPLETED', version = version + 1,
                    apply_claim_token = null, apply_lease_expires_at = null, updated_at = ?
                where id = ? and status = 'APPLYING' and apply_claim_token = ?
                """, Timestamp.from(completedAt), request.id(), claimToken);
        jdbc.update("""
                insert into reservation_change_event (
                    id, request_id, event_type, from_status, to_status, dedupe_key, payload)
                values (?, ?, 'CHANGE_APPLIED', 'APPLYING', 'COMPLETED', ?, '{}'::jsonb)
                on conflict (dedupe_key) where dedupe_key is not null do nothing
                """, UUID.randomUUID(), request.id(), "apply:" + request.id());
        return new ReservationChangeApplyResult(
                request.id(), reservation.id(), reservation.operationRevision() + 1, completedAt);
    }

    private void verifyInvariants(LockedReservation reservation, LockedChange request) {
        if (!"CONFIRMED".equals(reservation.status()) || reservation.assignments() > 0
                || reservation.operationRevision() != request.baseOperationRevision()) {
            throw conflict("RESERVATION_REVISION_CONFLICT", "예약 조건이 변경되어 자동 적용할 수 없습니다.");
        }
        LocalDate today = LocalDate.now(clock.withZone(java.time.ZoneId.of(reservation.timezone())));
        if (!today.isBefore(reservation.checkIn()) || !today.isBefore(request.targetCheckIn())) {
            throw conflict("RESERVATION_STAY_CHANGE_TOO_LATE", "체크인 당일에는 예약 변경을 적용할 수 없습니다.");
        }
        if (!"NONE".equals(request.direction())) {
            Boolean succeeded = jdbc.queryForObject("""
                    select exists(select 1 from payment_adjustment_attempt
                                  where request_id = ? and status = 'SUCCEEDED')
                    """, Boolean.class, request.id());
            if (!Boolean.TRUE.equals(succeeded)) {
                throw conflict("SETTLEMENT_NOT_COMPLETED", "정산 성공이 확인되지 않아 변경을 적용할 수 없습니다.");
            }
        }
    }

    private void applyInventory(
            LockedReservation reservation,
            LockedChange request,
            List<HeldDay> holds,
            Map<InventoryKey, InventoryRow> inventory) {
        Map<InventoryKey, HeldDay> holdByKey = new LinkedHashMap<>();
        for (HeldDay hold : holds) holdByKey.put(new InventoryKey(hold.roomTypeId(), hold.stayDate()), hold);
        int targetNights = 0;
        for (LocalDate date = request.targetCheckIn(); date.isBefore(request.targetCheckOut()); date = date.plusDays(1)) {
            InventoryKey key = new InventoryKey(request.targetRoomTypeId(), date);
            HeldDay hold = holdByKey.get(key);
            if (hold == null || !"HELD".equals(hold.status()) || !hold.expiresAt().isAfter(clock.instant())) {
                throw conflict("CHANGE_HOLD_INVALID", "대상 객실 재고 확보가 만료되었거나 일치하지 않습니다.");
            }
            targetNights++;
        }
        if (holds.size() != targetNights) {
            throw conflict("CHANGE_HOLD_INVALID", "대상 객실 재고 확보 일수가 일치하지 않습니다.");
        }

        for (Map.Entry<InventoryKey, InventoryRow> entry : inventory.entrySet()) {
            InventoryKey key = entry.getKey();
            InventoryRow row = entry.getValue();
            boolean oldStay = key.roomTypeId().equals(reservation.roomTypeId())
                    && !key.stayDate().isBefore(reservation.checkIn())
                    && key.stayDate().isBefore(reservation.checkOut());
            boolean targetStay = key.roomTypeId().equals(request.targetRoomTypeId())
                    && !key.stayDate().isBefore(request.targetCheckIn())
                    && key.stayDate().isBefore(request.targetCheckOut());
            int confirmedDelta = (targetStay ? request.rooms() : 0) - (oldStay ? reservation.rooms() : 0);
            HeldDay hold = holdByKey.get(key);
            int heldDelta = targetStay && hold != null && "NEW_HOLD".equals(hold.holdKind())
                    ? -request.rooms() : 0;
            if (oldStay && row.confirmed() < reservation.rooms()) {
                throw new IllegalStateException("기존 확정 재고가 예약 숙박일과 일치하지 않습니다.");
            }
            if (heldDelta < 0 && row.held() < request.rooms()) {
                throw new IllegalStateException("적용할 변경 확보 재고가 일치하지 않습니다.");
            }
            int updated = jdbc.update("""
                    update inventory_day
                    set held = held + ?, confirmed = confirmed + ?
                    where room_type_id = ? and stay_date = ?
                      and held + ? >= 0 and confirmed + ? >= 0
                      and held + confirmed + ? + ? <= capacity
                    """, heldDelta, confirmedDelta, key.roomTypeId(), key.stayDate(),
                    heldDelta, confirmedDelta, heldDelta, confirmedDelta);
            if (updated != 1) throw new IllegalStateException("예약 변경 재고 적용 결과가 유효하지 않습니다.");
        }
    }

    private void replaceReservation(LockedReservation reservation, LockedChange request) {
        int updated = jdbc.update("""
                update reservation
                set room_type_id = ?, rate_plan_id = ?, check_in = ?, check_out = ?,
                    adults = ?, children = ?, total_krw = ?, operation_revision = operation_revision + 1
                where id = ? and operation_revision = ?
                """, request.targetRoomTypeId(), request.targetRatePlanId(), request.targetCheckIn(),
                request.targetCheckOut(), request.adults(), request.children(), request.totalKrw(),
                reservation.id(), request.baseOperationRevision());
        if (updated != 1) throw conflict("RESERVATION_REVISION_CONFLICT", "예약 조건이 변경되었습니다.");
        jdbc.update("delete from reservation_night where reservation_id = ?", reservation.id());
        List<NightRow> nights = jdbc.query("""
                select stay_date, amount_krw from reservation_change_quote_night
                where quote_id = ? order by stay_date
                """, (rs, rowNumber) -> new NightRow(
                        rs.getDate("stay_date").toLocalDate(), rs.getInt("amount_krw")), request.quoteId());
        for (NightRow night : nights) {
            jdbc.update("insert into reservation_night values (?, ?, ?)",
                    reservation.id(), night.stayDate(), night.amountKrw());
        }
    }

    private LockedReservation lockReservation(UUID reservationId) {
        LockedReservation value = jdbc.query("""
                select reservation.id, reservation.room_type_id, reservation.rate_plan_id,
                       reservation.check_in, reservation.check_out, reservation.rooms,
                       reservation.status, reservation.operation_revision, hotel.timezone,
                       (select count(*) from reservation_room_assignment assignment
                        where assignment.reservation_id = reservation.id) as assignments
                from reservation
                join room_type on room_type.id = reservation.room_type_id
                join hotel on hotel.id = room_type.hotel_id
                where reservation.id = ? for update of reservation
                """, rs -> rs.next() ? new LockedReservation(
                        rs.getObject("id", UUID.class), rs.getObject("room_type_id", UUID.class),
                        rs.getObject("rate_plan_id", UUID.class), rs.getDate("check_in").toLocalDate(),
                        rs.getDate("check_out").toLocalDate(), rs.getInt("rooms"), rs.getString("status"),
                        rs.getLong("operation_revision"), rs.getString("timezone"), rs.getInt("assignments")) : null,
                reservationId);
        if (value == null) throw new ReservationNotFoundException();
        return value;
    }

    private LockedChange lockRequest(UUID requestId, UUID claimToken) {
        LockedChange value = jdbc.query("""
                select request.id, request.status, request.apply_claim_token,
                       request.base_operation_revision, request.settlement_direction,
                       request.requested_by, request.request_hash,
                       request.previous_check_in, request.previous_check_out,
                       request.previous_room_type_id, request.previous_rate_plan_id,
                       request.target_check_in, request.target_check_out,
                       request.target_room_type_id, request.target_rate_plan_id,
                       request.rooms, request.adults, request.children, request.current_quote_id,
                       quote.previous_total_krw, quote.total_krw, quote.difference_krw
                from reservation_change_request request
                join reservation_change_quote quote on quote.id = request.current_quote_id
                where request.id = ? for update of request
                """, rs -> rs.next() ? mapChange(rs) : null, requestId);
        if (value == null) throw new ReservationNotFoundException();
        if (!"APPLYING".equals(value.status()) || !claimToken.equals(value.claimToken())) {
            throw conflict("APPLY_CLAIM_LOST", "예약 변경 적용 작업의 소유권이 만료되었습니다.");
        }
        return value;
    }

    private List<HeldDay> lockHolds(UUID requestId) {
        return jdbc.query("""
                select room_type_id, stay_date, rooms, hold_kind, status, expires_at
                from reservation_change_hold_day where request_id = ?
                order by room_type_id, stay_date for update
                """, (rs, rowNumber) -> new HeldDay(
                        rs.getObject("room_type_id", UUID.class), rs.getDate("stay_date").toLocalDate(),
                        rs.getInt("rooms"), rs.getString("hold_kind"), rs.getString("status"),
                        rs.getTimestamp("expires_at").toInstant()), requestId);
    }

    private Map<InventoryKey, InventoryRow> lockInventory(
            LockedReservation reservation,
            LockedChange request) {
        List<InventoryRow> rows = jdbc.query("""
                select room_type_id, stay_date, capacity, held, confirmed
                from inventory_day
                where (room_type_id = ? and stay_date >= ? and stay_date < ?)
                   or (room_type_id = ? and stay_date >= ? and stay_date < ?)
                order by room_type_id, stay_date for update
                """, this::mapInventory,
                reservation.roomTypeId(), reservation.checkIn(), reservation.checkOut(),
                request.targetRoomTypeId(), request.targetCheckIn(), request.targetCheckOut());
        Map<InventoryKey, InventoryRow> result = new LinkedHashMap<>();
        rows.forEach(row -> result.put(new InventoryKey(row.roomTypeId(), row.stayDate()), row));
        return result;
    }

    private LockedChange mapChange(ResultSet rs) throws SQLException {
        return new LockedChange(
                rs.getObject("id", UUID.class), rs.getString("status"),
                rs.getObject("apply_claim_token", UUID.class), rs.getLong("base_operation_revision"),
                rs.getString("settlement_direction"), rs.getObject("requested_by", UUID.class),
                rs.getString("request_hash"), rs.getDate("previous_check_in").toLocalDate(),
                rs.getDate("previous_check_out").toLocalDate(), rs.getObject("previous_room_type_id", UUID.class),
                rs.getObject("previous_rate_plan_id", UUID.class), rs.getDate("target_check_in").toLocalDate(),
                rs.getDate("target_check_out").toLocalDate(), rs.getObject("target_room_type_id", UUID.class),
                rs.getObject("target_rate_plan_id", UUID.class), rs.getInt("rooms"), rs.getInt("adults"),
                rs.getInt("children"), rs.getObject("current_quote_id", UUID.class),
                rs.getLong("previous_total_krw"), rs.getLong("total_krw"), rs.getLong("difference_krw"));
    }

    private InventoryRow mapInventory(ResultSet rs, int rowNumber) throws SQLException {
        return new InventoryRow(
                rs.getObject("room_type_id", UUID.class), rs.getDate("stay_date").toLocalDate(),
                rs.getInt("capacity"), rs.getInt("held"), rs.getInt("confirmed"));
    }

    private BusinessConflictException conflict(String code, String message) {
        return new BusinessConflictException(code, message);
    }

    private record LockedReservation(
            UUID id, UUID roomTypeId, UUID ratePlanId, LocalDate checkIn, LocalDate checkOut,
            int rooms, String status, long operationRevision, String timezone, int assignments) {
    }

    private record LockedChange(
            UUID id, String status, UUID claimToken, long baseOperationRevision, String direction,
            UUID requestedBy, String requestHash, LocalDate previousCheckIn, LocalDate previousCheckOut,
            UUID previousRoomTypeId, UUID previousRatePlanId, LocalDate targetCheckIn, LocalDate targetCheckOut,
            UUID targetRoomTypeId, UUID targetRatePlanId, int rooms, int adults, int children, UUID quoteId,
            long previousTotalKrw, long totalKrw, long differenceKrw) {
    }

    private record HeldDay(
            UUID roomTypeId, LocalDate stayDate, int rooms, String holdKind, String status, Instant expiresAt) {
    }

    private record InventoryKey(UUID roomTypeId, LocalDate stayDate) {
    }

    private record InventoryRow(UUID roomTypeId, LocalDate stayDate, int capacity, int held, int confirmed) {
    }

    private record NightRow(LocalDate stayDate, int amountKrw) {
    }
}
