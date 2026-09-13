package team.hotelchain.reservation;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/reservations/{reservationId}")
public class StaffCancellationController {
    private final StaffReservationCancellationService cancellationService;

    public StaffCancellationController(StaffReservationCancellationService cancellationService) {
        this.cancellationService = cancellationService;
    }

    @GetMapping("/cancellation-preview")
    public StaffCancellationPreview preview(
            @PathVariable UUID reservationId,
            @RequestHeader("X-Staff-Session") String token) {
        return cancellationService.preview(token, reservationId);
    }

    @PostMapping("/cancel")
    public CancellationResult cancel(
            @PathVariable UUID reservationId,
            @RequestHeader("X-Staff-Session") String token,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return cancellationService.cancel(token, reservationId, idempotencyKey);
    }
}
