package team.hotelchain.reservation;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

@Service
public class StaffReservationCancellationService {
    private final JdbcTemplate jdbc;
    private final StaffAccessService staffAccess;
    private final CancellationService cancellationService;

    public StaffReservationCancellationService(
            JdbcTemplate jdbc,
            StaffAccessService staffAccess,
            CancellationService cancellationService) {
        this.jdbc = jdbc;
        this.staffAccess = staffAccess;
        this.cancellationService = cancellationService;
    }

    public StaffCancellationPreview preview(String token, UUID reservationId) {
        staffAccess.requireHotel(token, hotelId(reservationId));
        return cancellationService.previewForStaff(reservationId);
    }

    public CancellationResult cancel(String token, UUID reservationId, String idempotencyKey) {
        StaffPrincipal staff = staffAccess.requireHotel(token, hotelId(reservationId));
        return cancellationService.cancelForStaff(reservationId, staff.id(), idempotencyKey);
    }

    private UUID hotelId(UUID reservationId) {
        UUID hotelId = jdbc.query("""
                select rt.hotel_id
                from reservation r join room_type rt on rt.id = r.room_type_id
                where r.id = ?
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, reservationId);
        if (hotelId == null) throw new ReservationNotFoundException();
        return hotelId;
    }
}
