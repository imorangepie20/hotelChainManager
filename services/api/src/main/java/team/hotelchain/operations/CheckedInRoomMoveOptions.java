package team.hotelchain.operations;

import java.util.List;
import java.util.UUID;

public record CheckedInRoomMoveOptions(
        UUID reservationId,
        List<AssignableRoom> assignments,
        List<AssignableRoom> candidates) {}
