package team.hotelchain.guestrequest;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import team.hotelchain.web.ApiError;

/**
 * 본사·지점 직원이 고객 요청을 조회하고 처리 상태를 바꾼다.
 * <p>
 * 조회는 읽기 전용이고 상태 변경은 멱원 키로 중복을 막는다.
 */
@RestController
@RequestMapping("/api/staff/guest-requests")
public class GuestRequestController {

    private final GuestRequestQueryService queries;
    private final GuestRequestCommandService commands;

    public GuestRequestController(GuestRequestQueryService queries, GuestRequestCommandService commands) {
        this.queries = queries;
        this.commands = commands;
    }

    // 직원이 고객 요청 목록을 읽는다. 본사는 전 지점을, 지점 직원은 자기 지점만 읽는다.
    @GetMapping
    public GuestRequestListView list(
            @RequestHeader(value = "X-Staff-Session", required = false) String token,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID hotelId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        return queries.list(token, status, hotelId, limit, offset);
    }

    // 직원이 고객 요청 1건과 처리 이력을 읽는다.
    @GetMapping("/{requestId}")
    public GuestRequestView get(
            @PathVariable UUID requestId,
            @RequestHeader(value = "X-Staff-Session", required = false) String token) {
        return queries.get(token, requestId);
    }

    // 직원이 고객 요청의 처리 상태를 바꾼다. 멱원 재호출은 200으로 같은 결과를 돌려준다.
    @PostMapping("/{requestId}/transition")
    public ResponseEntity<GuestRequestView> transition(
            @PathVariable UUID requestId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Staff-Session", required = false) String token,
            @RequestBody GuestRequestTransitionRequest request) {
        GuestRequestView view = commands.transition(token, requestId, idempotencyKey, request);
        return ResponseEntity.ok(view);
    }

    @ExceptionHandler(GuestRequestNotFoundException.class)
    public ResponseEntity<ApiError> notFound(GuestRequestNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("GUEST_REQUEST_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(GuestRequestStateConflictException.class)
    public ResponseEntity<ApiError> conflict(GuestRequestStateConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("GUEST_REQUEST_STATE_CONFLICT", exception.getMessage()));
    }
}
