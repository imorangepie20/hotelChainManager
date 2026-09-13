package team.hotelchain.reservation;

import java.util.UUID;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/reservations/{reservationId}/guest")
public class StaffReservationGuestUpdateController {
    private final StaffReservationGuestUpdateService guestUpdateService;

    public StaffReservationGuestUpdateController(StaffReservationGuestUpdateService guestUpdateService) {
        this.guestUpdateService = guestUpdateService;
    }

    @PatchMapping
    public StaffReservationGuestUpdateResult update(
            @PathVariable UUID reservationId,
            @RequestHeader("X-Staff-Session") String token,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody StaffReservationGuestUpdateRequest request) {
        return guestUpdateService.update(token, reservationId, idempotencyKey, request);
    }
}
