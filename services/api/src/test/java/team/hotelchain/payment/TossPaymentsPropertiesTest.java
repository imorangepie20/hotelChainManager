package team.hotelchain.payment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TossPaymentsPropertiesTest {
    @Test
    void accepts_complete_test_configuration() {
        assertThatCode(() -> new TossPaymentsProperties("test_ck_fixture", "test_sk_fixture", "hotel-test", "http://127.0.0.1:4000")
                .requireTestConfiguration()).doesNotThrowAnyException();
    }

    @Test
    void rejects_live_or_missing_secret_before_checkout_endpoint_is_available() {
        assertThatThrownBy(() -> new TossPaymentsProperties("live_ck", "", "hotel-test", "http://127.0.0.1:4000")
                .requireTestConfiguration()).isInstanceOf(IllegalStateException.class);
    }
}
