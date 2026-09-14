package team.hotelchain.reservationchange;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations/{reservationId}")
public class CustomerReservationChangeController {
    private final CustomerReservationChangeService service;

    public CustomerReservationChangeController(CustomerReservationChangeService service) {
        this.service = service;
    }

    @GetMapping("/change-eligibility")
    public CustomerReservationChangeEligibility eligibility(@PathVariable UUID reservationId,
            @RequestHeader("X-Reservation-Token") String token) {
        return service.eligibility(token, reservationId);
    }

    @PostMapping("/change-quotes")
    public CustomerReservationChangeQuoteView quote(@PathVariable UUID reservationId,
            @RequestHeader("X-Reservation-Token") String token,
            @RequestBody CustomerReservationChangeQuoteRequest input) {
        return service.quote(token, reservationId, input);
    }

    @PostMapping("/change-requests")
    public ResponseEntity<CustomerReservationChangeStartView> create(@PathVariable UUID reservationId,
            @RequestHeader("X-Reservation-Token") String token,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CustomerReservationChangeRequest input, HttpServletRequest request) {
        CustomerReservationChangeService.StartResult result = service.create(token, reservationId, idempotencyKey, input);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.sessionToken() != null) {
            long maxAge = Math.max(0, Duration.between(Instant.now(), result.view().expiresAt()).toSeconds());
            ResponseCookie cookie = ResponseCookie.from("reservation_change_session", result.sessionToken())
                    .httpOnly(true).secure(isSecure(request)).sameSite("Strict")
                    .path("/api/reservation-change-payments").maxAge(maxAge).build();
            response.header(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        return response.body(result.view());
    }

    @PostMapping("/change-requests/{requestId}/cancel")
    public CustomerReservationChangeStartView cancel(@PathVariable UUID reservationId,
            @PathVariable UUID requestId, @RequestHeader("X-Reservation-Token") String token,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return service.cancel(token, reservationId, requestId, idempotencyKey);
    }

    private boolean isSecure(HttpServletRequest request) {
        String host = request.getServerName();
        return request.isSecure() || !("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host));
    }
}
