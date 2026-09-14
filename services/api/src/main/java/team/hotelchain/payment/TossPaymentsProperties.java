package team.hotelchain.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("payment.toss")
public record TossPaymentsProperties(
        String clientKey,
        String secretKey,
        String merchantAccount,
        String customerOrigin) {

    public void requireTestConfiguration() {
        if (!isTestKey(clientKey) || !isTestKey(secretKey)
                || merchantAccount == null || merchantAccount.isBlank()
                || customerOrigin == null || customerOrigin.isBlank()) {
            throw new IllegalStateException("Toss 테스트 결제는 test_ 키, merchant 계정, 고객 origin이 필요합니다.");
        }
    }

    private boolean isTestKey(String key) {
        return key != null && key.startsWith("test_") && key.length() > "test_".length();
    }
}
