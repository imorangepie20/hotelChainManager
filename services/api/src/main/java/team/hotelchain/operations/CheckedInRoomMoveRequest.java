package team.hotelchain.operations;

import java.util.UUID;

public record CheckedInRoomMoveRequest(
        UUID currentPhysicalRoomId,
        UUID newPhysicalRoomId,
        String reason) {}
