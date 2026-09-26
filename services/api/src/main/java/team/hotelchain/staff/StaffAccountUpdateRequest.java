package team.hotelchain.staff;

import java.util.UUID;

/**
 * 본사가 직원의 역할과 소속 지점을 바꿀 때 받는 요청이다.
 * <p>
 * 두 값 모두 null이면 변경하지 않는다. {@code hotelId}가 null이면
 * "소속 지점 없음"을 뜻한다. 역할과 지점의 짝은 서버가 최종 판단한다.
 */
public record StaffAccountUpdateRequest(
        String role,
        UUID hotelId) {

    void validate() {
        if (role == null && hotelId == null) {
            throw new IllegalArgumentException("역할이나 소속 지점 중 하나는 입력해 주세요.");
        }
        if (role != null && !StaffAccountCreateRequest.ALLOWED_ROLES.contains(role)) {
            throw new IllegalArgumentException(
                    "역할은 " + String.join(", ", StaffAccountCreateRequest.ALLOWED_ROLES) + " 중 하나여야 합니다.");
        }
        boolean headquarters = role == null || !"BRANCH_STAFF".equals(role);
        if (headquarters && hotelId != null) {
            throw new IllegalArgumentException("본사 역할은 지점을 가질 수 없습니다.");
        }
        if (!headquarters && hotelId == null) {
            throw new IllegalArgumentException("지점 직원은 소속 지점이 필요합니다.");
        }
    }
}
