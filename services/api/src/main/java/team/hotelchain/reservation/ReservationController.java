package team.hotelchain.reservation;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ReservationView> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Reservation-Token") String managementToken,
            @RequestBody ReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.create(idempotencyKey, managementToken, request));
    }

    @GetMapping("/{id}")
    public ReservationView get(
            @PathVariable UUID id,
            @RequestHeader("X-Reservation-Token") String managementToken) {
        return reservationService.get(id, managementToken);
    }
}
