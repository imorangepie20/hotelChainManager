package team.hotelchain.staff;

/**
 * 본사가 직원 임시 비밀번호를 재발급한 뒤 받는 응답이다.
 * <p>
 * 임시 비밀번호 원문은 DB에 보관하지 않으므로 멱원 재호출({@code created == false})에서는
 * 비밀번호를 내려주지 않는다. 사용자는 다시 발급받아야 한다.
 */
public record StaffPasswordResetResponse(
        String staffId,
        String email,
        String displayName,
        String role,
        String hotelId,
        String hotelName,
        String temporaryPassword,
        boolean created,
        long cooldownSeconds) {
}
