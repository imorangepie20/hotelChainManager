package team.hotelchain.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TossPaymentsConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(TossPaymentsConfiguration.class);

    @Test
    void live_mode_creates_live_environment_and_client() {
        context.withPropertyValues(
                "payment.provider=toss-live",
                "payment.toss.client-key=live_gck_fixture",
                "payment.toss.secret-key=live_gsk_fixture",
                "payment.toss.merchant-account=hotel-live",
                "payment.toss.customer-origin=https://hotel.example")
                .run(result -> {
                    assertThat(result).hasNotFailed();
                    assertThat(result).hasSingleBean(TossPaymentsClient.class);
                    assertThat(result.getBean(TossPaymentEnvironment.class)).isEqualTo(TossPaymentEnvironment.LIVE);
                });
    }

    @Test
    void fake_mode_does_not_create_toss_components() {
        context.withPropertyValues("payment.provider=fake")
                .run(result -> {
                    assertThat(result).hasNotFailed();
                    assertThat(result).doesNotHaveBean(TossPaymentsClient.class);
                    assertThat(result).doesNotHaveBean(TossPaymentEnvironment.class);
                });
    }
}
