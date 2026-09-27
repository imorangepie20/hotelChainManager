package team.hotelchain.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/concierge/telemetry")
public class AiTelemetryController {

    private final AiTelemetryIngestService ingest;
    private final byte[] expectedAuthorization;

    public AiTelemetryController(AiTelemetryIngestService ingest,
            @Value("${ai.telemetry.ingest-token:}") String token) {
        this.ingest = ingest;
        this.expectedAuthorization = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping
    public ResponseEntity<Void> ingest(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody AiTelemetryEventRequest request) {
        if (!authorized(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        ingest.ingest(request);
        return ResponseEntity.accepted().build();
    }

    private boolean authorized(String authorization) {
        if (expectedAuthorization.length == "Bearer ".length() || authorization == null) return false;
        return MessageDigest.isEqual(expectedAuthorization, authorization.getBytes(StandardCharsets.UTF_8));
    }
}
