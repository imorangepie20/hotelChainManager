package team.hotelchain.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentModeController {
    private final PaymentMode view;
    public PaymentModeController(@Value("${payment.provider:disabled}") String provider) { this.view = new PaymentMode(mode(provider)); }
    @GetMapping("/api/payments/mode") public PaymentMode mode() { return view; }
    private static String mode(String value) { return "fake".equals(value) || "toss-test".equals(value) ? value : "disabled"; }
    public record PaymentMode(String provider) { }
}
