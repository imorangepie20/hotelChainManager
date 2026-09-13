package team.hotelchain.operations;

import java.util.List;
import java.util.UUID;

public record RoomReassignmentOptions(
        UUID reservationId,
        List<AssignableRoom> assignments,
        List<AssignableRoom> candidates) {
}
