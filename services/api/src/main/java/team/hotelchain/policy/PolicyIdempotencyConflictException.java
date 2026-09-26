package team.hotelchain.policy;

import team.hotelchain.reservation.BusinessConflictException;

public class PolicyIdempotencyConflictException extends BusinessConflictException {

    public PolicyIdempotencyConflictException() {
        super("POLICY_IDEMPOTENCY_CONFLICT", "같은 정책 요청 키에 다른 내용이 사용되었습니다.");
    }
}
