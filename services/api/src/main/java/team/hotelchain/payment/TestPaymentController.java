package team.hotelchain.payment;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations/{id}/test-payment")
@ConditionalOnProperty(name = "test-payment.enabled", havingValue = "true")
public class TestPaymentController {

    private final TestPaymentService paymentService;

    public TestPaymentController(TestPaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public PaymentResult pay(
            @PathVariable UUID id,
            @RequestHeader("X-Reservation-Token") String token,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PaymentRequest request) {
        return paymentService.pay(id, token, idempotencyKey, request.outcome());
    }
}
