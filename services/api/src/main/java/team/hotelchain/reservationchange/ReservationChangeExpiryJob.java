package team.hotelchain.reservationchange;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationChangeExpiryJob {
    private final ReservationChangeHoldService holds;

    public ReservationChangeExpiryJob(ReservationChangeHoldService holds) {
        this.holds = holds;
    }

    @Scheduled(fixedDelayString = "${reservation.change.expiry-scan-delay:60s}")
    public void runScheduled() {
        expireDue(25);
    }

    public int expireDue(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("만료 처리 범위가 올바르지 않습니다.");
        int expired = 0;
        while (expired < limit && holds.expireNext()) expired++;
        return expired;
    }
}
