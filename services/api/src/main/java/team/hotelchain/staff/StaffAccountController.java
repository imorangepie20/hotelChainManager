package team.hotelchain.staff;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import team.hotelchain.web.ApiError;

@RestController
@RequestMapping("/api/staff/staff")
public class StaffAccountController {

    private final StaffAccountQueryService query;
    private final StaffAccountCommandService command;

    public StaffAccountController(StaffAccountQueryService query, StaffAccountCommandService command) {
        this.query = query;
        this.command = command;
    }

    @GetMapping
    public List<StaffAccountView> list(
            @RequestHeader(value = "X-Staff-Session", required = false) String token,
            @RequestParam(required = false) Integer limit) {
        return query.list(token, limit);
    }

    // 본사가 새 직원을 만든다. 응답은 201이고 멱원 재호출은 같은 직원을 돌려준다.
    @PostMapping
    public ResponseEntity<StaffAccountCreateResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Staff-Session", required = false) String token,
            @RequestBody StaffAccountCreateRequest request) {
        StaffAccountCreateResponse response = command.create(token, idempotencyKey, request);
        if (response.created()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    // 본사가 직원의 임시 비밀번호를 재발급한다. 비밀번호는 응답에 한 번만 내보낸다.
    @PostMapping("/{staffId}/password")
    public ResponseEntity<StaffPasswordResetResponse> resetPassword(
            @PathVariable UUID staffId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Staff-Session", required = false) String token) {
        StaffPasswordResetResponse response = command.resetPassword(token, staffId, idempotencyKey);
        if (response.created()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(StaffEmailConflictException.class)
    public ResponseEntity<ApiError> emailConflict(StaffEmailConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("STAFF_EMAIL_DUPLICATE", exception.getMessage()));
    }

    @ExceptionHandler(StaffAccountNotFoundException.class)
    public ResponseEntity<ApiError> accountNotFound(StaffAccountNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("STAFF_ACCOUNT_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(StaffPasswordCooldownException.class)
    public ResponseEntity<ApiError> passwordCooldown(StaffPasswordCooldownException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("STAFF_PASSWORD_RESET_COOLDOWN", exception.getMessage()));
    }
}
