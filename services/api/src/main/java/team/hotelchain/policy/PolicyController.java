package team.hotelchain.policy;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/policies")
public class PolicyController {

    private final PolicyQueryService queries;
    private final PolicyCommandService commands;

    public PolicyController(PolicyQueryService queries, PolicyCommandService commands) {
        this.queries = queries;
        this.commands = commands;
    }

    // 본사가 체인 공통 정책의 현재값을 읽는다.
    @GetMapping
    public PolicyView current(@RequestHeader(value = "X-Staff-Session", required = false) String token) {
        return queries.current(token);
    }

    // 본사가 정책을 바꾼 이력을 최신순으로 읽는다. SELECT만 사용한다.
    @GetMapping("/revisions")
    public PolicyRevisionsView revisions(
            @RequestHeader(value = "X-Staff-Session", required = false) String token,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        return queries.revisions(token, limit, offset);
    }

    // 본사가 취소 정책을 변경한다. 응답은 201이고 멱원 재호출은 200으로 같은 revision을 돌려준다.
    @PutMapping("/cancellation")
    public ResponseEntity<CancellationPolicyUpdateResponse> updateCancellation(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Staff-Session", required = false) String token,
            @RequestBody CancellationPolicyUpdateRequest request) {
        CancellationPolicyUpdateResponse response = commands.updateCancellation(token, idempotencyKey, request);
        return response.created()
                ? ResponseEntity.status(HttpStatus.CREATED).body(response)
                : ResponseEntity.ok(response);
    }

    // 본사가 예약 변경 승인 한도를 변경한다. 진행 중인 변경 요청은 저장된 한도를 유지한다.
    @PutMapping("/change-limit")
    public ResponseEntity<ChangeApprovalLimitUpdateResponse> updateChangeApprovalLimit(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Staff-Session", required = false) String token,
            @RequestBody ChangeApprovalLimitUpdateRequest request) {
        ChangeApprovalLimitUpdateResponse response = commands.updateChangeApprovalLimit(token, idempotencyKey, request);
        return response.created()
                ? ResponseEntity.status(HttpStatus.CREATED).body(response)
                : ResponseEntity.ok(response);
    }
}
