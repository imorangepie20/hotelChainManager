package team.hotelchain.guestrequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 개인정보 없이 OPEN 고객 요청에서 계산한 앱 내 알림 목록. */
public record GuestRequestNotificationListView(
        List<NotificationView> notifications,
        int totalCount) {

    public record NotificationView(
            UUID id,
            UUID requestId,
            UUID hotelId,
            String hotelName,
            String requestType,
            String notificationType,
            Instant createdAt) {}
}
