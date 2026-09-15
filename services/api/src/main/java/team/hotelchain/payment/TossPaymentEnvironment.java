package team.hotelchain.payment;

import java.net.URI;

public enum TossPaymentEnvironment {
    TEST("toss-test", "TOSS_TEST", "test_gck_", "test_ck_", "test_gsk_", "test_sk_", false),
    LIVE("toss-live", "TOSS_LIVE", "live_gck_", "live_ck_", "live_gsk_", "live_sk_", true);

    private final String mode;
    private final String providerCode;
    private final String clientKeyPrefix;
    private final String legacyClientKeyPrefix;
    private final String secretKeyPrefix;
    private final String legacySecretKeyPrefix;
    private final boolean secureOriginRequired;

    TossPaymentEnvironment(String mode, String providerCode, String clientKeyPrefix, String legacyClientKeyPrefix,
            String secretKeyPrefix, String legacySecretKeyPrefix, boolean secureOriginRequired) {
        this.mode = mode;
        this.providerCode = providerCode;
        this.clientKeyPrefix = clientKeyPrefix;
        this.legacyClientKeyPrefix = legacyClientKeyPrefix;
        this.secretKeyPrefix = secretKeyPrefix;
        this.legacySecretKeyPrefix = legacySecretKeyPrefix;
        this.secureOriginRequired = secureOriginRequired;
    }

    public static TossPaymentEnvironment from(String mode) {
        for (TossPaymentEnvironment environment : values()) {
            if (environment.mode.equals(mode)) return environment;
        }
        throw new IllegalArgumentException("지원하지 않는 Toss 결제 환경입니다.");
    }

    public String providerCode() {
        return providerCode;
    }

    public String mode() {
        return mode;
    }

    public void requireConfiguration(TossPaymentsProperties properties) {
        if (properties == null
                || !hasPrefix(properties.clientKey(), clientKeyPrefix, legacyClientKeyPrefix)
                || !hasPrefix(properties.secretKey(), secretKeyPrefix, legacySecretKeyPrefix)
                || properties.clientKey().equals(properties.secretKey())
                || isBlank(properties.merchantAccount())) {
            throw new IllegalStateException("Toss " + (this == TEST ? "test_" : "live_")
                    + " 결제 키와 상점 설정이 선택한 환경과 일치해야 합니다.");
        }
        URI origin;
        try {
            origin = URI.create(properties.customerOrigin());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("올바른 고객 origin이 필요합니다.", exception);
        }
        if (origin.getHost() == null || origin.getUserInfo() != null || origin.getQuery() != null
                || origin.getFragment() != null || (secureOriginRequired && !"https".equalsIgnoreCase(origin.getScheme()))) {
            throw new IllegalStateException("선택한 Toss 환경에 맞는 고객 origin이 필요합니다.");
        }
    }

    private static boolean hasPrefix(String value, String prefix, String legacyPrefix) {
        return value != null && ((value.startsWith(prefix) && value.length() > prefix.length())
                || (value.startsWith(legacyPrefix) && value.length() > legacyPrefix.length()));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
