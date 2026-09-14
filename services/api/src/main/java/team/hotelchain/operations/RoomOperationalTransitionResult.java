package team.hotelchain.operations;

import java.time.Instant;
import java.util.UUID;

public record RoomOperationalTransitionResult(
        UUID physicalRoomId,
        String roomNumber,
        String housekeepingStatus,
        String operationalStatus,
        String operationalReason,
        Instant expectedRecoveryAt,
        long operationalVersion) {}
