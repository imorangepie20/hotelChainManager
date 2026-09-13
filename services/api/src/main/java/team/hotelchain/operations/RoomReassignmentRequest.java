package team.hotelchain.operations;

import java.util.UUID;

public record RoomReassignmentRequest(UUID newPhysicalRoomId) {
}
