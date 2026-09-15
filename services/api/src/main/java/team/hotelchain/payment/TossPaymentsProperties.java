package team.hotelchain.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("payment.toss")
public record TossPaymentsProperties(
        String clientKey,
        String secretKey,
        String merchantAccount,
        String customerOrigin) {

    public void requireTestConfiguration() {
        TossPaymentEnvironment.TEST.requireConfiguration(this);
    }
}
