package team.hotelchain.operations;

import java.util.List;

import team.hotelchain.reservation.BusinessConflictException;

public class RoomHasActiveAssignmentsException extends BusinessConflictException {
    private final List<RoomOperationsView.ImpactedAssignment> assignments;

    public RoomHasActiveAssignmentsException(List<RoomOperationsView.ImpactedAssignment> assignments) {
        super("ROOM_HAS_ACTIVE_ASSIGNMENTS", "현재 또는 예정된 객실 배정이 있어 판매 중지할 수 없습니다.");
        this.assignments = List.copyOf(assignments);
    }

    public List<RoomOperationsView.ImpactedAssignment> assignments() {
        return assignments;
    }
}
