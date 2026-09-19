package team.hotelchain.staff;

import java.util.UUID;

/**
 * 본사가 존재하지 않는 직원을 대상으로 작업을 시도했을 때 던진다.
 */
public class StaffAccountNotFoundException extends RuntimeException {

    private final UUID staffId;

    public StaffAccountNotFoundException(UUID staffId) {
        super("직원을 찾을 수 없습니다.");
        this.staffId = staffId;
    }

    public UUID staffId() {
        return staffId;
    }
}
