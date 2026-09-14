package team.hotelchain.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 결제 진입 UI를 위한 비민감 설정. 키와 상점 정보는 제공하지 않는다. */
@RestController
public class PaymentModeController {
    private final PaymentMode view;

    public PaymentModeController(@Value("${payment.provider:disabled}") String provider,
            @Value("${reservation.change.gateway:disabled}") String changeProvider) {
        this.view = new PaymentMode(mode(provider), mode(changeProvider));
    }

    @GetMapping("/api/payments/mode")
    public PaymentMode mode() { return view; }

    private static String mode(String value) {
        return "fake".equals(value) || "toss-test".equals(value) ? value : "disabled";
    }

    public record PaymentMode(String provider, String changeProvider) { }
}
