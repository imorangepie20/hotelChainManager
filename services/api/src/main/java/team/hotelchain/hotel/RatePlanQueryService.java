package team.hotelchain.hotel;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import team.hotelchain.staff.StaffAccessService;

/**
 * 본사·지점 직원이 객실 유형의 전체 요금제를 읽는다. SELECT만 사용한다.
 * <p>
 * 가격·재고·예약 확정의 권한은 Spring Boot에 있다. 이 서비스는
 * 현재 요금제 목록을 보여주기만 한다.
 * <p>
 * 본사는 모든 지점을 읽을 수 있고 지점 직원은 자기 지점만 읽을 수 있다.
 * {@link StaffAccessService#requireHotel}이 그 구분을 담당한다.
 */
@Service
public class RatePlanQueryService {

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;

    public RatePlanQueryService(JdbcTemplate jdbc, StaffAccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    /**
     * 객실 유형의 전체 요금제를 돌려준다.
     * <p>
     * 다른 지점의 유형을 읽지 않도록 {@code hotel_id} 쌍으로 찾는다.
     */
    public RatePlanListView list(String token, UUID hotelId, UUID roomTypeId) {
        access.requireHotel(token, hotelId);
        if (!hotelExists(hotelId)) {
            throw new HotelNotFoundException(hotelId);
        }
        if (!roomTypeBelongsToHotel(hotelId, roomTypeId)) {
            throw new HotelNotFoundException(hotelId);
        }

        List<RatePlanListView.RatePlanSummary> ratePlans = jdbc.query("""
                select rp.id as rate_plan_id, rp.name as rate_plan_name, rp.breakfast_included,
                       rp.policy_version,
                       count(rd.stay_date) as priced_days,
                       min(rd.amount_krw) as min_amount_krw,
                       max(rd.amount_krw) as max_amount_krw
                  from rate_plan rp
                  left join rate_day rd on rd.rate_plan_id = rp.id
                 where rp.room_type_id = ?
                 group by rp.id, rp.name, rp.breakfast_included, rp.policy_version
                 order by rp.created_at, rp.id
                """, (rs, rowNumber) -> new RatePlanListView.RatePlanSummary(
                        rs.getObject("rate_plan_id", UUID.class),
                        rs.getString("rate_plan_name"),
                        rs.getBoolean("breakfast_included"),
                        rs.getString("policy_version"),
                        rs.getInt("priced_days"),
                        rs.getObject("min_amount_krw", Integer.class),
                        rs.getObject("max_amount_krw", Integer.class)), roomTypeId);

        return new RatePlanListView(hotelId, roomTypeId, ratePlans);
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
