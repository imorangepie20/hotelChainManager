package team.hotelchain.staff;

/**
 * 직원이 본인 계정을 수정할 때 받는 요청이다.
 * <p>
 * 표시 이름과 새 비밀번호 모두 선택이다. 둘 다 비우면 400이다.
 * 역할·소속 지점은 본사 전용 권한이므로 받지 않는다.
 */
public record StaffSelfUpdateRequest(
        String displayName,
        String currentPassword,
        String newPassword) {

    public static final int NAME_MIN_LENGTH = 1;
    public static final int NAME_MAX_LENGTH = 100;
    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int PASSWORD_MAX_LENGTH = 100;

    void validate() {
        boolean hasName = displayName != null && !displayName.isBlank();
        boolean hasPassword = newPassword != null && !newPassword.isBlank();
        if (!hasName && !hasPassword) {
            throw new IllegalArgumentException("바꿀 이름이나 새 비밀번호 중 하나는 입력해 주세요.");
        }
        if (hasName && (displayName.trim().length() < NAME_MIN_LENGTH
                || displayName.trim().length() > NAME_MAX_LENGTH)) {
            throw new IllegalArgumentException(
                    "이름은 " + NAME_MIN_LENGTH + "자 이상 " + NAME_MAX_LENGTH + "자 이하여야 합니다.");
        }
        // 비밀번호를 바꿀 때는 현재 비밀번호 확인이 필수다.
        if (hasPassword && (currentPassword == null || currentPassword.isBlank())) {
            throw new IllegalArgumentException("새 비밀번호를 바꾸려면 현재 비밀번호를 입력해 주세요.");
        }
        if (hasPassword && (newPassword.length() < PASSWORD_MIN_LENGTH
                || newPassword.length() > PASSWORD_MAX_LENGTH)) {
            throw new IllegalArgumentException(
                    "새 비밀번호는 " + PASSWORD_MIN_LENGTH + "자 이상 "
                            + PASSWORD_MAX_LENGTH + "자 이하여야 합니다.");
        }
    }

    boolean wantsPasswordChange() {
        return newPassword != null && !newPassword.isBlank();
    }
}
