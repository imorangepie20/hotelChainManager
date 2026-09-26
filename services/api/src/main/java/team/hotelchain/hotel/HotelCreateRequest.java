package team.hotelchain.hotel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 본사가 새 지점을 만들 때 보내는 본문.
 * <p>
 * 이름·지역은 사람이 읽는 라벨이고, 시간대는 재고·요금 시드와 취소 마감 시각의
 * 기준이 된다.
 */
public record HotelCreateRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        @NotBlank
        @Size(max = 50)
        String region,

        @NotBlank
        @Size(max = 50)
        String timezone) {

    String normalizedName() {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ");
    }

    String normalizedRegion() {
        return region == null ? "" : region.trim().replaceAll("\\s+", " ");
    }

    boolean isValidTimezone() {
        if (timezone == null || timezone.isBlank()) {
            return false;
        }
        try {
            java.time.ZoneId.of(timezone.trim());
            return true;
        } catch (Exception exception) {
            return false;
        }
    }
}
