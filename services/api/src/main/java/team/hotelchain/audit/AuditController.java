package team.hotelchain.audit;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/staff/audit")
public class AuditController {

    private final AuditQueryService queries;
    private final AuditEventsXlsxService xlsx;

    public AuditController(AuditQueryService queries, AuditEventsXlsxService xlsx) {
        this.queries = queries;
        this.xlsx = xlsx;
    }

    // 본사가 V29~V37 감사 표와 예약 변경 이력을 통합해 읽는다.
    // masked=true면 고객 이름·이메일을 가린다. 화면 공유·캡처 시 개인정보 노출을 막는다.
    @GetMapping
    public AuditEventsView list(
            @RequestHeader(value = "X-Staff-Session", required = false) String token,
            @RequestParam(required = false) UUID reservationId,
            @RequestParam(required = false) UUID hotelId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Boolean masked,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        return queries.list(token, AuditQueryFilters.of(reservationId, hotelId, from, to, masked, limit, offset));
    }

    @GetMapping(value = "/export.xlsx",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportXlsx(
            @RequestHeader(value = "X-Staff-Session", required = false) String token,
            @RequestParam(required = false) UUID reservationId,
            @RequestParam(required = false) UUID hotelId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Boolean masked,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        AuditEventsView view = queries.list(
                token, AuditQueryFilters.of(reservationId, hotelId, from, to, masked, limit, offset));
        String filename = "audit-events" + (view.masked() ? "-masked" : "")
                + "-" + view.offset() + ".xlsx";
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(xlsx.write(view));
    }
}
