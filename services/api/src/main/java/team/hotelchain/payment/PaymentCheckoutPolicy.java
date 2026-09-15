package team.hotelchain.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import team.hotelchain.reservation.BusinessConflictException;

@Component
public class PaymentCheckoutPolicy {
    private final boolean enabled;

    public PaymentCheckoutPolicy(@Value("${payment.provider:fake}") String provider,
            @Value("${payment.checkout-enabled:false}") boolean explicitlyEnabled) {
        this.enabled = !"toss-live".equals(provider) || explicitlyEnabled;
    }

    public void requireEnabled() {
        if (!enabled) {
            throw new BusinessConflictException(
                    "PAYMENT_CHECKOUT_DISABLED", "새 결제 시작이 일시 중지되었습니다.");
        }
    }
}
