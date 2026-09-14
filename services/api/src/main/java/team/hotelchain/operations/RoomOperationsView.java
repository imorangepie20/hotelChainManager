package team.hotelchain.operations;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RoomOperationsView(UUID hotelId, Summary summary, List<RoomItem> rooms) {
    public record Summary(int inspectionRequired, int outOfService, int overdueRecovery) {}

    public record RoomItem(
            UUID physicalRoomId,
            String roomNumber,
            String roomTypeName,
            String housekeepingStatus,
            String operationalStatus,
            String operationalReason,
            Instant expectedRecoveryAt,
            long operationalVersion,
            List<ImpactedAssignment> impactedAssignments,
            List<EventItem> events) {}

    public record ImpactedAssignment(
            UUID reservationId,
            String guestName,
            String status,
            LocalDate checkIn,
            LocalDate checkOut) {}

    public record EventItem(
            UUID id,
            String previousStatus,
            String status,
            String reason,
            Instant expectedRecoveryAt,
            UUID staffId,
            Instant createdAt) {}
}
