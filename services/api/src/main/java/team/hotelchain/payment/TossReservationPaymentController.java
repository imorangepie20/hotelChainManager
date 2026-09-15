package team.hotelchain.payment;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import team.hotelchain.payment.TossReservationPaymentView.CheckoutView;
import team.hotelchain.payment.TossReservationPaymentView.ConfirmPaymentRequest;
import team.hotelchain.payment.TossReservationPaymentView.StatusView;

@RestController
@RequestMapping("/api/reservations/{id}")
@ConditionalOnExpression("'${payment.provider:fake}' == 'toss-test' or '${payment.provider:fake}' == 'toss-live'")
public class TossReservationPaymentController {
    private final TossReservationPaymentService payments;

    public TossReservationPaymentController(TossReservationPaymentService payments) { this.payments = payments; }

    @PostMapping("/payment-checkout")
    public CheckoutView checkout(@PathVariable UUID id, @RequestHeader("X-Reservation-Token") String token,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return payments.checkout(id, token, idempotencyKey);
    }

    @PostMapping("/payment-confirm")
    public StatusView confirm(@PathVariable UUID id, @RequestHeader("X-Reservation-Token") String token,
            @RequestBody ConfirmPaymentRequest request) {
        return payments.confirm(id, token, request);
    }

    @GetMapping("/payment-status")
    public StatusView status(@PathVariable UUID id, @RequestHeader("X-Reservation-Token") String token) {
        return payments.status(id, token);
    }

    @PostMapping("/payment-reconcile")
    public StatusView reconcile(@PathVariable UUID id, @RequestHeader("X-Reservation-Token") String token) {
        return payments.reconcile(id, token);
    }
}
