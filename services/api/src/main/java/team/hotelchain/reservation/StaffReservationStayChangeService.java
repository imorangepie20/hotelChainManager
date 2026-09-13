package team.hotelchain.reservation;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
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
import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

@Service
public class StaffReservationStayChangeService {
    private final JdbcTemplate jdbc;
    private final StaffAccessService staffAccess;
    private final ReservationAccess reservationAccess;
    private final Clock clock;

    public StaffReservationStayChangeService(
            JdbcTemplate jdbc,
            StaffAccessService staffAccess,
            ReservationAccess reservationAccess,
            Clock clock) {
        this.jdbc = jdbc;
        this.staffAccess = staffAccess;
        this.reservationAccess = reservationAccess;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StaffReservationStayChangePreview preview(
            String token,
            UUID reservationId,
            StaffReservationStayChangePreviewRequest request) {
        StaffPrincipal staff = staffAccess.current(token);
        StayDates dates = validateDates(request == null ? null : request.checkIn(), request == null ? null : request.checkOut());
        ReservationStay reservation = findReservation(reservationId, false);
        staffAccess.requireHotel(staff, reservation.hotelId());
        requireChangeable(reservation);
        requireFutureTarget(reservation, dates);

        return new StaffReservationStayChangePreview(
                reservationId,
                dates.checkIn(),
                dates.checkOut(),
                reservation.totalKrw(),
                reservation.currency(),
                findOffers(reservation, dates));
    }

    @Transactional
    public StaffReservationStayChangeResult update(
            String token,
            UUID reservationId,
            String idempotencyKey,
            StaffReservationStayChangeRequest request) {
        StaffPrincipal staff = staffAccess.current(token);
        ValidatedChange change = validateChange(idempotencyKey, request);
        ReservationStay reservation = findReservation(reservationId, true);
        staffAccess.requireHotel(staff, reservation.hotelId());
        String requestHash = requestHash(staff.id(), change);

        ExistingChange existing = existingChange(reservationId, idempotencyKey);
        if (existing != null) {
            if (!existing.requestHash().equals(requestHash)) {
                throw new BusinessConflictException(
                        "IDEMPOTENCY_CONFLICT", "같은 요청 키에 다른 숙박 조건 변경 요청이 사용되었습니다.");
            }
            return existing.result(reservationId, reservation.currency());
        }

        requireChangeable(reservation);
        requireFutureTarget(reservation, change.dates());
        if (reservation.roomTypeId().equals(change.roomTypeId())
                && reservation.checkIn().equals(change.dates().checkIn())
                && reservation.checkOut().equals(change.dates().checkOut())) {
            throw new BusinessConflictException(
                    "RESERVATION_STAY_UNCHANGED", "변경할 날짜 또는 객실 유형을 선택해 주세요.");
        }

        TargetPlan targetPlan = findTargetPlan(change.roomTypeId(), change.ratePlanId());
        if (targetPlan == null || !targetPlan.hotelId().equals(reservation.hotelId())) {
            throw new BusinessConflictException("SOLD_OUT", "선택한 객실 유형과 요금제를 이용할 수 없습니다.");
        }
        if ((long) targetPlan.maxOccupancy() * reservation.rooms()
                < (long) reservation.adults() + reservation.children()) {
            throw new BusinessConflictException(
                    "RESERVATION_PARTY_CAPACITY_EXCEEDED", "선택한 객실 유형의 최대 수용 인원을 초과했습니다.");
        }

        Map<InventoryKey, InventoryRow> inventory = lockInventory(reservation, change);
        requireOriginalInventory(reservation, inventory);
        List<RateNight> targetNights = findRateNights(change);
        requireTargetAvailability(reservation, change, inventory, targetNights);
        long totalKrw = targetNights.stream().mapToLong(RateNight::amountKrw).sum() * reservation.rooms();
        if (totalKrw != change.expectedTotal()) {
            throw new BusinessConflictException("PRICE_CHANGED", "요금이 변경되었습니다. 최신 변경안을 다시 확인해 주세요.");
        }

        int released = jdbc.update("""
                update inventory_day set confirmed = confirmed - ?
                where room_type_id = ? and stay_date >= ? and stay_date < ? and confirmed >= ?
                """, reservation.rooms(), reservation.roomTypeId(), reservation.checkIn(), reservation.checkOut(),
                reservation.rooms());
        if (released != reservation.nights()) {
            throw new IllegalStateException("반환할 확정 재고가 예약 숙박일과 일치하지 않습니다.");
        }
        int confirmed = jdbc.update("""
                update inventory_day set confirmed = confirmed + ?
                where room_type_id = ? and stay_date >= ? and stay_date < ?
                """, reservation.rooms(), change.roomTypeId(), change.dates().checkIn(), change.dates().checkOut());
        if (confirmed != targetNights.size()) {
            throw new IllegalStateException("확정할 재고가 변경 숙박일과 일치하지 않습니다.");
        }

        jdbc.update("""
                update reservation
                set room_type_id = ?, rate_plan_id = ?, check_in = ?, check_out = ?, total_krw = ?
                where id = ?
                """, change.roomTypeId(), change.ratePlanId(), change.dates().checkIn(),
                change.dates().checkOut(), totalKrw, reservationId);
        jdbc.update("delete from reservation_night where reservation_id = ?", reservationId);
        for (RateNight night : targetNights) {
            jdbc.update("insert into reservation_night values (?, ?, ?)",
                    reservationId, night.stayDate(), night.amountKrw());
        }

        long differenceKrw = totalKrw - reservation.totalKrw();
        jdbc.update("""
                insert into reservation_stay_change
                    (id, reservation_id, idempotency_key, request_hash, staff_id,
                     previous_room_type_id, room_type_id, previous_rate_plan_id, rate_plan_id,
                     previous_check_in, previous_check_out, check_in, check_out,
                     previous_total_krw, total_krw, difference_krw)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), reservationId, idempotencyKey, requestHash, staff.id(),
                reservation.roomTypeId(), change.roomTypeId(), reservation.ratePlanId(), change.ratePlanId(),
                reservation.checkIn(), reservation.checkOut(), change.dates().checkIn(), change.dates().checkOut(),
                reservation.totalKrw(), totalKrw, differenceKrw);

        return new StaffReservationStayChangeResult(
                reservationId, change.dates().checkIn(), change.dates().checkOut(),
                change.roomTypeId(), change.ratePlanId(), totalKrw, differenceKrw, reservation.currency());
    }

    private List<StaffReservationStayChangeOffer> findOffers(ReservationStay reservation, StayDates dates) {
        int nights = Math.toIntExact(ChronoUnit.DAYS.between(dates.checkIn(), dates.checkOut()));
        List<OfferNight> rows = jdbc.query("""
                select rt.id as room_type_id, rt.name as room_type_name,
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
                reservation.hotelId(), dates.checkIn(), dates.checkOut(), reservation.rooms(),
                reservation.adults() + reservation.children());
        Map<UUID, List<OfferNight>> byRatePlan = new LinkedHashMap<>();
        rows.forEach(row -> byRatePlan.computeIfAbsent(row.ratePlanId(), ignored -> new ArrayList<>()).add(row));
        return byRatePlan.values().stream()
                .filter(group -> group.size() == nights)
                .filter(group -> group.stream().mapToInt(OfferNight::remaining).min().orElse(0) >= reservation.rooms())
                .map(group -> toOffer(group, reservation))
                .toList();
    }

    private StaffReservationStayChangeOffer toOffer(List<OfferNight> group, ReservationStay reservation) {
        OfferNight first = group.getFirst();
        List<NightlyPrice> nightlyPrices = group.stream()
                .map(night -> new NightlyPrice(night.stayDate(), night.amountKrw()))
                .toList();
        long totalKrw = nightlyPrices.stream().mapToLong(NightlyPrice::amount).sum() * reservation.rooms();
        return new StaffReservationStayChangeOffer(
                first.roomTypeId(), first.roomTypeName(), first.ratePlanId(), first.ratePlanName(),
                first.breakfastIncluded(), group.stream().mapToInt(OfferNight::remaining).min().orElseThrow(),
                nightlyPrices, totalKrw, totalKrw - reservation.totalKrw(), reservation.currency());
    }

    private Map<InventoryKey, InventoryRow> lockInventory(
            ReservationStay reservation,
            ValidatedChange change) {
        List<InventoryRow> rows = jdbc.query("""
                select room_type_id, stay_date, capacity, held, confirmed
                from inventory_day
                where (room_type_id = ? and stay_date >= ? and stay_date < ?)
                   or (room_type_id = ? and stay_date >= ? and stay_date < ?)
                order by room_type_id, stay_date
                for update
                """, this::mapInventory,
                reservation.roomTypeId(), reservation.checkIn(), reservation.checkOut(),
                change.roomTypeId(), change.dates().checkIn(), change.dates().checkOut());
        Map<InventoryKey, InventoryRow> inventory = new LinkedHashMap<>();
        rows.forEach(row -> inventory.put(new InventoryKey(row.roomTypeId(), row.stayDate()), row));
        return inventory;
    }

    private void requireOriginalInventory(
            ReservationStay reservation,
            Map<InventoryKey, InventoryRow> inventory) {
        for (LocalDate date = reservation.checkIn(); date.isBefore(reservation.checkOut()); date = date.plusDays(1)) {
            InventoryRow row = inventory.get(new InventoryKey(reservation.roomTypeId(), date));
            if (row == null || row.confirmed() < reservation.rooms()) {
                throw new IllegalStateException("기존 확정 재고가 예약 숙박일과 일치하지 않습니다.");
            }
        }
    }

    private void requireTargetAvailability(
            ReservationStay reservation,
            ValidatedChange change,
            Map<InventoryKey, InventoryRow> inventory,
            List<RateNight> targetNights) {
        int expectedNights = Math.toIntExact(
                ChronoUnit.DAYS.between(change.dates().checkIn(), change.dates().checkOut()));
        if (targetNights.size() != expectedNights) {
            throw new BusinessConflictException("SOLD_OUT", "선택한 숙박일의 요금이 준비되지 않았습니다.");
        }
        for (RateNight night : targetNights) {
            InventoryRow row = inventory.get(new InventoryKey(change.roomTypeId(), night.stayDate()));
            if (row == null) {
                throw new BusinessConflictException("SOLD_OUT", "선택한 객실의 재고가 부족합니다.");
            }
            int ownInventory = change.roomTypeId().equals(reservation.roomTypeId())
                    && !night.stayDate().isBefore(reservation.checkIn())
                    && night.stayDate().isBefore(reservation.checkOut())
                    ? reservation.rooms() : 0;
            if (row.capacity() - row.held() - row.confirmed() + ownInventory < reservation.rooms()) {
                throw new BusinessConflictException("SOLD_OUT", "선택한 객실의 재고가 부족합니다.");
            }
        }
    }

    private List<RateNight> findRateNights(ValidatedChange change) {
        return jdbc.query("""
                select stay_date, amount_krw from rate_day
                where rate_plan_id = ? and stay_date >= ? and stay_date < ?
                order by stay_date
                """, (rs, rowNumber) -> new RateNight(
                        rs.getDate("stay_date").toLocalDate(), rs.getInt("amount_krw")),
                change.ratePlanId(), change.dates().checkIn(), change.dates().checkOut());
    }

    private ReservationStay findReservation(UUID reservationId, boolean lock) {
        String sql = """
                select r.id, r.room_type_id, r.rate_plan_id, rt.hotel_id, h.timezone,
                       r.check_in, r.check_out, r.adults, r.children, r.rooms, r.status,
                       r.total_krw, r.currency,
                       (select count(*) from reservation_night rn where rn.reservation_id = r.id) as nights,
                       (select count(*) from reservation_room_assignment rra where rra.reservation_id = r.id)
                           as assignments
                from reservation r
                join room_type rt on rt.id = r.room_type_id
                join hotel h on h.id = rt.hotel_id
                where r.id = ?
                """ + (lock ? " for update of r" : "");
        ReservationStay reservation = jdbc.query(
                sql, rs -> rs.next() ? mapReservation(rs) : null, reservationId);
        if (reservation == null) throw new ReservationNotFoundException();
        return reservation;
    }

    private TargetPlan findTargetPlan(UUID roomTypeId, UUID ratePlanId) {
        return jdbc.query("""
                select rt.hotel_id, rt.max_occupancy
                from room_type rt join rate_plan rp on rp.room_type_id = rt.id
                where rt.id = ? and rp.id = ?
                """, rs -> rs.next()
                        ? new TargetPlan(rs.getObject("hotel_id", UUID.class), rs.getInt("max_occupancy"))
                        : null,
                roomTypeId, ratePlanId);
    }

    private ExistingChange existingChange(UUID reservationId, String idempotencyKey) {
        return jdbc.query("""
                select request_hash, check_in, check_out, room_type_id, rate_plan_id, total_krw, difference_krw
                from reservation_stay_change
                where reservation_id = ? and idempotency_key = ?
                """, rs -> rs.next() ? new ExistingChange(
                        rs.getString("request_hash"),
                        rs.getDate("check_in").toLocalDate(),
                        rs.getDate("check_out").toLocalDate(),
                        rs.getObject("room_type_id", UUID.class),
                        rs.getObject("rate_plan_id", UUID.class),
                        rs.getLong("total_krw"),
                        rs.getLong("difference_krw")) : null,
                reservationId, idempotencyKey);
    }

    private void requireChangeable(ReservationStay reservation) {
        if (!"CONFIRMED".equals(reservation.status())) {
            throw new BusinessConflictException(
                    "RESERVATION_STATE_CONFLICT", "확정된 예약의 숙박 조건만 변경할 수 있습니다.");
        }
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(reservation.timezone())));
        if (!today.isBefore(reservation.checkIn())) {
            throw new BusinessConflictException(
                    "RESERVATION_STAY_CHANGE_TOO_LATE", "체크인일이 지난 예약은 숙박 조건을 변경할 수 없습니다.");
        }
        if (reservation.assignments() > 0) {
            throw new BusinessConflictException(
                    "RESERVATION_HAS_ROOM_ASSIGNMENT", "배정 객실이 있는 예약은 숙박 조건을 변경할 수 없습니다.");
        }
    }

    private void requireFutureTarget(ReservationStay reservation, StayDates dates) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(reservation.timezone())));
        if (!dates.checkIn().isAfter(today)) {
            throw new BusinessConflictException(
                    "RESERVATION_STAY_CHANGE_TOO_LATE", "변경할 체크인 날짜는 지점 현지 날짜 이후여야 합니다.");
        }
    }

    private ValidatedChange validateChange(String idempotencyKey, StaffReservationStayChangeRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new IllegalArgumentException("올바른 Idempotency-Key가 필요합니다.");
        }
        if (request == null || request.roomTypeId() == null || request.ratePlanId() == null
                || request.expectedTotal() < 0) {
            throw new IllegalArgumentException("변경할 객실 유형, 요금제와 예상 금액을 확인해 주세요.");
        }
        return new ValidatedChange(
                validateDates(request.checkIn(), request.checkOut()),
                request.roomTypeId(), request.ratePlanId(), request.expectedTotal());
    }

    private StayDates validateDates(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("체크아웃 날짜는 체크인 날짜보다 이후여야 합니다.");
        }
        return new StayDates(checkIn, checkOut);
    }

    private String requestHash(UUID staffId, ValidatedChange change) {
        String value = String.join(":",
                staffId.toString(),
                change.dates().checkIn().toString(),
                change.dates().checkOut().toString(),
                change.roomTypeId().toString(),
                change.ratePlanId().toString(),
                Long.toString(change.expectedTotal()));
        return reservationAccess.sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private OfferNight mapOfferNight(ResultSet rs, int rowNumber) throws SQLException {
        return new OfferNight(
                rs.getObject("room_type_id", UUID.class),
                rs.getString("room_type_name"),
                rs.getObject("rate_plan_id", UUID.class),
                rs.getString("rate_plan_name"),
                rs.getBoolean("breakfast_included"),
                rs.getDate("stay_date").toLocalDate(),
                rs.getInt("amount_krw"),
                rs.getInt("remaining"));
    }

    private InventoryRow mapInventory(ResultSet rs, int rowNumber) throws SQLException {
        return new InventoryRow(
                rs.getObject("room_type_id", UUID.class),
                rs.getDate("stay_date").toLocalDate(),
                rs.getInt("capacity"),
                rs.getInt("held"),
                rs.getInt("confirmed"));
    }

    private ReservationStay mapReservation(ResultSet rs) throws SQLException {
        return new ReservationStay(
                rs.getObject("id", UUID.class),
                rs.getObject("room_type_id", UUID.class),
                rs.getObject("rate_plan_id", UUID.class),
                rs.getObject("hotel_id", UUID.class),
                rs.getString("timezone"),
                rs.getDate("check_in").toLocalDate(),
                rs.getDate("check_out").toLocalDate(),
                rs.getInt("adults"),
                rs.getInt("children"),
                rs.getInt("rooms"),
                rs.getString("status"),
                rs.getLong("total_krw"),
                rs.getString("currency").trim(),
                rs.getInt("nights"),
                rs.getInt("assignments"));
    }

    private record StayDates(LocalDate checkIn, LocalDate checkOut) {
    }

    private record ValidatedChange(
            StayDates dates, UUID roomTypeId, UUID ratePlanId, long expectedTotal) {
    }

    private record ReservationStay(
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
            int nights,
            int assignments) {
    }

    private record TargetPlan(UUID hotelId, int maxOccupancy) {
    }

    private record OfferNight(
            UUID roomTypeId,
            String roomTypeName,
            UUID ratePlanId,
            String ratePlanName,
            boolean breakfastIncluded,
            LocalDate stayDate,
            int amountKrw,
            int remaining) {
    }

    private record InventoryKey(UUID roomTypeId, LocalDate stayDate) {
    }

    private record InventoryRow(
            UUID roomTypeId, LocalDate stayDate, int capacity, int held, int confirmed) {
    }

    private record RateNight(LocalDate stayDate, int amountKrw) {
    }

    private record ExistingChange(
            String requestHash,
            LocalDate checkIn,
            LocalDate checkOut,
            UUID roomTypeId,
            UUID ratePlanId,
            long totalKrw,
            long differenceKrw) {
        StaffReservationStayChangeResult result(UUID reservationId, String currency) {
            return new StaffReservationStayChangeResult(
                    reservationId, checkIn, checkOut, roomTypeId, ratePlanId,
                    totalKrw, differenceKrw, currency);
        }
    }
}
