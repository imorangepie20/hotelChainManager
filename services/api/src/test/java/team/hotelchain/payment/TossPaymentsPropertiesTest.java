package team.hotelchain.payment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void separates_test_and_live_provider_snapshots() {
        assertThat(TossPaymentEnvironment.from("toss-test").providerCode()).isEqualTo("TOSS_TEST");
        assertThat(TossPaymentEnvironment.from("toss-live").providerCode()).isEqualTo("TOSS_LIVE");
        assertThatThrownBy(() -> TossPaymentEnvironment.from("fake"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void live_rejects_test_keys_and_insecure_customer_origin() {
        var testKeys = new TossPaymentsProperties(
                "test_gck_fixture", "test_gsk_fixture", "hotel-live", "https://hotel.example");
        var insecureOrigin = new TossPaymentsProperties(
                "live_gck_fixture", "live_gsk_fixture", "hotel-live", "http://hotel.example");

        assertThatThrownBy(() -> TossPaymentEnvironment.LIVE.requireConfiguration(testKeys))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> TossPaymentEnvironment.LIVE.requireConfiguration(insecureOrigin))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void live_accepts_matching_keys_and_https_origin() {
        var properties = new TossPaymentsProperties(
                "live_gck_fixture", "live_gsk_fixture", "hotel-live", "https://hotel.example");

        assertThatCode(() -> TossPaymentEnvironment.LIVE.requireConfiguration(properties))
                .doesNotThrowAnyException();
    }
}
