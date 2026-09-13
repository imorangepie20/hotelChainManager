package team.hotelchain.operations;

import java.util.UUID;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.reservation.BusinessConflictException;
import team.hotelchain.staff.StaffAccessService;

@Service
public class StaffOperationsService {
    private final JdbcTemplate jdbc;
    private final StaffAccessService staffAccess;

    public StaffOperationsService(JdbcTemplate jdbc, StaffAccessService staffAccess) {
        this.jdbc = jdbc;
        this.staffAccess = staffAccess;
    }

    @Transactional
    public void assign(String token, UUID reservationId, UUID physicalRoomId) {
        ReservationOperation reservation = reservation(reservationId);
        staffAccess.requireHotel(token, reservation.hotelId());
        Room room = room(physicalRoomId);
        if (!reservation.hotelId().equals(room.hotelId()) || !reservation.roomTypeId().equals(room.roomTypeId())) {
            throw new BusinessConflictException("ROOM_NOT_MATCHED", "예약 객실 유형과 일치하는 객실만 배정할 수 있습니다.");
        }
        Integer existingAssignment = jdbc.queryForObject("""
                select count(*) from reservation_room_assignment
                where reservation_id = ? and physical_room_id = ?
                """, Integer.class, reservationId, physicalRoomId);
        if (existingAssignment != null && existingAssignment > 0) return;
        if (!"CONFIRMED".equals(reservation.status())) {
            throw new BusinessConflictException("RESERVATION_NOT_ASSIGNABLE", "확정된 예약만 객실을 배정할 수 있습니다.");
        }
        if (!"CLEAN".equals(room.housekeepingStatus())) {
            throw new BusinessConflictException("ROOM_NOT_CLEAN", "청결 상태인 객실만 배정할 수 있습니다.");
        }
        Integer assignedCount = jdbc.queryForObject("select count(*) from reservation_room_assignment where reservation_id = ?", Integer.class, reservationId);
        if (assignedCount != null && assignedCount >= reservation.rooms()) {
            throw new BusinessConflictException("ROOMS_ALREADY_ASSIGNED", "예약 객실 수만큼 이미 배정되었습니다.");
        }
        Integer conflicts = jdbc.queryForObject("""
                SELECT count(*) FROM reservation_room_assignment a
                JOIN reservation r ON r.id = a.reservation_id
                WHERE a.physical_room_id = ? AND r.id <> ? AND r.status IN ('CONFIRMED', 'CHECKED_IN')
                  AND r.check_in < ? AND r.check_out > ?
                """, Integer.class, physicalRoomId, reservationId, reservation.checkOut(), reservation.checkIn());
        if (conflicts != null && conflicts > 0) {
            throw new BusinessConflictException("ROOM_ALREADY_ASSIGNED", "숙박 기간이 겹치는 예약에 이미 배정된 객실입니다.");
        }
        jdbc.update("insert into reservation_room_assignment (reservation_id, physical_room_id) values (?, ?) on conflict do nothing", reservationId, physicalRoomId);
    }

    @Transactional
    public List<AssignableRoom> assignableRooms(String token, UUID reservationId) {
        ReservationOperation reservation = reservation(reservationId);
        staffAccess.requireHotel(token, reservation.hotelId());
        if (!"CONFIRMED".equals(reservation.status())) {
            throw new BusinessConflictException("RESERVATION_NOT_ASSIGNABLE", "확정된 예약만 객실을 배정할 수 있습니다.");
        }
        return jdbc.query("""
                SELECT p.id, p.room_number
                  FROM physical_room p
                 WHERE p.hotel_id = ? AND p.room_type_id = ? AND p.housekeeping_status = 'CLEAN'
                   AND NOT EXISTS (SELECT 1 FROM reservation_room_assignment own
                                   WHERE own.reservation_id = ? AND own.physical_room_id = p.id)
                   AND NOT EXISTS (
                       SELECT 1 FROM reservation_room_assignment a JOIN reservation r ON r.id = a.reservation_id
                        WHERE a.physical_room_id = p.id AND r.id <> ? AND r.status IN ('CONFIRMED', 'CHECKED_IN')
                          AND r.check_in < ? AND r.check_out > ?
                   )
                 ORDER BY p.room_number
                """, (rs, row) -> new AssignableRoom(rs.getObject("id", UUID.class), rs.getString("room_number")),
                reservation.hotelId(), reservation.roomTypeId(), reservationId, reservationId,
                reservation.checkOut(), reservation.checkIn());
    }

