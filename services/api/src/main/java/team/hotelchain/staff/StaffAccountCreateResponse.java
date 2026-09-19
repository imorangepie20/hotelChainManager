package team.hotelchain.staff;

import java.util.UUID;

/**
 * 본사가 직원을 만들 때 받는 응답이다.
 * 임시 비밀번호는 생성 시 한 번만 내보내고 저장하지 않는다.
 */
public record StaffAccountCreateResponse(
        String staffId,
        String email,
        String displayName,
        String role,
        String hotelId,
        String hotelName,
        String temporaryPassword,
        boolean created) {
}
