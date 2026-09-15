package team.hotelchain.payment;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 인스턴스 전체 webhook 유입량을 제한한다. 외부 ingress 제한과 함께 사용한다. */
@Component
public class TossWebhookRateLimiter {
    private final int maxPerSecond;
    private final Clock clock;
    private long currentSecond = Long.MIN_VALUE;
    private int accepted;

    public TossWebhookRateLimiter(
            @Value("${payment.toss.webhook-max-per-second:120}") int maxPerSecond,
            Clock clock) {
        if (maxPerSecond < 1) {
            throw new IllegalArgumentException("Toss webhook 초당 한도는 1 이상이어야 합니다.");
        }
        this.maxPerSecond = maxPerSecond;
        this.clock = clock;
    }

    public synchronized boolean tryAcquire() {
        long second = clock.instant().getEpochSecond();
        if (second != currentSecond) {
            currentSecond = second;
            accepted = 0;
        }
        if (accepted >= maxPerSecond) return false;
        accepted++;
        return true;
    }
}
