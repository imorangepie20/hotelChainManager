package team.hotelchain.reports;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/reports")
public class ReportController {

    private final OperationsReportService operations;

    public ReportController(OperationsReportService operations) {
        this.operations = operations;
    }

    // 본사가 지점별 매출·점유율·취소율·노쇼율과 예약 변경 승인 건수를 읽기 전용으로 확인한다.
    @GetMapping("/operations")
    public OperationsReportView operations(
            @RequestHeader(value = "X-Staff-Session", required = false) String token,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) UUID hotelId) {
        return operations.operations(token, from, to, hotelId);
    }

    // 본사가 지점의 객실 유형별 매출을 읽기 전용으로 확인한다.
    @GetMapping("/operations/room-types")
    public RoomTypeRevenueView roomTypeRevenue(
            @RequestHeader(value = "X-Staff-Session", required = false) String token,
            @RequestParam UUID hotelId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return operations.roomTypeRevenue(token, hotelId, from, to);
    }
}
