package team.hotelchain.hotel;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import team.hotelchain.staff.StaffAccessService;

/**
 * 본사가 객실 유형의 일자별 요금을 읽는다. SELECT만 사용한다.
 * <p>
 * 가격·재고·예약 확정의 권한은 Spring Boot에 있다. 이 서비스는
 * 본사 관리자에게 현재 가격을 보여주기만 한다.
 */
@Service
public class RateQueryService {

    private static final int MAX_DAYS = 92;

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;

    public RateQueryService(JdbcTemplate jdbc, StaffAccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    /**
     * 객실 유형의 일자별 금액을 돌려준다.
     * <p>
     * {@code ratePlanId}를 명시하면 그 요금제를, 명시하지 않으면 기본 요금제를
     * 읽는다. {@link RoomTypeRatePlanService}와 같은 선택 기준을 쓴다.
     * 다른 지점의 유형을 읽지 않도록 {@code hotel_id} 쌍으로 찾는다.
     */
    public RoomTypeRatesView listRates(String token, UUID hotelId, UUID roomTypeId, UUID ratePlanId,
            LocalDate from, LocalDate to) {
        access.requireHotel(token, hotelId);
        if (!hotelExists(hotelId)) {
            throw new HotelNotFoundException(hotelId);
        }
        if (!roomTypeBelongsToHotel(hotelId, roomTypeId)) {
            throw new HotelNotFoundException(hotelId);
        }

        LocalDate start = from;
        LocalDate end = to;
        if (start == null) {
            start = LocalDate.now();
        }
        if (end == null || end.isBefore(start)) {
            end = start.plusDays(MAX_DAYS - 1);
        }
        long span = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        if (span > MAX_DAYS) {
            throw new IllegalArgumentException("조회 기간은 최대 " + MAX_DAYS + "일입니다.");
        }

        UUID resolvedRatePlanId = ratePlanId != null
                ? ratePlanId
                : defaultRatePlanId(roomTypeId);
        if (resolvedRatePlanId == null) {
            // 요금제가 없는 유형은 가격을 읽을 수 없다. 빈 목록을 돌려준다.
            return new RoomTypeRatesView(hotelId, roomTypeId, null, null, List.of());
        }
        // 명시한 요금제가 해당 유형에 속하지 않으면 다른 유형의 금액을 읽는 것이다.
        if (ratePlanId != null && !ratePlanBelongsToRoomType(roomTypeId, ratePlanId)) {
            throw new RatePlanNotFoundException(ratePlanId);
        }

        List<RoomTypeRatesView.RateDay> days = jdbc.query("""
                select stay_date, amount_krw
                  from rate_day
                 where rate_plan_id = ? and stay_date between ? and ?
                 order by stay_date
                """, (rs, rowNumber) -> new RoomTypeRatesView.RateDay(
                        rs.getDate("stay_date").toLocalDate(),
                        rs.getInt("amount_krw")), resolvedRatePlanId, start, end);

        return new RoomTypeRatesView(hotelId, roomTypeId, resolvedRatePlanId,
                ratePlanName(resolvedRatePlanId), days);
    }

    private UUID defaultRatePlanId(UUID roomTypeId) {
        return jdbc.query("""
                select rp.id from rate_plan rp
                 where rp.room_type_id = ?
                 order by (select rt.seed_completed_at is null from room_type rt where rt.id = rp.room_type_id),
                          rp.created_at, rp.id
                 limit 1
                """, rs -> rs.next() ? rs.getObject("id", UUID.class) : null, roomTypeId);
    }

    private String ratePlanName(UUID ratePlanId) {
        return jdbc.query(
                "select name from rate_plan where id = ?",
                rs -> rs.next() ? rs.getString("name") : null, ratePlanId);
    }

    private boolean ratePlanBelongsToRoomType(UUID roomTypeId, UUID ratePlanId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from rate_plan where id = ? and room_type_id = ?",
                Integer.class, ratePlanId, roomTypeId);
        return count != null && count > 0;
    }

    private boolean hotelExists(UUID hotelId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from hotel where id = ?", Integer.class, hotelId);
        return count != null && count > 0;
    }

    private boolean roomTypeBelongsToHotel(UUID hotelId, UUID roomTypeId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from room_type where id = ? and hotel_id = ?", Integer.class, roomTypeId, hotelId);
        return count != null && count > 0;
    }
}
