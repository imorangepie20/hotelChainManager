package team.hotelchain.audit;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/audit")
public class AuditController {

    private final AuditQueryService queries;

    public AuditController(AuditQueryService queries) {
        this.queries = queries;
    }

    // 본사가 V29~V37 감사 표와 예약 변경 이력을 통합해 읽는다.
    @GetMapping
    public AuditEventsView list(
            @RequestHeader(value = "X-Staff-Session", required = false) String token,
            @RequestParam(required = false) UUID reservationId,
            @RequestParam(required = false) UUID hotelId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        return queries.list(token, AuditQueryFilters.of(reservationId, hotelId, from, to, limit, offset));
    }
}
