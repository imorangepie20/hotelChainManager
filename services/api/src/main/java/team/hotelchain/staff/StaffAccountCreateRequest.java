package team.hotelchain.staff;

import java.util.List;
import java.util.UUID;

/**
 * 본사가 새 직원을 만들 때 받는 요청이다.
 * 검증은 서버가 최종 판단한다.
 */
public record StaffAccountCreateRequest(
        String email,
        String displayName,
        String role,
        UUID hotelId) {

    public static final int EMAIL_MAX_LENGTH = 254;
    public static final int NAME_MAX_LENGTH = 100;
    public static final List<String> ALLOWED_ROLES =
            List.of("HQ_ADMIN", "HQ_EDITOR", "HQ_PUBLISHER", "BRANCH_STAFF");

    void validate() {
        if (email == null || email.isBlank() || email.length() > EMAIL_MAX_LENGTH || !email.contains("@")) {
            throw new IllegalArgumentException("올바른 이메일 형식이 필요합니다.");
        }
        if (displayName == null || displayName.isBlank() || displayName.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("이름은 1자 이상 " + NAME_MAX_LENGTH + "자 이하여야 합니다.");
        }
        if (!ALLOWED_ROLES.contains(role)) {
            throw new IllegalArgumentException("역할은 " + String.join(", ", ALLOWED_ROLES) + " 중 하나여야 합니다.");
        }
        boolean headquarters = !"BRANCH_STAFF".equals(role);
        if (headquarters && hotelId != null) {
            throw new IllegalArgumentException("본사 역할은 지점을 가질 수 없습니다.");
        }
        if (!headquarters && hotelId == null) {
            throw new IllegalArgumentException("지점 직원은 소속 지점이 필요합니다.");
        }
    }
}
