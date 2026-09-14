package team.hotelchain.operations;

import java.time.Instant;

public record RoomOperationalTransitionRequest(
        String targetStatus,
        String reason,
        Instant expectedRecoveryAt,
        Long expectedVersion) {
    public RoomOperationalTransitionRequest(
            String targetStatus, String reason, Instant expectedRecoveryAt, long expectedVersion) {
        this(targetStatus, reason, expectedRecoveryAt, Long.valueOf(expectedVersion));
    }
}
