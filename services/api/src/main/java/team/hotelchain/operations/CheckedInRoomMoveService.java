package team.hotelchain.operations;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.reservation.BusinessConflictException;
import team.hotelchain.reservation.ReservationAccess;
import team.hotelchain.reservation.ReservationNotFoundException;
import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

@Service
public class CheckedInRoomMoveService {
    private final JdbcTemplate jdbc;
    private final StaffAccessService staffAccess;
    private final ReservationAccess reservationAccess;
    private final Clock clock;

    public CheckedInRoomMoveService(JdbcTemplate jdbc, StaffAccessService staffAccess,
            ReservationAccess reservationAccess, Clock clock) {
        this.jdbc = jdbc;
        this.staffAccess = staffAccess;
        this.reservationAccess = reservationAccess;
        this.clock = clock;
    }

    @Transactional
    public CheckedInRoomMoveOptions options(String token, UUID reservationId) {
        LockedReservation reservation = lockReservation(reservationId);
        staffAccess.requireHotel(token, reservation.hotelId());
        requireCheckedIn(reservation);

        List<AssignableRoom> assignments = jdbc.query("""
                select p.id, p.room_number
                  from reservation_room_assignment a
                  join physical_room p on p.id = a.physical_room_id
                 where a.reservation_id = ?
                 order by p.room_number
                """, (rs, rowNum) -> new AssignableRoom(
                        rs.getObject("id", UUID.class), rs.getString("room_number")), reservationId);
        List<AssignableRoom> candidates = jdbc.query("""
                select p.id, p.room_number
                  from physical_room p
                 where p.hotel_id = ? and p.room_type_id = ?
                   and p.housekeeping_status = 'CLEAN' and p.operational_status = 'AVAILABLE'
                   and not exists (
                       select 1 from reservation_room_assignment own
                        where own.reservation_id = ? and own.physical_room_id = p.id
                   )
                   and not exists (
                       select 1
                         from reservation_room_assignment a
                         join reservation r on r.id = a.reservation_id
                        where a.physical_room_id = p.id and r.id <> ?
                          and r.status in ('CONFIRMED', 'CHECKED_IN')
                          and r.check_in < ? and r.check_out > ?
                   )
                 order by p.room_number
                """, (rs, rowNum) -> new AssignableRoom(
                        rs.getObject("id", UUID.class), rs.getString("room_number")),
                reservation.hotelId(), reservation.roomTypeId(), reservationId, reservationId,
                reservation.checkOut(), reservation.checkIn());
        return new CheckedInRoomMoveOptions(reservationId, assignments, candidates);
    }

