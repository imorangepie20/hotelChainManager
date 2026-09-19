package team.hotelchain.staff;

import java.util.List;

/**
 * 본사가 직원 목록을 읽기 전용으로 확인한다.
 * SELECT만 사용하고 직원 정보를 변경하지 않는다.
 */
public record StaffAccountView(
        String id,
        String email,
        String displayName,
        String role,
        String hotelId,
        String hotelName,
        boolean active) {

    public static List<String> ROLES = List.of("HQ_ADMIN", "HQ_EDITOR", "HQ_PUBLISHER", "BRANCH_STAFF");
}
