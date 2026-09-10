package team.hotelchain.reservation;

import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations/{id}/cancel")
public class CancellationController {

    private final CancellationService cancellationService;

    public CancellationController(CancellationService cancellationService) {
        this.cancellationService = cancellationService;
    }

    @PostMapping
    public CancellationResult cancel(
            @PathVariable UUID id,
            @RequestHeader("X-Reservation-Token") String token,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return cancellationService.cancel(id, token, idempotencyKey);
    }
}
