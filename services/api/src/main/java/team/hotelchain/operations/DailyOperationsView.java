package team.hotelchain.operations;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DailyOperationsView(
        UUID hotelId,
        LocalDate date,
        List<ReservationItem> arrivals,
        List<ReservationItem> departures,
        List<RoomItem> roomsNeedingCleaning
) {
    public record ReservationItem(
            UUID reservationId,
            String guestName,
            String roomTypeName,
            String status,
            List<String> assignedRoomNumbers
    ) {
    }

    public record RoomItem(
            UUID physicalRoomId,
            String roomNumber,
            String roomTypeName,
            String housekeepingStatus
    ) {
    }
}
