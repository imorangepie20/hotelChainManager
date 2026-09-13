package team.hotelchain.reservation;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

@Service
public class StaffReservationPartyUpdateService {
    private final JdbcTemplate jdbc;
    private final StaffAccessService staffAccess;
    private final ReservationAccess reservationAccess;

    public StaffReservationPartyUpdateService(
            JdbcTemplate jdbc,
            StaffAccessService staffAccess,
            ReservationAccess reservationAccess) {
        this.jdbc = jdbc;
        this.staffAccess = staffAccess;
        this.reservationAccess = reservationAccess;
    }

    @Transactional
    public StaffReservationPartyUpdateResult update(
            String token,
            UUID reservationId,
            String idempotencyKey,
            StaffReservationPartyUpdateRequest request) {
        StaffPrincipal staff = staffAccess.current(token);
        Party party = validate(idempotencyKey, request);
        ReservationParty reservation = lockReservation(reservationId);
        staffAccess.requireHotel(staff, reservation.hotelId());
        String requestHash = requestHash(staff.id(), party);

        ExistingChange existing = existingChange(reservationId, idempotencyKey);
        if (existing != null) {
            if (!existing.requestHash().equals(requestHash)) {
                throw new BusinessConflictException(
                        "IDEMPOTENCY_CONFLICT", "같은 요청 키에 다른 투숙 인원 변경 요청이 사용되었습니다.");
            }
            return new StaffReservationPartyUpdateResult(
                    reservationId, existing.adults(), existing.children());
        }
        if (!"CONFIRMED".equals(reservation.status())) {
            throw new BusinessConflictException(
                    "RESERVATION_STATE_CONFLICT", "확정된 예약의 투숙 인원만 변경할 수 있습니다.");
        }
        if ((long) reservation.maxOccupancy() * reservation.rooms()
                < (long) party.adults() + party.children()) {
            throw new BusinessConflictException(
                    "RESERVATION_PARTY_CAPACITY_EXCEEDED", "예약한 객실의 최대 수용 인원을 초과했습니다.");
        }
        if (reservation.adults() == party.adults() && reservation.children() == party.children()) {
            throw new BusinessConflictException(
                    "RESERVATION_PARTY_UNCHANGED", "변경할 투숙 인원을 입력해 주세요.");
        }

        jdbc.update("update reservation set adults = ?, children = ? where id = ?",
                party.adults(), party.children(), reservationId);
        jdbc.update("""
                insert into reservation_party_change
                    (id, reservation_id, idempotency_key, request_hash, staff_id,
                     previous_adults, previous_children, adults, children)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), reservationId, idempotencyKey, requestHash, staff.id(),
                reservation.adults(), reservation.children(), party.adults(), party.children());
        return new StaffReservationPartyUpdateResult(reservationId, party.adults(), party.children());
    }

    private ReservationParty lockReservation(UUID reservationId) {
        ReservationParty reservation = jdbc.query("""
                select r.id, rt.hotel_id, r.status, r.adults, r.children, r.rooms, rt.max_occupancy
                from reservation r join room_type rt on rt.id = r.room_type_id
                where r.id = ?
                for update of r
                """, rs -> rs.next() ? new ReservationParty(
                        rs.getObject("id", UUID.class),
                        rs.getObject("hotel_id", UUID.class),
                        rs.getString("status"),
                        rs.getInt("adults"),
                        rs.getInt("children"),
                        rs.getInt("rooms"),
                        rs.getInt("max_occupancy")) : null, reservationId);
        if (reservation == null) throw new ReservationNotFoundException();
        return reservation;
    }

    private ExistingChange existingChange(UUID reservationId, String idempotencyKey) {
        return jdbc.query("""
                select request_hash, adults, children
                from reservation_party_change
                where reservation_id = ? and idempotency_key = ?
                """, rs -> rs.next() ? new ExistingChange(
                        rs.getString("request_hash"), rs.getInt("adults"), rs.getInt("children")) : null,
                reservationId, idempotencyKey);
    }

    private Party validate(String idempotencyKey, StaffReservationPartyUpdateRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new IllegalArgumentException("올바른 Idempotency-Key가 필요합니다.");
        }
        if (request == null || request.adults() == null || request.children() == null) {
            throw new IllegalArgumentException("성인은 1명 이상, 아동은 0명 이상이어야 합니다.");
        }
        try {
            int adults = request.adults().intValueExact();
            int children = request.children().intValueExact();
            if (adults < 1 || children < 0) {
                throw new IllegalArgumentException("성인은 1명 이상, 아동은 0명 이상이어야 합니다.");
            }
            return new Party(adults, children);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("성인과 아동 인원은 정수로 입력해 주세요.", exception);
        }
    }

    private String requestHash(UUID staffId, Party party) {
        return reservationAccess.sha256(
                (staffId + ":" + party.adults() + ":" + party.children()).getBytes(StandardCharsets.UTF_8));
    }

    private record Party(int adults, int children) {
    }

    private record ReservationParty(
            UUID id, UUID hotelId, String status, int adults, int children, int rooms, int maxOccupancy) {
    }

    private record ExistingChange(String requestHash, int adults, int children) {
    }
}
