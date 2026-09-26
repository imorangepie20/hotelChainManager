package team.hotelchain.staff;

/**
 * 본사가 직원의 활성 상태를 바꾼 결과. 멱원 재호출은 현재값을
 * 그대로 돌려준다. created가 false면 이미 처리된 요청의 결과를 다시 보낸 것이다.
 */
public record StaffAccountActivationResponse(
        String staffId,
        String email,
        String displayName,
        String role,
        String hotelId,
        String hotelName,
        boolean active,
        boolean created) {
}
