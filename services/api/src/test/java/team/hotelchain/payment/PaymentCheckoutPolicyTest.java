package team.hotelchain.payment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import team.hotelchain.reservation.BusinessConflictException;

class PaymentCheckoutPolicyTest {
    @Test
    void live_checkout_is_disabled_until_explicitly_enabled() {
        assertThatThrownBy(() -> new PaymentCheckoutPolicy("toss-live", false).requireEnabled())
                .isInstanceOfSatisfying(BusinessConflictException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.code())
                                .isEqualTo("PAYMENT_CHECKOUT_DISABLED"));
    }

    @Test
    void test_and_explicitly_enabled_live_checkout_remain_available() {
        assertThatCode(() -> new PaymentCheckoutPolicy("toss-test", false).requireEnabled())
                .doesNotThrowAnyException();
        assertThatCode(() -> new PaymentCheckoutPolicy("toss-live", true).requireEnabled())
                .doesNotThrowAnyException();
    }
}
