package team.hotelchain.operations;

import java.util.UUID;

public record RoomReassignmentResult(
        UUID reservationId,
        UUID previousPhysicalRoomId,
        String previousRoomNumber,
        UUID physicalRoomId,
        String roomNumber) {
}
