package team.hotelchain.audit;

/**
 * 본사가 읽는 통합 감사 이력 한 건.
 * <p>
 * 원본 JSON이나 개인정보 전문을 그대로 담지 않고, 본사가 "누가, 언제, 무엇을 바꿨는지"를
 * 파악할 수 있는 요약만 제공한다.
 */
public record AuditEventView(
        String eventType,
        String createdAt,
        String staffEmail,
        String staffDisplayName,
        String staffRole,
        String hotelId,
        String hotelName,
        String reservationId,
        String guestName,
        String roomNumber,
        String summary) {
}
