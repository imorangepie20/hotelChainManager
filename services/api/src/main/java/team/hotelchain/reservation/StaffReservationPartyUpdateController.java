package team.hotelchain.reservation;

import java.util.UUID;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/reservations/{reservationId}/party")
public class StaffReservationPartyUpdateController {
    private final StaffReservationPartyUpdateService partyUpdateService;

    public StaffReservationPartyUpdateController(StaffReservationPartyUpdateService partyUpdateService) {
        this.partyUpdateService = partyUpdateService;
    }

    @PatchMapping
    public StaffReservationPartyUpdateResult update(
            @PathVariable UUID reservationId,
            @RequestHeader("X-Staff-Session") String token,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody StaffReservationPartyUpdateRequest request) {
        return partyUpdateService.update(token, reservationId, idempotencyKey, request);
    }
}
