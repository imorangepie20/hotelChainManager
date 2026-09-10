package team.hotelchain.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "reservation.expiry-job-enabled", havingValue = "true")
public class ReservationExpiryJob {

    private final ReservationExpiryService expiryService;

    public ReservationExpiryJob(ReservationExpiryService expiryService) {
        this.expiryService = expiryService;
    }

    @Scheduled(fixedDelayString = "${reservation.expiry-scan-delay:60s}")
    public void expireDueReservations() {
        expiryService.expireDue(50);
    }
}
