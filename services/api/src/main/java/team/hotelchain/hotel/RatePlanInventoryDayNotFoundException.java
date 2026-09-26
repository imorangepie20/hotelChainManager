package team.hotelchain.hotel;

import java.time.LocalDate;

/**
 * 본사가 요금제의 일자별 금액을 심으려는 날짜에 재고가 없을 때 발생한다.
 * <p>
 * 고객 검색이 오퍼를 만들려면 {@code rate_day}와 {@code inventory_day}가
 * 같은 날짜에 있어야 한다. 재고가 없는 날짜의 금액을 심으면 고객에게
 * 보여주지도 못하는 요금제가 생기므로 409로 거부한다.
 */
public class RatePlanInventoryDayNotFoundException extends RuntimeException {

    public RatePlanInventoryDayNotFoundException(LocalDate stayDate) {
        super("해당 날짜(" + stayDate + ")의 재고가 없습니다. 객실 유형을 만들 때 심어둔 기간 안에서 요금제를 추가해 주세요.");
    }
}
