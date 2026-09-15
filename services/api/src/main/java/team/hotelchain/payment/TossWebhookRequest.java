package team.hotelchain.payment;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

public record TossWebhookRequest(String eventType, String orderId) {
    public static TossWebhookRequest parse(byte[] body, ObjectMapper json) throws IOException {
        var root = json.readTree(body);
        String eventType = root.path("eventType").asText("");
        String orderId = root.path("data").path("orderId").asText("");
        if (!"PAYMENT_STATUS_CHANGED".equals(eventType)
                || !orderId.matches("[A-Za-z0-9_-]{6,64}")) {
            throw new IllegalArgumentException("지원하지 않는 Toss webhook입니다.");
        }
        return new TossWebhookRequest(eventType, orderId);
    }
}
