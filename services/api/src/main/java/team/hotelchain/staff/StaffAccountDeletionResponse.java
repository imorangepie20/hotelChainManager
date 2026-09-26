package team.hotelchain.staff;

import java.util.UUID;

/**
 * 본사가 직원을 삭제한 결과.
 * <p>
 * {@code deleted}가 {@code false}면 멱원 재호출로 같은 결과를 돌려주는 것이다.
 * 삭제된 계정은 이메일·표시 이름이 자리 표시자로 덮여 있어서
 * {@link StaffAccountQueryService#list}에서 빠진다.
 */
public record StaffAccountDeletionResponse(
        UUID staffId,
        String email,
        String displayName,
        boolean deleted,
        int remainingStaff) {
}
