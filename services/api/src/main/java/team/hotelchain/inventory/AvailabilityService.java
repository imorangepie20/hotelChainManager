package team.hotelchain.inventory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AvailabilityService {

    private final JdbcTemplate jdbc;

    public AvailabilityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AvailabilityOffer> search(AvailabilityQuery query) {
        validate(query);
        int nights = Math.toIntExact(ChronoUnit.DAYS.between(query.checkIn(), query.checkOut()));
        int partySize = query.adults() + query.children();

        List<OfferNightRow> rows = jdbc.query("""
                SELECT rt.id AS room_type_id, rt.name AS room_type_name,
                       rp.id AS rate_plan_id, rp.name AS rate_plan_name,
                       rp.breakfast_included, rp.policy_version,
                       rd.stay_date, rd.amount_krw,
                       (i.capacity - i.held - i.confirmed) AS remaining
                  FROM room_type rt
                  JOIN rate_plan rp ON rp.room_type_id = rt.id
                  JOIN rate_day rd ON rd.rate_plan_id = rp.id
                  JOIN inventory_day i ON i.room_type_id = rt.id AND i.stay_date = rd.stay_date
                 WHERE rt.hotel_id = ?
                   AND rd.stay_date >= ? AND rd.stay_date < ?
                   AND rt.max_occupancy * ? >= ?
                 ORDER BY rp.id, rd.stay_date
                """, this::mapRow, query.hotelId(), query.checkIn(), query.checkOut(), query.rooms(), partySize);

        Map<UUID, List<OfferNightRow>> byRatePlan = new LinkedHashMap<>();
        rows.forEach(row -> byRatePlan.computeIfAbsent(row.ratePlanId(), ignored -> new ArrayList<>()).add(row));

        return byRatePlan.values().stream()
                .filter(group -> group.size() == nights)
                .filter(group -> group.stream().mapToInt(OfferNightRow::remaining).min().orElse(0) >= query.rooms())
                .map(this::toOffer)
                .toList();
    }

    private void validate(AvailabilityQuery query) {
        if (query.hotelId() == null || query.checkIn() == null || query.checkOut() == null) {
            throw new IllegalArgumentException("호텔과 숙박 날짜는 필수입니다.");
        }
        if (!query.checkOut().isAfter(query.checkIn())) {
            throw new IllegalArgumentException("체크아웃 날짜는 체크인 날짜보다 이후여야 합니다.");
        }
        if (query.adults() < 1 || query.children() < 0 || query.rooms() < 1) {
            throw new IllegalArgumentException("인원과 객실 수를 확인해 주세요.");
        }
    }

    private OfferNightRow mapRow(ResultSet rs, int rowNumber) throws SQLException {
        return new OfferNightRow(
                rs.getObject("room_type_id", UUID.class),
                rs.getString("room_type_name"),
                rs.getObject("rate_plan_id", UUID.class),
                rs.getString("rate_plan_name"),
                rs.getBoolean("breakfast_included"),
                rs.getString("policy_version"),
                rs.getDate("stay_date").toLocalDate(),
                rs.getInt("amount_krw"),
                rs.getInt("remaining"));
    }

    private AvailabilityOffer toOffer(List<OfferNightRow> group) {
        OfferNightRow first = group.getFirst();
        List<NightlyPrice> prices = group.stream()
                .map(row -> new NightlyPrice(row.stayDate(), row.amount()))
                .toList();
        return new AvailabilityOffer(
                first.roomTypeId(), first.roomTypeName(), first.ratePlanId(), first.ratePlanName(),
                first.breakfastIncluded(), group.stream().mapToInt(OfferNightRow::remaining).min().orElseThrow(),
                prices, prices.stream().mapToInt(NightlyPrice::amount).sum(), "KRW", first.policyVersion());
    }

    private record OfferNightRow(
            UUID roomTypeId, String roomTypeName, UUID ratePlanId, String ratePlanName,
            boolean breakfastIncluded, String policyVersion, java.time.LocalDate stayDate,
            int amount, int remaining) {
    }
}
