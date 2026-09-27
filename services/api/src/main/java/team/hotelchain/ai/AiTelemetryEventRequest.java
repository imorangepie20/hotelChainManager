package team.hotelchain.ai;

import java.time.Instant;
import java.util.UUID;

public record AiTelemetryEventRequest(
        UUID eventId,
        String eventType,
        String model,
        String outcome,
        Double elapsedMs,
        Instant occurredAt) {
}
