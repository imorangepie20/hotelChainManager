package team.hotelchain.hotel;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import team.hotelchain.staff.StaffAccessService;

@Service
public class HotelCatalogQueryService {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;

    public HotelCatalogQueryService(JdbcTemplate jdbc, StaffAccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    public RoomTypeCatalogResponse listRoomTypes(String token, UUID hotelId, Integer limit, Integer offset) {
        access.requireHotel(token, hotelId);
        return listRoomTypes(hotelId, limit, offset);
    }

    /**
     * 본사가 수정 화면에 미리 채울 기본 요금제의 현재값을 돌려준다.
     * 다른 지점의 유형을 읽지 않도록 {@code hotel_id} 쌍으로 찾는다. SELECT만 사용한다.
     */
    public RoomTypeCatalogView.RoomDefaults roomDefaults(UUID hotelId, UUID roomTypeId, String token) {
        access.requireHotel(token, hotelId);
        if (!hotelExists(hotelId)) {
            throw new HotelNotFoundException(hotelId);
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM room_type WHERE id = ? AND hotel_id = ?", Integer.class, roomTypeId, hotelId);
        if (count == null || count == 0) {
            throw new HotelNotFoundException(hotelId);
        }
        return roomDefaults(roomTypeId);
    }

    private RoomTypeCatalogResponse listRoomTypes(UUID hotelId, Integer limit, Integer offset) {
        if (!hotelExists(hotelId)) {
            throw new HotelNotFoundException(hotelId);
        }
        int pageSize = clamp(limit);
        int pageOffset = offset == null ? 0 : Math.max(0, offset);

        List<CatalogRow> rows = jdbc.query("""
                SELECT rt.id AS room_type_id, rt.name AS room_type_name, rt.max_occupancy,
                       rp.id AS rate_plan_id, rp.name AS rate_plan_name,
                       rp.breakfast_included, rp.policy_version,
                       COUNT(rd.stay_date) AS priced_days,
                       MIN(rd.amount_krw) AS min_amount_krw,
                       MAX(rd.amount_krw) AS max_amount_krw,
                       AVG(rd.amount_krw) AS avg_amount_krw
                  FROM room_type rt
                  LEFT JOIN rate_plan rp ON rp.room_type_id = rt.id
                  LEFT JOIN rate_day rd ON rd.rate_plan_id = rp.id
                 WHERE rt.hotel_id = ?
                 GROUP BY rt.id, rp.id
                 ORDER BY rt.id, rp.created_at, rp.id
                 LIMIT ? OFFSET ?
                 """, this::mapRow, hotelId, pageSize, pageOffset);

        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM room_type WHERE hotel_id = ?", Integer.class, hotelId);

        return new RoomTypeCatalogResponse(
                hotelId,
                total == null ? 0 : total,
                groupRows(rows));
    }

    /**
     * 본사가 수정 화면에 미리 채울 기본 요금제의 현재값을 돌려준다.
     * 카탈로그 본문과 같은 선택 기준을 쓴다. SELECT만 사용한다.
     */
    public RoomTypeCatalogView.RoomDefaults roomDefaults(UUID roomTypeId) {
        return jdbc.query("""
                SELECT rp.id AS rate_plan_id, rp.name AS rate_plan_name, rp.breakfast_included,
                       MIN(rd.amount_krw) AS min_amount_krw
                  FROM rate_plan rp
                  LEFT JOIN rate_day rd ON rd.rate_plan_id = rp.id
                 WHERE rp.room_type_id = ?
                 GROUP BY rp.id, rp.name, rp.breakfast_included
                 ORDER BY (SELECT rt.seed_completed_at IS NULL FROM room_type rt WHERE rt.id = rp.room_type_id),
                          rp.created_at, rp.id
                 LIMIT 1
                """, rs -> rs.next() ? new RoomTypeCatalogView.RoomDefaults(
                        rs.getObject("rate_plan_id", UUID.class),
                        rs.getString("rate_plan_name"),
                        rs.getBoolean("breakfast_included"),
                        rs.getObject("min_amount_krw", Integer.class)) : null, roomTypeId);
    }

    private boolean hotelExists(UUID hotelId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hotel WHERE id = ?", Integer.class, hotelId);
        return count != null && count > 0;
    }

    private int clamp(Integer limit) {
        if (limit == null) return MAX_LIMIT;
        return Math.min(MAX_LIMIT, Math.max(MIN_LIMIT, limit));
    }

    private List<RoomTypeCatalogView> groupRows(List<CatalogRow> rows) {
        Map<UUID, List<CatalogRow>> byRoomType = new LinkedHashMap<>();
        for (CatalogRow row : rows) {
            byRoomType.computeIfAbsent(row.roomTypeId(), ignored -> new ArrayList<>()).add(row);
        }
        List<RoomTypeCatalogView> roomTypes = new ArrayList<>();
        for (Map.Entry<UUID, List<CatalogRow>> entry : byRoomType.entrySet()) {
            List<CatalogRow> group = entry.getValue();
            CatalogRow first = group.getFirst();
            List<RoomTypeCatalogView.RatePlanSummary> ratePlans = group.stream()
                    .filter(row -> row.ratePlanId() != null)
                    .map(row -> new RoomTypeCatalogView.RatePlanSummary(
                            row.ratePlanId(),
                            row.ratePlanName(),
                            row.breakfastIncluded(),
                            row.policyVersion(),
                            row.pricedDays(),
                            row.minAmountKrw(),
                            row.maxAmountKrw(),
                            row.avgAmountKrw() == null ? null : Math.round(row.avgAmountKrw() * 100.0) / 100.0))
                    .toList();
            roomTypes.add(new RoomTypeCatalogView(
                    first.roomTypeId(), first.roomTypeName(), first.maxOccupancy(),
                    first.ratePlanId() != null && first.breakfastIncluded(),
                    first.minAmountKrw(), ratePlans));
        }
        return roomTypes;
    }

    private CatalogRow mapRow(ResultSet rs, int rowNumber) throws SQLException {
        return new CatalogRow(
                rs.getObject("room_type_id", UUID.class),
                rs.getString("room_type_name"),
                rs.getInt("max_occupancy"),
                rs.getObject("rate_plan_id", UUID.class),
                rs.getString("rate_plan_name"),
                rs.getBoolean("breakfast_included"),
                rs.getString("policy_version"),
                rs.getInt("priced_days"),
                rs.getObject("min_amount_krw", Integer.class),
                rs.getObject("max_amount_krw", Integer.class),
                rs.getObject("avg_amount_krw") == null ? null : rs.getDouble("avg_amount_krw"));
    }

    private record CatalogRow(
            UUID roomTypeId, String roomTypeName, int maxOccupancy,
            UUID ratePlanId, String ratePlanName, boolean breakfastIncluded, String policyVersion,
            int pricedDays, Integer minAmountKrw, Integer maxAmountKrw, Double avgAmountKrw) {
    }
}
