package team.hotelchain.staff;

import java.util.UUID;

/**
 * 직원이 본인 계정을 수정한 결과.
 * <p>
 * {@code changed}가 {@code false}면 멱원 재호출로 같은 결과를 돌려주는 것이다.
 */
public record StaffSelfUpdateResponse(
        UUID staffId,
        String email,
        String displayName,
        String role,
        boolean changed) {
}
