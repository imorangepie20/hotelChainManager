package team.hotelchain.reservation;

import java.util.UUID;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/reservations/{reservationId}")
public class StaffReservationStayChangeController {
    private final StaffReservationStayChangeService stayChangeService;

    public StaffReservationStayChangeController(StaffReservationStayChangeService stayChangeService) {
        this.stayChangeService = stayChangeService;
    }

    @PostMapping("/stay-change-preview")
    public StaffReservationStayChangePreview preview(
            @PathVariable UUID reservationId,
            @RequestHeader("X-Staff-Session") String token,
            @RequestBody StaffReservationStayChangePreviewRequest request) {
        return stayChangeService.preview(token, reservationId, request);
    }

    @PatchMapping("/stay")
    public StaffReservationStayChangeResult update(
            @PathVariable UUID reservationId,
            @RequestHeader("X-Staff-Session") String token,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody StaffReservationStayChangeRequest request) {
        return stayChangeService.update(token, reservationId, idempotencyKey, request);
    }
}
