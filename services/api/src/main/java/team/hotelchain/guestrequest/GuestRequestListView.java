package team.hotelchain.guestrequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 고객 요청 목록. SELECT만 사용하므로 상태를 변경하지 않는다.
 */
public record GuestRequestListView(
        List<GuestRequestSummary> requests,
        int totalCount,
        String status,
        UUID hotelId,
        int limit,
        int offset) {

    public record GuestRequestSummary(
            UUID id,
            UUID hotelId,
            String hotelName,
            UUID reservationId,
            String requestType,
            String subject,
            String guestName,
            String status,
            String priority,
            String assignedDisplayName,
            Instant createdAt,
            Instant updatedAt) {
    }
}
