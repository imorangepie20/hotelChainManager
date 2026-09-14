package team.hotelchain.operations;

import java.time.Instant;
import java.util.UUID;

public record CheckedInRoomMoveResult(
        UUID reservationId,
        UUID previousPhysicalRoomId,
        String previousRoomNumber,
        UUID physicalRoomId,
        String roomNumber,
        String reason,
        Instant movedAt) {}
