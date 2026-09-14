package team.hotelchain.reservation;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations/{id}/cancellation-preview")
public class CustomerCancellationPreviewController {
    private final CancellationService cancellationService;

    public CustomerCancellationPreviewController(CancellationService cancellationService) {
        this.cancellationService = cancellationService;
    }

    @GetMapping
    public CancellationPreview preview(
            @PathVariable UUID id,
            @RequestHeader("X-Reservation-Token") String token) {
        return cancellationService.preview(id, token);
    }
}
