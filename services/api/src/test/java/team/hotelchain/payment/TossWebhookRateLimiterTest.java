package team.hotelchain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TossWebhookRateLimiterTest {
    @Test
    void rejects_requests_over_the_per_second_limit() {
        var limiter = new TossWebhookRateLimiter(2,
                Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneOffset.UTC));

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void rejects_non_positive_configuration() {
        assertThatThrownBy(() -> new TossWebhookRateLimiter(0, Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
