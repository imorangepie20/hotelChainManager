package team.hotelchain.ai;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/ai-operations/metrics")
public class AiOperationsMetricsController {

    private final AiOperationsMetricsService metrics;

    public AiOperationsMetricsController(AiOperationsMetricsService metrics) {
        this.metrics = metrics;
    }

    @GetMapping
    public ResponseEntity<AiOperationsMetricsView> metrics(
            @RequestHeader(value = "X-Staff-Session", required = false) String token,
            @RequestParam(defaultValue = "24H") String period) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(metrics.metrics(token, period));
    }
}
