package team.hotelchain.staff;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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

@RestController
@RequestMapping("/api/staff/staff")
public class StaffAccountController {

    private final StaffAccountQueryService query;
    private final StaffAccountCommandService command;
    private final StaffAccountDeletionService deletion;
    private final StaffSelfUpdateService selfUpdate;

    public StaffAccountController(StaffAccountQueryService query, StaffAccountCommandService command,
            StaffAccountDeletionService deletion, StaffSelfUpdateService selfUpdate) {
        this.query = query;
        this.command = command;
        this.deletion = deletion;
        this.selfUpdate = selfUpdate;
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

    // 본사가 직원의 역할과 소속 지점을 바꾼다. 멱원 재호출은 같은 결과를 돌려준다.
    @PatchMapping("/{staffId}")
    public ResponseEntity<StaffAccountUpdateResponse> update(
            @PathVariable UUID staffId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Staff-Session", required = false) String token,
            @RequestBody StaffAccountUpdateRequest request) {
        StaffAccountUpdateResponse response = command.update(token, staffId, idempotencyKey, request);
        if (response.created()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    // 본사가 직원을 비활성하거나 다시 활성한다. 비활성 직원의 세션은 즉시 끊긴다.
    @PatchMapping("/{staffId}/active")
    public ResponseEntity<StaffAccountActivationResponse> activate(
            @PathVariable UUID staffId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Staff-Session", required = false) String token,
            @RequestBody StaffAccountActivationRequest request) {
        StaffAccountActivationResponse response = command.activate(token, staffId, idempotencyKey, request);
        if (response.created()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    // 본사가 직원 계정을 영구 삭제한다. 행을 남기고 식별자만 비운다.
    // 되돌릴 수 없으므로 진행 중인 작업이 있으면 409로 거부한다.
    @DeleteMapping("/{staffId}")
    public ResponseEntity<StaffAccountDeletionResponse> delete(
            @PathVariable UUID staffId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Staff-Session", required = false) String token) {
        StaffAccountDeletionResponse response = deletion.delete(token, staffId, idempotencyKey);
        if (response.deleted()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    // 직원이 본인의 표시 이름·비밀번호를 바꾼다. 전 역할이 쓸 수 있다.
    // 역할·소속 지점은 본사 전용 권한이다.
    @PatchMapping("/me")
    public ResponseEntity<StaffSelfUpdateResponse> updateSelf(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Staff-Session", required = false) String token,
            @RequestBody StaffSelfUpdateRequest request) {
        StaffSelfUpdateResponse response = selfUpdate.update(token, idempotencyKey, request);
        if (response.changed()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(StaffSelfUpdateException.class)
    public ResponseEntity<ApiError> selfUpdate(StaffSelfUpdateException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("STAFF_PASSWORD_MISMATCH", exception.getMessage()));
    }

    @ExceptionHandler(StaffAccountDeletionConflictException.class)
    public ResponseEntity<ApiError> deletionConflict(StaffAccountDeletionConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("STAFF_DELETION_CONFLICT", exception.getMessage()));
    }

    @ExceptionHandler(StaffSelfModificationException.class)
    public ResponseEntity<ApiError> selfModification(StaffSelfModificationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("STAFF_SELF_MODIFICATION_FORBIDDEN", exception.getMessage()));
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
