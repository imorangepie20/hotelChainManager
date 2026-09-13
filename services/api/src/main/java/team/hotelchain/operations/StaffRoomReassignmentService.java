package team.hotelchain.operations;

import java.nio.charset.StandardCharsets;
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
import team.hotelchain.reservationchange.ReservationChangeMutationGuard;
import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

@Service
public class StaffRoomReassignmentService {
    private final JdbcTemplate jdbc;
    private final StaffAccessService staffAccess;
    private final ReservationAccess reservationAccess;
    private final ReservationChangeMutationGuard mutationGuard;

    public StaffRoomReassignmentService(
            JdbcTemplate jdbc,
            StaffAccessService staffAccess,
            ReservationAccess reservationAccess,
            ReservationChangeMutationGuard mutationGuard) {
        this.jdbc = jdbc;
        this.staffAccess = staffAccess;
        this.reservationAccess = reservationAccess;
        this.mutationGuard = mutationGuard;
    }

    @Transactional
    public RoomReassignmentOptions options(String token, UUID reservationId) {
        ReservationOperation reservation = lockReservation(reservationId);
        staffAccess.requireHotel(token, reservation.hotelId());
        requireConfirmed(reservation);

        List<AssignableRoom> assignments = jdbc.query("""
                select p.id, p.room_number
                from reservation_room_assignment a
                join physical_room p on p.id = a.physical_room_id
                where a.reservation_id = ?
                order by p.room_number
                """, (rs, row) -> new AssignableRoom(
                        rs.getObject("id", UUID.class), rs.getString("room_number")), reservationId);
        List<AssignableRoom> candidates = jdbc.query("""
                select p.id, p.room_number
                from physical_room p
                where p.hotel_id = ? and p.room_type_id = ? and p.housekeeping_status = 'CLEAN'
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
                """, (rs, row) -> new AssignableRoom(
                        rs.getObject("id", UUID.class), rs.getString("room_number")),
                reservation.hotelId(), reservation.roomTypeId(), reservationId, reservationId,
                reservation.checkOut(), reservation.checkIn());
        return new RoomReassignmentOptions(reservationId, assignments, candidates);
    }

