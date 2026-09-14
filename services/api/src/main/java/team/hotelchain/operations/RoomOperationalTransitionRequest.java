package team.hotelchain.operations;

import java.time.Instant;

public record RoomOperationalTransitionRequest(
        String targetStatus,
        String reason,
        Instant expectedRecoveryAt,
        long expectedVersion) {}
