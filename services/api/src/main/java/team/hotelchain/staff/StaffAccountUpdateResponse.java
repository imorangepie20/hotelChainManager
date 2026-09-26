package team.hotelchain.staff;

import java.util.UUID;

/**
 * 본사가 직원의 역할과 소속 지점을 바꾼 결과. 멱원 재호출은 현재값을
 * 그대로 돌려준다. created가 false면 이미 처리된 요청의 결과를 다시 보낸 것이다.
 */
public record StaffAccountUpdateResponse(
        UUID staffId,
        String email,
        String displayName,
        String role,
        UUID hotelId,
        String hotelName,
        boolean created) {
}
