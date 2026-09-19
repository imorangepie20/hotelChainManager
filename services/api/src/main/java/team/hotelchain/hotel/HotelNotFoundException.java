package team.hotelchain.hotel;

import java.util.UUID;

/**
 * 존재하지 않는 지점을 조회했을 때 사용한다.
 */
public class HotelNotFoundException extends RuntimeException {

    public HotelNotFoundException(UUID hotelId) {
        super("존재하지 않는 지점입니다. 지점을 확인해 주세요.");
    }
}
