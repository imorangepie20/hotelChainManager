package team.hotelchain.reservationchange;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservation-change-payments")
@ConditionalOnProperty(name = "reservation.change.settlement-enabled", havingValue = "true")
public class CustomerReservationChangePaymentController {
    private static final String SESSION_COOKIE = "reservation_change_session";
    private final ReservationChangeSettlementService settlements;

    public CustomerReservationChangePaymentController(ReservationChangeSettlementService settlements) {
        this.settlements = settlements;
    }

    @PostMapping("/session")
    public ResponseEntity<Void> exchange(
            @RequestHeader("X-Reservation-Change-Token") String publicToken,
            HttpServletRequest request) {
        ReservationChangeSettlementService.CustomerSession session = settlements.exchangeCustomerToken(publicToken);
        long maxAge = Math.max(0, Duration.between(Instant.now(), session.expiresAt()).toSeconds());
        ResponseCookie cookie = ResponseCookie.from(SESSION_COOKIE, session.token())
                .httpOnly(true)
                .secure(isSecure(request))
                .sameSite("Strict")
                .path("/api/reservation-change-payments")
                .maxAge(maxAge)
                .build();
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    @GetMapping("/current")
    public CustomerReservationChangePaymentView current(
            @CookieValue(SESSION_COOKIE) String sessionToken) {
        return settlements.current(sessionToken);
    }

    @PostMapping("/current/checkout")
    public Map<String, String> checkout(
            @CookieValue(SESSION_COOKIE) String sessionToken) {
        return Map.of("checkoutUrl", settlements.checkout(sessionToken));
    }

    private boolean isSecure(HttpServletRequest request) {
        String host = request.getServerName();
        return request.isSecure() || !("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host));
    }
}
