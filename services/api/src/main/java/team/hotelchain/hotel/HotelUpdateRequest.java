package team.hotelchain.hotel;

import jakarta.validation.constraints.Size;

/**
 * 본사가 지점의 이름·지역·시간대를 바꿀 때 보내는 본문.
 * <p>
 * 세 필드 모두 선택이다. 보내지 않은 필드는 현재 값을 유지한다. 이름을 바꿀 때만
 * 다른 지점과의 중복 검사를 한다.
 */
public record HotelUpdateRequest(

        @Size(max = 100)
        String name,

        @Size(max = 50)
        String region,

        @Size(max = 50)
        String timezone) {

    String normalizedName() {
        return name == null ? null : name.trim().replaceAll("\\s+", " ");
    }

    String normalizedRegion() {
        return region == null ? null : region.trim().replaceAll("\\s+", " ");
    }

    String normalizedTimezone() {
        return timezone == null ? null : timezone.trim();
    }

    boolean isValidTimezone() {
        String value = normalizedTimezone();
        if (value == null || value.isEmpty()) {
            return true;
        }
        try {
            java.time.ZoneId.of(value);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    // 본문이 비어 있으면 아무것도 바꾸지 않는다. 실수로 빈 PATCH가 지점을 초기화하지 않는다.
    boolean hasAnyField() {
        return name != null || region != null || timezone != null;
    }
}
