package team.hotelchain.payment.settlement;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import team.hotelchain.staff.StaffAccessService;

/**
 * 본사 읽기 전용 정산·대사 조회. GET만 노출하며 어떤 결제·정산 상태도 변경하지 않는다.
 * 정산 worker가 활성화된 toss-live 환경에서만 로드된다.
 */
@RestController
@RequestMapping("/api/staff/settlements")
@ConditionalOnExpression("${payment.toss.settlement-enabled:false} and '${payment.provider:fake}' == 'toss-live'")
public class TossSettlementController {
    private final StaffAccessService access;
    private final TossSettlementQueryService query;

    public TossSettlementController(StaffAccessService access, TossSettlementQueryService query) {
        this.access = access;
        this.query = query;
    }

    @GetMapping("/runs")
    public TossSettlementQueryService.SettlementRunsView runs(
            @RequestHeader("X-Staff-Session") String token,
            @RequestParam(defaultValue = "20") int limit) {
        access.requireHeadquarters(token);
        return query.runs(limit);
    }

    @GetMapping("/runs/{runId}")
    public TossSettlementQueryService.SettlementRunDetailView run(
            @PathVariable UUID runId,
            @RequestHeader("X-Staff-Session") String token,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int limit) {
        access.requireHeadquarters(token);
        return query.run(runId, status, limit);
    }
}