    @Transactional
    public void checkIn(String token, UUID reservationId) {
        ReservationOperation reservation = reservation(reservationId);
        staffAccess.requireHotel(token, reservation.hotelId());
        if (!"CONFIRMED".equals(reservation.status())) {
            throw new BusinessConflictException("RESERVATION_NOT_CHECKIN_READY", "확정된 예약만 체크인할 수 있습니다.");
        }
        Integer readyRooms = jdbc.queryForObject("""
                SELECT count(*) FROM reservation_room_assignment a JOIN physical_room p ON p.id = a.physical_room_id
                WHERE a.reservation_id = ? AND p.housekeeping_status = 'CLEAN'
                """, Integer.class, reservationId);
        if (readyRooms == null || readyRooms != reservation.rooms()) {
            throw new BusinessConflictException("ROOM_NOT_READY", "모든 배정 객실이 청결 상태여야 체크인할 수 있습니다.");
        }
        jdbc.update("update reservation set status = 'CHECKED_IN' where id = ?", reservationId);
    }

    @Transactional
    public void checkOut(String token, UUID reservationId) {
        ReservationOperation reservation = reservation(reservationId);
        staffAccess.requireHotel(token, reservation.hotelId());
        if (!"CHECKED_IN".equals(reservation.status())) {
            throw new BusinessConflictException("RESERVATION_NOT_CHECKED_IN", "체크인 중인 예약만 체크아웃할 수 있습니다.");
        }
        jdbc.update("update reservation set status = 'CHECKED_OUT' where id = ?", reservationId);
        jdbc.update("""
                UPDATE physical_room SET housekeeping_status = 'NEEDS_CLEANING'
                WHERE id IN (SELECT physical_room_id FROM reservation_room_assignment WHERE reservation_id = ?)
                """, reservationId);
    }

    @Transactional
    public void markNoShow(String token, UUID reservationId) {
        ReservationOperation reservation = reservation(reservationId);
        staffAccess.requireHotel(token, reservation.hotelId());
        if (!"CONFIRMED".equals(reservation.status())) {
            throw new BusinessConflictException("RESERVATION_NOT_NO_SHOW_READY", "확정 상태 예약만 노쇼 처리할 수 있습니다.");
        }
        jdbc.update("delete from reservation_room_assignment where reservation_id = ?", reservationId);
        jdbc.update("update reservation set status = 'NO_SHOW' where id = ?", reservationId);
    }

    @Transactional
    public void completeHousekeeping(String token, UUID physicalRoomId) {
        Room room = room(physicalRoomId);
        staffAccess.requireHotel(token, room.hotelId());
        if (!"NEEDS_CLEANING".equals(room.housekeepingStatus())) {
            throw new BusinessConflictException("ROOM_NOT_NEEDS_CLEANING", "청소 필요 상태의 객실만 완료 처리할 수 있습니다.");
        }
        jdbc.update("update physical_room set housekeeping_status = 'CLEAN' where id = ?", physicalRoomId);
    }

    private ReservationOperation reservation(UUID id) {
        ReservationOperation result = jdbc.query("""
                SELECT r.id, rt.hotel_id, r.room_type_id, r.check_in, r.check_out, r.rooms, r.status
                FROM reservation r JOIN room_type rt ON rt.id = r.room_type_id WHERE r.id = ? FOR UPDATE
                """, rs -> rs.next() ? new ReservationOperation(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getDate(4).toLocalDate(), rs.getDate(5).toLocalDate(), rs.getInt(6), rs.getString(7)) : null, id);
        if (result == null) throw new IllegalArgumentException("예약을 찾을 수 없습니다.");
        return result;
    }

    private Room room(UUID id) {
        Room result = jdbc.query("select id, hotel_id, room_type_id, housekeeping_status from physical_room where id = ? for update",
                rs -> rs.next() ? new Room(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getString(4)) : null, id);
        if (result == null) throw new IllegalArgumentException("객실을 찾을 수 없습니다.");
        return result;
    }

    private record ReservationOperation(UUID id, UUID hotelId, UUID roomTypeId, java.time.LocalDate checkIn, java.time.LocalDate checkOut, int rooms, String status) {}
    private record Room(UUID id, UUID hotelId, UUID roomTypeId, String housekeepingStatus) {}
}
