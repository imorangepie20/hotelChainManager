package team.hotelchain.reservation;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

@Service
public class StaffReservationGuestUpdateService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final JdbcTemplate jdbc;
    private final StaffAccessService staffAccess;
    private final ReservationAccess reservationAccess;

    public StaffReservationGuestUpdateService(
            JdbcTemplate jdbc,
            StaffAccessService staffAccess,
            ReservationAccess reservationAccess) {
        this.jdbc = jdbc;
        this.staffAccess = staffAccess;
        this.reservationAccess = reservationAccess;
    }

    @Transactional
    public StaffReservationGuestUpdateResult update(
            String token,
            UUID reservationId,
            String idempotencyKey,
            StaffReservationGuestUpdateRequest request) {
        StaffPrincipal staff = staffAccess.current(token);
        GuestDetails normalized = validate(idempotencyKey, request);
        ReservationGuest reservation = lockReservation(reservationId);
        staffAccess.requireHotel(staff, reservation.hotelId());
        String requestHash = requestHash(staff.id(), normalized);

        ExistingChange existing = existingChange(reservationId, idempotencyKey);
        if (existing != null) {
            if (!existing.requestHash().equals(requestHash)) {
                throw new BusinessConflictException(
                        "IDEMPOTENCY_CONFLICT", "같은 요청 키에 다른 예약자 정보가 사용되었습니다.");
            }
            return new StaffReservationGuestUpdateResult(
                    reservationId, existing.guestName(), existing.guestEmail());
        }
        if (!"CONFIRMED".equals(reservation.status())) {
            throw new BusinessConflictException(
                    "RESERVATION_STATE_CONFLICT", "확정된 예약의 예약자 정보만 수정할 수 있습니다.");
        }
        if (reservation.guestName().equals(normalized.name())
                && reservation.guestEmail().equals(normalized.email())) {
            throw new BusinessConflictException(
                    "RESERVATION_GUEST_UNCHANGED", "변경할 예약자 정보를 입력해 주세요.");
        }

        jdbc.update("update reservation set guest_name = ?, guest_email = ? where id = ?",
                normalized.name(), normalized.email(), reservationId);
        jdbc.update("""
                insert into reservation_guest_change
                    (id, reservation_id, idempotency_key, request_hash, staff_id,
                     previous_guest_name, previous_guest_email, guest_name, guest_email)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), reservationId, idempotencyKey, requestHash, staff.id(),
                reservation.guestName(), reservation.guestEmail(), normalized.name(), normalized.email());
        return new StaffReservationGuestUpdateResult(reservationId, normalized.name(), normalized.email());
    }

    private ReservationGuest lockReservation(UUID reservationId) {
        ReservationGuest reservation = jdbc.query("""
                select r.id, rt.hotel_id, r.status, r.guest_name, r.guest_email
                from reservation r join room_type rt on rt.id = r.room_type_id
                where r.id = ?
                for update of r
                """, rs -> rs.next() ? new ReservationGuest(
                        rs.getObject("id", UUID.class),
                        rs.getObject("hotel_id", UUID.class),
                        rs.getString("status"),
                        rs.getString("guest_name"),
                        rs.getString("guest_email")) : null,
                reservationId);
        if (reservation == null) throw new ReservationNotFoundException();
        return reservation;
    }

    private ExistingChange existingChange(UUID reservationId, String idempotencyKey) {
        return jdbc.query("""
                select request_hash, guest_name, guest_email
                from reservation_guest_change
                where reservation_id = ? and idempotency_key = ?
                """, rs -> rs.next() ? new ExistingChange(
                        rs.getString("request_hash"),
                        rs.getString("guest_name"),
                        rs.getString("guest_email")) : null,
                reservationId, idempotencyKey);
    }

    private GuestDetails validate(String idempotencyKey, StaffReservationGuestUpdateRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new IllegalArgumentException("올바른 Idempotency-Key가 필요합니다.");
        }
        if (request == null || request.guestName() == null || request.guestEmail() == null) {
            throw new IllegalArgumentException("예약자 이름과 이메일은 필수입니다.");
        }
        String name = request.guestName().trim();
        String email = request.guestEmail().trim();
        if (name.isEmpty() || name.length() > 100 || email.isEmpty() || email.length() > 254
                || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("예약자 이름과 이메일을 확인해 주세요.");
        }
        return new GuestDetails(name, email);
    }

    private String requestHash(UUID staffId, GuestDetails details) {
        String source = staffId + ":" + details.name().length() + ":" + details.name()
                + details.email().length() + ":" + details.email();
        return reservationAccess.sha256(source.getBytes(StandardCharsets.UTF_8));
    }

    private record GuestDetails(String name, String email) {
    }

    private record ReservationGuest(
            UUID id, UUID hotelId, String status, String guestName, String guestEmail) {
    }

    private record ExistingChange(String requestHash, String guestName, String guestEmail) {
    }
}
