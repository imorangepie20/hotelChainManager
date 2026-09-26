package team.hotelchain.reservation;

import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class TestRefundGateway {

    private final Set<UUID> failures = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> refundedAmounts = new ConcurrentHashMap<>();

    public boolean refund(UUID reservationId, long amount) {
        refundedAmounts.put(reservationId, amount);
        return !failures.contains(reservationId);
    }

    public void failFor(UUID reservationId) {
        failures.add(reservationId);
    }

    public void reset() {
        failures.clear();
        refundedAmounts.clear();
    }

    public Long refundedAmount(UUID reservationId) {
        return refundedAmounts.get(reservationId);
    }
}
