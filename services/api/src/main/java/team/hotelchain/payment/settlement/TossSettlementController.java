package team.hotelchain.payment.settlement;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

/**
 * 본사 정산·대사 조회와 실행 제어. GET은 읽기 전용이고 POST는 정산·결제·재고
 * 상태를 직접 바꾸지 않고 worker가 처리할 실행만 만들거나 다시 대기시킨다.
 * 정산 worker가 활성화된 토스 결제 환경(toss-test·toss-live)에서만 로드된다.
 */
@RestController
@RequestMapping("/api/staff/settlements")
@ConditionalOnExpression("${payment.toss.settlement-enabled:false} and '${payment.provider:fake}' != 'fake'")
public class TossSettlementController {
    private final StaffAccessService access;
    private final TossSettlementQueryService query;
    private final TossSettlementRunService runs;

    public TossSettlementController(StaffAccessService access, TossSettlementQueryService query,
            TossSettlementRunService runs) {
        this.access = access;
        this.query = query;
        this.runs = runs;
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

    @PostMapping("/runs")
    public ResponseEntity<CreatedRunView> createRun(
            @RequestHeader("X-Staff-Session") String token,
            @Valid @RequestBody CreateRunRequest request) {
        StaffPrincipal principal = access.requireHeadquarters(token);
        UUID runId = runs.create(request.from(), request.to(), principal.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreatedRunView(runId));
    }

    @PostMapping("/runs/{runId}/retry")
    public ResponseEntity<Void> retryRun(
            @PathVariable UUID runId,
            @RequestHeader("X-Staff-Session") String token) {
        access.requireHeadquarters(token);
        runs.retry(runId);
        return ResponseEntity.noContent().build();
    }

    public record CreateRunRequest(@jakarta.validation.constraints.NotNull LocalDate from,
            @jakarta.validation.constraints.NotNull LocalDate to) {
    }

    public record CreatedRunView(UUID runId) {
    }
}
