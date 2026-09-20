package team.hotelchain.hotel;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 본사가 새 객실 유형을 만들 때 함께 심는 기본 요금·재고의 기준값.
 * <p>
 * 환경 변수로 조정하지만 이미 만든 객실 유형에는 영향을 주지 않는다.
 * 시드는 어디까지나 첫 값일 뿐이고, 본사가 일자별 요금·재고를 덮어쓴다.
 */
@ConfigurationProperties("hotel.seed")
public record RoomTypeSeedProperties(
        int defaultRateKrw,
        int rateDays,
        int inventoryCapacity) {

    public static final int MIN_RATE_KRW = 1;
    public static final int MIN_DAYS = 1;
    public static final int MIN_CAPACITY = 1;

    public RoomTypeSeedProperties {
        if (defaultRateKrw < MIN_RATE_KRW) {
            throw new IllegalArgumentException(
                    "hotel.seed.default-rate-krw은 " + MIN_RATE_KRW + " 이상이어야 합니다.");
        }
        if (rateDays < MIN_DAYS) {
            throw new IllegalArgumentException(
                    "hotel.seed.rate-days은 " + MIN_DAYS + " 이상이어야 합니다.");
        }
        if (inventoryCapacity < MIN_CAPACITY) {
            throw new IllegalArgumentException(
                    "hotel.seed.inventory-capacity은 " + MIN_CAPACITY + " 이상이어야 합니다.");
        }
    }
}