    @Transactional
    public RoomReassignmentResult reassign(
            String token,
            UUID reservationId,
            UUID currentPhysicalRoomId,
            String idempotencyKey,
            RoomReassignmentRequest request) {
        StaffPrincipal staff = staffAccess.current(token);
        UUID newPhysicalRoomId = validate(currentPhysicalRoomId, idempotencyKey, request);
        ReservationOperation reservation = lockReservation(reservationId);
        staffAccess.requireHotel(staff, reservation.hotelId());
        String requestHash = requestHash(staff.id(), currentPhysicalRoomId, newPhysicalRoomId);

        ExistingChange existing = existingChange(reservationId, idempotencyKey);
        if (existing != null) {
            if (!existing.requestHash().equals(requestHash)) {
                throw new BusinessConflictException(
                        "IDEMPOTENCY_CONFLICT", "같은 요청 키에 다른 객실 변경 요청이 사용되었습니다.");
            }
            return existing.result(reservationId);
        }

        requireConfirmed(reservation);
        if (currentPhysicalRoomId.equals(newPhysicalRoomId)) {
            throw new BusinessConflictException(
                    "ROOM_REASSIGNMENT_UNCHANGED", "현재 객실과 다른 객실을 선택해 주세요.");
        }

        mutationGuard.prepareCriticalMutation(reservationId);
        Map<UUID, Room> rooms = lockRooms(currentPhysicalRoomId, newPhysicalRoomId);
        Room currentRoom = requireRoom(rooms, currentPhysicalRoomId);
        Room newRoom = requireRoom(rooms, newPhysicalRoomId);
        requireMatchingRoom(reservation, currentRoom);
        requireMatchingRoom(reservation, newRoom);
        if (!"CLEAN".equals(newRoom.housekeepingStatus())) {
            throw new BusinessConflictException("ROOM_NOT_CLEAN", "청결 상태인 객실로만 변경할 수 있습니다.");
        }

        Integer currentAssignment = jdbc.queryForObject("""
                select count(*) from reservation_room_assignment
                where reservation_id = ? and physical_room_id = ?
                """, Integer.class, reservationId, currentPhysicalRoomId);
        if (currentAssignment == null || currentAssignment == 0) {
            throw new BusinessConflictException(
                    "ROOM_ASSIGNMENT_NOT_FOUND", "현재 예약에 배정된 객실을 선택해 주세요.");
        }
        Integer ownTarget = jdbc.queryForObject("""
                select count(*) from reservation_room_assignment
                where reservation_id = ? and physical_room_id = ?
                """, Integer.class, reservationId, newPhysicalRoomId);
        if (ownTarget != null && ownTarget > 0) {
            throw new BusinessConflictException(
                    "ROOM_ALREADY_ASSIGNED_TO_RESERVATION", "이미 이 예약에 배정된 객실입니다.");
        }
        Integer conflicts = jdbc.queryForObject("""
                select count(*)
                from reservation_room_assignment a
                join reservation r on r.id = a.reservation_id
                where a.physical_room_id = ? and r.id <> ?
                  and r.status in ('CONFIRMED', 'CHECKED_IN')
                  and r.check_in < ? and r.check_out > ?
                """, Integer.class, newPhysicalRoomId, reservationId,
                reservation.checkOut(), reservation.checkIn());
        if (conflicts != null && conflicts > 0) {
            throw new BusinessConflictException(
                    "ROOM_ALREADY_ASSIGNED", "숙박 기간이 겹치는 예약에 이미 배정된 객실입니다.");
        }

        jdbc.update("""
                update reservation_room_assignment
                set physical_room_id = ?
                where reservation_id = ? and physical_room_id = ?
                """, newPhysicalRoomId, reservationId, currentPhysicalRoomId);
        jdbc.update("""
                insert into reservation_room_assignment_change
                    (id, reservation_id, idempotency_key, request_hash, staff_id,
                     previous_physical_room_id, previous_room_number, physical_room_id, room_number)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), reservationId, idempotencyKey, requestHash, staff.id(),
                currentRoom.id(), currentRoom.roomNumber(), newRoom.id(), newRoom.roomNumber());
        mutationGuard.incrementRevision(reservationId);
        return new RoomReassignmentResult(
                reservationId, currentRoom.id(), currentRoom.roomNumber(), newRoom.id(), newRoom.roomNumber());
    }

    private ReservationOperation lockReservation(UUID reservationId) {
        ReservationOperation reservation = jdbc.query("""
                select r.id, rt.hotel_id, r.room_type_id, r.check_in, r.check_out, r.status
                from reservation r join room_type rt on rt.id = r.room_type_id
                where r.id = ?
                for update of r
                """, rs -> rs.next() ? new ReservationOperation(
                        rs.getObject("id", UUID.class),
                        rs.getObject("hotel_id", UUID.class),
                        rs.getObject("room_type_id", UUID.class),
                        rs.getDate("check_in").toLocalDate(),
                        rs.getDate("check_out").toLocalDate(),
                        rs.getString("status")) : null, reservationId);
        if (reservation == null) throw new ReservationNotFoundException();
        return reservation;
    }

    private Map<UUID, Room> lockRooms(UUID firstRoomId, UUID secondRoomId) {
        List<Room> rows = jdbc.query("""
                select id, hotel_id, room_type_id, room_number, housekeeping_status
                from physical_room
                where id in (?, ?)
                order by id
                for update
                """, (rs, row) -> new Room(
                        rs.getObject("id", UUID.class),
                        rs.getObject("hotel_id", UUID.class),
                        rs.getObject("room_type_id", UUID.class),
                        rs.getString("room_number"),
                        rs.getString("housekeeping_status")), firstRoomId, secondRoomId);
        Map<UUID, Room> result = new HashMap<>();
        rows.forEach(room -> result.put(room.id(), room));
        return result;
    }

    private Room requireRoom(Map<UUID, Room> rooms, UUID roomId) {
        Room room = rooms.get(roomId);
        if (room == null) throw new IllegalArgumentException("객실을 찾을 수 없습니다.");
        return room;
    }

    private void requireMatchingRoom(ReservationOperation reservation, Room room) {
        if (!reservation.hotelId().equals(room.hotelId())
                || !reservation.roomTypeId().equals(room.roomTypeId())) {
            throw new BusinessConflictException(
                    "ROOM_NOT_MATCHED", "예약과 같은 호텔·객실 유형의 객실만 선택할 수 있습니다.");
        }
    }

    private void requireConfirmed(ReservationOperation reservation) {
        if (!"CONFIRMED".equals(reservation.status())) {
            throw new BusinessConflictException(
                    "RESERVATION_NOT_REASSIGNABLE", "확정된 예약의 객실만 변경할 수 있습니다.");
        }
    }

    private UUID validate(
            UUID currentPhysicalRoomId, String idempotencyKey, RoomReassignmentRequest request) {
        if (currentPhysicalRoomId == null) {
            throw new IllegalArgumentException("현재 객실은 필수입니다.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new IllegalArgumentException("올바른 Idempotency-Key가 필요합니다.");
        }
        if (request == null || request.newPhysicalRoomId() == null) {
            throw new IllegalArgumentException("변경할 객실은 필수입니다.");
        }
        return request.newPhysicalRoomId();
    }

    private String requestHash(UUID staffId, UUID currentRoomId, UUID newRoomId) {
        return reservationAccess.sha256(
                (staffId + ":" + currentRoomId + ":" + newRoomId).getBytes(StandardCharsets.UTF_8));
    }

    private ExistingChange existingChange(UUID reservationId, String idempotencyKey) {
        return jdbc.query("""
                select request_hash, previous_physical_room_id, previous_room_number,
                       physical_room_id, room_number
                from reservation_room_assignment_change
                where reservation_id = ? and idempotency_key = ?
                """, rs -> rs.next() ? new ExistingChange(
                        rs.getString("request_hash"),
                        rs.getObject("previous_physical_room_id", UUID.class),
                        rs.getString("previous_room_number"),
                        rs.getObject("physical_room_id", UUID.class),
                        rs.getString("room_number")) : null, reservationId, idempotencyKey);
    }

    private record ReservationOperation(
            UUID id, UUID hotelId, UUID roomTypeId,
            java.time.LocalDate checkIn, java.time.LocalDate checkOut, String status) {
    }

    private record Room(
            UUID id, UUID hotelId, UUID roomTypeId, String roomNumber, String housekeepingStatus) {
    }

    private record ExistingChange(
            String requestHash,
            UUID previousPhysicalRoomId,
            String previousRoomNumber,
            UUID physicalRoomId,
            String roomNumber) {
        RoomReassignmentResult result(UUID reservationId) {
            return new RoomReassignmentResult(
                    reservationId, previousPhysicalRoomId, previousRoomNumber, physicalRoomId, roomNumber);
        }
    }
}
