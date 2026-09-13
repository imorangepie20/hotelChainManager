package team.hotelchain.reservationchange;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff")
public class StaffReservationChangeController {
    private final ReservationChangeRequestService requests;
    private final ReservationChangeApprovalService approvals;

    public StaffReservationChangeController(
            ReservationChangeRequestService requests,
            ReservationChangeApprovalService approvals) {
        this.requests = requests;
        this.approvals = approvals;
    }

    @GetMapping("/reservation-change-policy")
    public ReservationChangePolicyView policy(
            @RequestHeader("X-Staff-Session") String token) {
        return requests.policy(token);
    }

    @PostMapping("/reservations/{reservationId}/change-requests")
    public ReservationChangeRequestView create(
            @PathVariable UUID reservationId,
            @RequestHeader("X-Staff-Session") String token,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CreateReservationChangeRequest request) {
        return requests.create(token, reservationId, idempotencyKey, request);
    }

    @GetMapping("/reservation-change-requests")
    public List<ReservationChangeRequestView> list(
            @RequestHeader("X-Staff-Session") String token,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID hotelId) {
        return requests.list(token, status, hotelId);
    }

    @GetMapping("/reservation-change-requests/{requestId}")
    public ReservationChangeRequestView get(
            @PathVariable UUID requestId,
            @RequestHeader("X-Staff-Session") String token) {
        return requests.get(token, requestId);
    }

    @PostMapping("/reservation-change-requests/{requestId}/approve")
    public ReservationChangeRequestView approve(
            @PathVariable UUID requestId,
            @RequestHeader("X-Staff-Session") String token,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody ReservationChangeApprovalRequest request) {
        return approvals.approve(token, requestId, idempotencyKey, request);
    }

    @PostMapping("/reservation-change-requests/{requestId}/reject")
    public ReservationChangeRequestView reject(
            @PathVariable UUID requestId,
            @RequestHeader("X-Staff-Session") String token,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody ReservationChangeRejectionRequest request) {
        return approvals.reject(token, requestId, idempotencyKey, request);
    }

    @PostMapping("/reservation-change-requests/{requestId}/reprice")
    public ReservationChangeRequestView reprice(
            @PathVariable UUID requestId,
            @RequestHeader("X-Staff-Session") String token,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody ReservationChangeVersionRequest request) {
        return requests.reprice(token, requestId, idempotencyKey, request);
    }

    @PostMapping("/reservation-change-requests/{requestId}/cancel")
    public ReservationChangeRequestView cancel(
            @PathVariable UUID requestId,
            @RequestHeader("X-Staff-Session") String token,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody ReservationChangeVersionRequest request) {
        return requests.cancel(token, requestId, idempotencyKey, request);
    }
}