    @Transactional
    public CheckedInRoomMoveResult move(String token, UUID reservationId, String idempotencyKey,
            CheckedInRoomMoveRequest request) {
        ValidatedRequest validated = validate(idempotencyKey, request);
        StaffPrincipal staff = staffAccess.current(token);
        LockedReservation reservation = lockReservation(reservationId);
        staffAccess.requireHotel(staff, reservation.hotelId());
        String requestHash = requestHash(staff.id(), validated);

        ExistingMove existing = existingMove(reservationId, validated.idempotencyKey());
        if (existing != null) {
            if (!existing.requestHash().equals(requestHash)) {
                throw new BusinessConflictException(
                        "IDEMPOTENCY_CONFLICT", "같은 멱등 키에 다른 객실 이동 요청을 사용할 수 없습니다.");
            }
            return existing.result(reservationId);
        }

        requireCheckedIn(reservation);
        if (validated.currentRoomId().equals(validated.newRoomId())) {
            throw new BusinessConflictException("ROOM_MOVE_UNCHANGED", "현재 객실과 다른 객실을 선택해 주세요.");
        }

        Map<UUID, LockedRoom> rooms = lockRooms(validated.currentRoomId(), validated.newRoomId());
        LockedRoom currentRoom = requireRoom(rooms, validated.currentRoomId());
        LockedRoom newRoom = requireRoom(rooms, validated.newRoomId());
        requireCurrentAssignment(reservationId, validated.currentRoomId());
        requireMatchingRoom(reservation, currentRoom);
        requireMatchingRoom(reservation, newRoom);
        if (!"CLEAN".equals(newRoom.housekeepingStatus())) {
            throw new BusinessConflictException("ROOM_NOT_CLEAN", "청결 상태인 객실로만 이동할 수 있습니다.");
        }
        if (!"AVAILABLE".equals(newRoom.operationalStatus())) {
            throw new BusinessConflictException(
                    "ROOM_NOT_OPERATIONALLY_AVAILABLE", "점검 또는 판매 중지 중인 객실로 이동할 수 없습니다.");
        }
        requireUnoccupied(reservation, validated.newRoomId());

        int changed = jdbc.update("""
                update reservation_room_assignment
                   set physical_room_id = ?, assigned_at = ?
                 where reservation_id = ? and physical_room_id = ?
                """, validated.newRoomId(), Timestamp.from(clock.instant()),
                reservationId, validated.currentRoomId());
        if (changed != 1) {
            throw new BusinessConflictException("ROOM_ASSIGNMENT_NOT_FOUND", "현재 예약에 배정된 객실을 선택해 주세요.");
        }

        Instant movedAt = clock.instant();
        jdbc.update("""
                update physical_room
                   set housekeeping_status = 'NEEDS_CLEANING',
                       operational_status = 'INSPECTION_REQUIRED', operational_reason = ?,
                       expected_recovery_at = null, operational_version = operational_version + 1,
                       operational_updated_at = ?
                 where id = ?
                """, validated.reason(), Timestamp.from(movedAt), validated.currentRoomId());
        jdbc.update("""
                insert into physical_room_operational_event
                    (id, physical_room_id, previous_status, status, previous_reason, reason,
                     previous_expected_recovery_at, expected_recovery_at, staff_id,
                     idempotency_key, request_hash, created_at)
                values (?, ?, ?, 'INSPECTION_REQUIRED', ?, ?, ?, null, ?, ?, ?, ?)
                """, UUID.randomUUID(), currentRoom.id(), currentRoom.operationalStatus(),
                currentRoom.operationalReason(), validated.reason(), timestamp(currentRoom.expectedRecoveryAt()),
                staff.id(), operationalEventKey(reservationId, validated.idempotencyKey()), requestHash,
                Timestamp.from(movedAt));
        jdbc.update("""
                insert into checked_in_room_move
                    (id, reservation_id, previous_physical_room_id, previous_room_number,
                     physical_room_id, room_number, reason, staff_id, idempotency_key, request_hash, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), reservationId, currentRoom.id(), currentRoom.roomNumber(),
                newRoom.id(), newRoom.roomNumber(), validated.reason(), staff.id(),
                validated.idempotencyKey(), requestHash, Timestamp.from(movedAt));
        return existingMove(reservationId, validated.idempotencyKey()).result(reservationId);
    }

    private LockedReservation lockReservation(UUID reservationId) {
        LockedReservation reservation = jdbc.query("""
                select r.id, rt.hotel_id, r.room_type_id, r.check_in, r.check_out, r.status
                  from reservation r join room_type rt on rt.id = r.room_type_id
                 where r.id = ? for update of r
                """, rs -> rs.next() ? new LockedReservation(
                        rs.getObject("id", UUID.class), rs.getObject("hotel_id", UUID.class),
                        rs.getObject("room_type_id", UUID.class), rs.getObject("check_in", LocalDate.class),
                        rs.getObject("check_out", LocalDate.class), rs.getString("status")) : null,
                reservationId);
        if (reservation == null) throw new ReservationNotFoundException();
        return reservation;
    }

    private Map<UUID, LockedRoom> lockRooms(UUID firstRoomId, UUID secondRoomId) {
        List<LockedRoom> rows = jdbc.query("""
                select id, hotel_id, room_type_id, room_number, housekeeping_status,
                       operational_status, operational_reason, expected_recovery_at
                  from physical_room
                 where id in (?, ?)
                 order by id
                 for update
                """, (rs, rowNum) -> new LockedRoom(
                        rs.getObject("id", UUID.class), rs.getObject("hotel_id", UUID.class),
                        rs.getObject("room_type_id", UUID.class), rs.getString("room_number"),
                        rs.getString("housekeeping_status"), rs.getString("operational_status"),
                        rs.getString("operational_reason"), instant(rs, "expected_recovery_at")),
                firstRoomId, secondRoomId);
        Map<UUID, LockedRoom> result = new HashMap<>();
        rows.forEach(room -> result.put(room.id(), room));
        return result;
    }

    private ExistingMove existingMove(UUID reservationId, String idempotencyKey) {
        return jdbc.query("""
                select request_hash, previous_physical_room_id, previous_room_number,
                       physical_room_id, room_number, reason, created_at
                  from checked_in_room_move
                 where reservation_id = ? and idempotency_key = ?
                """, rs -> rs.next() ? new ExistingMove(
                        rs.getString("request_hash"), rs.getObject("previous_physical_room_id", UUID.class),
                        rs.getString("previous_room_number"), rs.getObject("physical_room_id", UUID.class),
                        rs.getString("room_number"), rs.getString("reason"), instant(rs, "created_at")) : null,
                reservationId, idempotencyKey);
    }

    private void requireCheckedIn(LockedReservation reservation) {
        if (!"CHECKED_IN".equals(reservation.status())) {
            throw new BusinessConflictException("RESERVATION_NOT_CHECKED_IN", "체크인 중인 예약만 객실을 이동할 수 있습니다.");
        }
    }

    private LockedRoom requireRoom(Map<UUID, LockedRoom> rooms, UUID roomId) {
        LockedRoom room = rooms.get(roomId);
        if (room == null) throw new IllegalArgumentException("객실을 찾을 수 없습니다.");
        return room;
    }

    private void requireCurrentAssignment(UUID reservationId, UUID currentRoomId) {
        Integer count = jdbc.queryForObject("""
                select count(*) from reservation_room_assignment
                 where reservation_id = ? and physical_room_id = ?
                """, Integer.class, reservationId, currentRoomId);
        if (count == null || count == 0) {
            throw new BusinessConflictException("ROOM_ASSIGNMENT_NOT_FOUND", "현재 예약에 배정된 객실을 선택해 주세요.");
        }
    }

    private void requireMatchingRoom(LockedReservation reservation, LockedRoom room) {
        if (!reservation.hotelId().equals(room.hotelId()) || !reservation.roomTypeId().equals(room.roomTypeId())) {
            throw new BusinessConflictException("ROOM_NOT_MATCHED", "예약과 같은 호텔·객실 유형의 객실만 선택할 수 있습니다.");
        }
    }

    private void requireUnoccupied(LockedReservation reservation, UUID newRoomId) {
        Integer conflicts = jdbc.queryForObject("""
                select count(*)
                  from reservation_room_assignment a
                  join reservation r on r.id = a.reservation_id
                 where a.physical_room_id = ? and r.id <> ?
                   and r.status in ('CONFIRMED', 'CHECKED_IN')
                   and r.check_in < ? and r.check_out > ?
                """, Integer.class, newRoomId, reservation.id(), reservation.checkOut(), reservation.checkIn());
        if (conflicts != null && conflicts > 0) {
            throw new BusinessConflictException("ROOM_ALREADY_ASSIGNED", "다른 투숙 또는 예약에 배정된 객실입니다.");
        }
    }

    private ValidatedRequest validate(String idempotencyKey, CheckedInRoomMoveRequest request) {
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isEmpty() || key.length() > 100) {
            throw new IllegalArgumentException("Idempotency-Key는 1자 이상 100자 이하로 입력해야 합니다.");
        }
        if (request == null || request.currentPhysicalRoomId() == null || request.newPhysicalRoomId() == null) {
            throw new IllegalArgumentException("현재 객실과 이동할 객실은 필수입니다.");
        }
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.isEmpty() || reason.length() > 500) {
            throw new IllegalArgumentException("이동 사유는 1자 이상 500자 이하로 입력해야 합니다.");
        }
        return new ValidatedRequest(key, request.currentPhysicalRoomId(), request.newPhysicalRoomId(), reason);
    }

    private String requestHash(UUID staffId, ValidatedRequest request) {
        String value = staffId + "\n" + request.currentRoomId() + "\n" + request.newRoomId() + "\n" + request.reason();
        return reservationAccess.sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private String operationalEventKey(UUID reservationId, String idempotencyKey) {
        return "checked-in-move:" + reservationId + ":" + idempotencyKey;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private record LockedReservation(
            UUID id, UUID hotelId, UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, String status) {}

    private record LockedRoom(
            UUID id, UUID hotelId, UUID roomTypeId, String roomNumber, String housekeepingStatus,
            String operationalStatus, String operationalReason, Instant expectedRecoveryAt) {}

    private record ValidatedRequest(String idempotencyKey, UUID currentRoomId, UUID newRoomId, String reason) {}

    private record ExistingMove(
            String requestHash, UUID previousRoomId, String previousRoomNumber,
            UUID roomId, String roomNumber, String reason, Instant movedAt) {
        CheckedInRoomMoveResult result(UUID reservationId) {
            return new CheckedInRoomMoveResult(
                    reservationId, previousRoomId, previousRoomNumber, roomId, roomNumber, reason, movedAt);
        }
    }
}
