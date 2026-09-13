package team.hotelchain.reservationchange;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.inventory.NightlyPrice;
import team.hotelchain.reservation.ReservationNotFoundException;
import team.hotelchain.reservationchange.ReservationStayQuote.SelectedStayOffer;

@Service
public class ReservationStayQuoteService {
    private final JdbcTemplate jdbc;

    public ReservationStayQuoteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public ReservationStayQuote quote(
            UUID reservationId,
            LocalDate checkIn,
            LocalDate checkOut,
            boolean lockReservation) {
        requireDates(checkIn, checkOut);
        ReservationSnapshot reservation = loadReservation(reservationId, lockReservation);
        return new ReservationStayQuote(
                reservation.id(), reservation.roomTypeId(), reservation.ratePlanId(),
                reservation.hotelId(), reservation.timezone(), reservation.checkIn(), reservation.checkOut(),
                checkIn, checkOut, reservation.adults(), reservation.children(), reservation.rooms(),
                reservation.status(), reservation.totalKrw(), reservation.currency(), reservation.operationRevision(),
                reservation.nights(), reservation.assignments(), findOffers(reservation, checkIn, checkOut));
    }

    private void requireDates(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("체크아웃 날짜는 체크인 날짜보다 이후여야 합니다.");
        }
    }

    private ReservationSnapshot loadReservation(UUID reservationId, boolean lockReservation) {
        String sql = """
                select r.id, r.room_type_id, r.rate_plan_id, rt.hotel_id, h.timezone,
                       r.check_in, r.check_out, r.adults, r.children, r.rooms, r.status,
                       r.total_krw, r.currency, r.operation_revision,
                       (select count(*) from reservation_night rn where rn.reservation_id = r.id) as nights,
                       (select count(*) from reservation_room_assignment rra where rra.reservation_id = r.id)
                           as assignments
                from reservation r
                join room_type rt on rt.id = r.room_type_id
                join hotel h on h.id = rt.hotel_id
                where r.id = ?
                """ + (lockReservation ? " for update of r" : "");
        ReservationSnapshot reservation = jdbc.query(
                sql, rs -> rs.next() ? mapReservation(rs) : null, reservationId);
        if (reservation == null) {
            throw new ReservationNotFoundException();
        }
        return reservation;
    }

    private List<SelectedStayOffer> findOffers(
            ReservationSnapshot reservation,
            LocalDate checkIn,
            LocalDate checkOut) {
        int nights = Math.toIntExact(ChronoUnit.DAYS.between(checkIn, checkOut));
        List<OfferNight> rows = jdbc.query("""
                select rt.id as room_type_id, rt.name as room_type_name, rt.max_occupancy,
                       rp.id as rate_plan_id, rp.name as rate_plan_name, rp.breakfast_included,
                       rd.stay_date, rd.amount_krw,
                       i.capacity - i.held - i.confirmed
                         + case when rt.id = ? and rd.stay_date >= ? and rd.stay_date < ? then ? else 0 end
                         as remaining
                from room_type rt
                join rate_plan rp on rp.room_type_id = rt.id
                join rate_day rd on rd.rate_plan_id = rp.id
                join inventory_day i on i.room_type_id = rt.id and i.stay_date = rd.stay_date
                where rt.hotel_id = ? and rd.stay_date >= ? and rd.stay_date < ?
                  and rt.max_occupancy * ? >= ?
                order by rp.id, rd.stay_date
                """, this::mapOfferNight,
                reservation.roomTypeId(), reservation.checkIn(), reservation.checkOut(), reservation.rooms(),
                reservation.hotelId(), checkIn, checkOut, reservation.rooms(),
                reservation.adults() + reservation.children());
        Map<UUID, List<OfferNight>> byRatePlan = new LinkedHashMap<>();
        rows.forEach(row -> byRatePlan.computeIfAbsent(row.ratePlanId(), ignored -> new ArrayList<>()).add(row));
        return byRatePlan.values().stream()
                .filter(group -> group.size() == nights)
                .filter(group -> group.stream().mapToInt(OfferNight::remaining).min().orElse(0) >= reservation.rooms())
                .map(group -> toOffer(group, reservation))
                .toList();
    }

    private SelectedStayOffer toOffer(List<OfferNight> group, ReservationSnapshot reservation) {
        OfferNight first = group.getFirst();
        List<NightlyPrice> nightlyPrices = group.stream()
                .map(night -> new NightlyPrice(night.stayDate(), night.amountKrw()))
                .toList();
        long totalKrw = nightlyPrices.stream().mapToLong(NightlyPrice::amount).sum() * reservation.rooms();
        return new SelectedStayOffer(
                first.roomTypeId(), first.roomTypeName(), first.ratePlanId(), first.ratePlanName(),
                first.breakfastIncluded(), first.maxOccupancy(),
                group.stream().mapToInt(OfferNight::remaining).min().orElseThrow(),
                nightlyPrices, totalKrw, totalKrw - reservation.totalKrw(), reservation.currency());
    }

    private ReservationSnapshot mapReservation(ResultSet rs) throws SQLException {
        return new ReservationSnapshot(
                rs.getObject("id", UUID.class), rs.getObject("room_type_id", UUID.class),
                rs.getObject("rate_plan_id", UUID.class), rs.getObject("hotel_id", UUID.class),
                rs.getString("timezone"), rs.getDate("check_in").toLocalDate(),
                rs.getDate("check_out").toLocalDate(), rs.getInt("adults"), rs.getInt("children"),
                rs.getInt("rooms"), rs.getString("status"), rs.getLong("total_krw"),
                rs.getString("currency").trim(), rs.getLong("operation_revision"),
                rs.getInt("nights"), rs.getInt("assignments"));
    }

    private OfferNight mapOfferNight(ResultSet rs, int rowNumber) throws SQLException {
        return new OfferNight(
                rs.getObject("room_type_id", UUID.class), rs.getString("room_type_name"),
                rs.getObject("rate_plan_id", UUID.class), rs.getString("rate_plan_name"),
                rs.getBoolean("breakfast_included"), rs.getInt("max_occupancy"),
                rs.getDate("stay_date").toLocalDate(), rs.getInt("amount_krw"), rs.getInt("remaining"));
    }

    private record ReservationSnapshot(
            UUID id,
            UUID roomTypeId,
            UUID ratePlanId,
            UUID hotelId,
            String timezone,
            LocalDate checkIn,
            LocalDate checkOut,
            int adults,
            int children,
            int rooms,
            String status,
            long totalKrw,
            String currency,
            long operationRevision,
            int nights,
            int assignments) {
    }

    private record OfferNight(
            UUID roomTypeId,
            String roomTypeName,
            UUID ratePlanId,
            String ratePlanName,
            boolean breakfastIncluded,
            int maxOccupancy,
            LocalDate stayDate,
            int amountKrw,
            int remaining) {
    }
}
