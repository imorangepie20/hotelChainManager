package team.hotelchain.reservationchange;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import team.hotelchain.inventory.NightlyPrice;

public record ReservationChangeRequestView(
        UUID id,
        UUID reservationId,
        UUID hotelId,
        String status,
        String settlementDirection,
        long version,
        LocalDate previousCheckIn,
        LocalDate previousCheckOut,
        UUID previousRoomTypeId,
        UUID previousRatePlanId,
        LocalDate targetCheckIn,
        LocalDate targetCheckOut,
        UUID targetRoomTypeId,
        UUID targetRatePlanId,
        int rooms,
        int adults,
        int children,
        Instant approvalExpiresAt,
        QuoteView quote,
        ApprovalView approval,
        List<EventView> events,
        Set<String> actions) {

    public record QuoteView(
            UUID id,
            long revision,
            long previousTotalKrw,
            long totalKrw,
            long differenceKrw,
            String currency,
            List<NightlyPrice> nightlyPrices,
            Instant createdAt) {
    }

    public record ApprovalView(
            UUID id,
            String decisionType,
            UUID decidedBy,
            String decidedRole,
            long limitKrw,
            String reason,
            Instant createdAt) {
    }

    public record EventView(
            UUID id,
            String eventType,
            String fromStatus,
            String toStatus,
            UUID actorStaffId,
            String reason,
            Instant createdAt) {
    }
}
