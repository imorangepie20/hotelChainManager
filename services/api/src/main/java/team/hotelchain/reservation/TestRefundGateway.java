package team.hotelchain.reservation;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class TestRefundGateway {

    private final Set<UUID> failures = ConcurrentHashMap.newKeySet();

    public boolean refund(UUID reservationId, long amount) {
        return !failures.contains(reservationId);
    }

    public void failFor(UUID reservationId) {
        failures.add(reservationId);
    }

    public void reset() {
        failures.clear();
    }
}
