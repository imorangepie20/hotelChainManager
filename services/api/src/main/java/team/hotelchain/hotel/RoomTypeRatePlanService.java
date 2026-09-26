package team.hotelchain.hotel;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 객실 유형에 깔리는 기본 요금제를 읽고, 본사가 그 요금제의 조식 포함 여부와
 * 기본 요금을 바꾼다.
 * <p>
 * 본사가 만든 유형은 {@code seed_completed_at}이 채워진 기본 요금제 1개를 가지므로
 * 그것을 기본으로 삼고, 시드 이력이 없으면 가장 오래된 요금제를 기본으로 삼는다.
 * 모든 동작은 호출자의 트랜잭션 안에서 일어난다.
 */
@Service
public class RoomTypeRatePlanService {

    private final JdbcTemplate jdbc;

    public RoomTypeRatePlanService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 기본 요금제의 현재 조식 포함 여부와 대표 금액을 돌려준다.
     * 요금제가 없으면 모두 {@code null}이다.
     */
    public RatePlanDefaults loadDefaults(UUID roomTypeId) {
        // seededCompletedAt이 있는 유형의 요금제가 우선이고, 없으면 가장 오래된
        // 요금제를 쓴다. rp.id는 UUID라 삽입 순서를 알 수 없으므로 created_at으로
        // 순서를 정한다. 여러 요금제가 있어도 항상 같은 요금제가 기본이 된다.
        List<RatePlanDefaults> rows = jdbc.query("""
                select rp.id as rate_plan_id, rp.name as rate_plan_name, rp.breakfast_included,
                       (select min(rd.amount_krw) from rate_day rd where rd.rate_plan_id = rp.id) as min_amount_krw,
                       (select max(rd.amount_krw) from rate_day rd where rd.rate_plan_id = rp.id) as max_amount_krw
                  from rate_plan rp
                 where rp.room_type_id = ?
                 order by (select rt.seed_completed_at is null from room_type rt where rt.id = rp.room_type_id),
                          rp.created_at, rp.id
                 """, (rs, rowNumber) -> new RatePlanDefaults(
                rs.getObject("rate_plan_id", UUID.class),
                rs.getString("rate_plan_name"),
                rs.getBoolean("breakfast_included"),
                rs.getObject("min_amount_krw", Integer.class),
                rs.getObject("max_amount_krw", Integer.class)), roomTypeId);
        return rows.isEmpty() ? RatePlanDefaults.empty() : rows.getFirst();
    }

    /**
     * 기본 요금제의 조식 포함 여부와 일자별 기본 금액을 바꾼다.
     * <p>
     * 조식 포함 여부는 예약의 계약 조건이므로, 확정 예약이 현재 조건으로
     * 예약돼 있으면 거부한다. ({@link #countBreakfastConflicts})
     * <p>
     * 기본 금액은 일자별 요금 전체를 같은 폭으로 움직인다. 이미 설정된
     * 주말·계절 차등은 그대로 두고 기준 금액만 옮기기 위해서다.
     * 이미 확정된 예약의 금액은 변경하지 않는다.
     */
    public UpdatedRatePlan updateDefaults(UUID roomTypeId, boolean breakfastIncluded, int defaultRateKrw) {
        UUID ratePlanId = defaultRatePlanId(roomTypeId);
        if (ratePlanId == null) {
            throw new RoomTypeRatePlanNotFoundException(roomTypeId);
        }

        jdbc.update("update rate_plan set breakfast_included = ? where id = ?",
                breakfastIncluded, ratePlanId);

        // 같은 트랜잭션에서 잠금을 잡은 채로 일자별 금액을 잔차액만큼 더한다.
        // 차등이 없는 유형은 잔차액이 0이라 한 번의 update로 끝난다.
        Integer currentMin = jdbc.queryForObject(
                "select min(amount_krw) from rate_day where rate_plan_id = ?", Integer.class, ratePlanId);
        int base = currentMin == null ? defaultRateKrw : currentMin;
        int delta = defaultRateKrw - base;
        int updatedDays;
        if (delta == 0) {
            updatedDays = jdbc.queryForObject(
                    "select count(*) from rate_day where rate_plan_id = ?", Integer.class, ratePlanId);
        } else {
            updatedDays = jdbc.update(
                    "update rate_day set amount_krw = amount_krw + ? where rate_plan_id = ?",
                    delta, ratePlanId);
        }

        return new UpdatedRatePlan(ratePlanId, breakfastIncluded, defaultRateKrw, updatedDays);
    }

    /**
     * 확정 예약 중 요금제의 조식 조건과 다른 조건으로 예약된 건수를 센다.
     * 조식 포함 여부를 바꾸면 이 예약의 계약 내용이 달라지므로 거부한다.
     */
    public int countBreakfastConflicts(UUID roomTypeId, boolean breakfastIncluded) {
        Integer count = jdbc.queryForObject("""
                select count(*) from reservation r
                  join rate_plan rp on rp.id = r.rate_plan_id
                 where rp.room_type_id = ?
                   and r.status = 'CONFIRMED'
                   and rp.breakfast_included <> ?
                """, Integer.class, roomTypeId, breakfastIncluded);
        return count == null ? 0 : count;
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

    public record RatePlanDefaults(
            UUID ratePlanId,
            String ratePlanName,
            Boolean breakfastIncluded,
            Integer minAmountKrw,
            Integer maxAmountKrw) {

        public static RatePlanDefaults empty() {
            return new RatePlanDefaults(null, null, null, null, null);
        }
    }

    public record UpdatedRatePlan(
            UUID ratePlanId,
            boolean breakfastIncluded,
            int defaultRateKrw,
            int updatedDays) {
    }
}
