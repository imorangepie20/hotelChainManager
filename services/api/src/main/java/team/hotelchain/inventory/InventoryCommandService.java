package team.hotelchain.inventory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.hotel.HotelNotFoundException;
import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

/**
 * 본사가 객실 유형별 일자 재고의 총량을 조정한다.
 * <p>
 * 총량은 특정 날짜의 예약 가능 여부를 결정하므로, 내릴 때는 이미 확정·보류된
 * 건수 아래로 내릴 수 없다. 재고는 음수가 될 수 없기 때문이다.
 * <p>
 * 같은 요청의 중복 조정은 {@code inventory_command}의 멱원 키와 요청 지문으로 막는다.
 */
@Service
public class InventoryCommandService {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 100;
    private static final int MAX_DAYS_PER_REQUEST = 92;

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;
    private final Clock clock;

    public InventoryCommandService(JdbcTemplate jdbc, StaffAccessService access, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public InventoryAdjustResponse adjust(String token, UUID hotelId, String idempotencyKey,
            InventoryAdjustRequest request) {
        StaffPrincipal staff = access.requireHeadquarters(token);
        requireIdempotencyKey(idempotencyKey);
        request.validate();
        if (!hotelExists(hotelId)) {
            throw new HotelNotFoundException(hotelId);
        }
        if (!roomTypeBelongsToHotel(hotelId, request.roomTypeId())) {
            throw new HotelNotFoundException(hotelId);
        }

        // 객실 유형 단위로 쓰기를 직렬화해 같은 내용의 동시 재시도가 한 쪽만 저장되게 한다.
        jdbc.query("select id from room_type where id = ? for update", rs -> { }, request.roomTypeId());

        String requestHash = requestHash(staff.id(), hotelId, request);
        List<InventoryAdjustRequest.DayAdjustment> adjustments = request.adjustments();
        if (adjustments.size() > MAX_DAYS_PER_REQUEST) {
            throw new IllegalArgumentException(
                    "한 번에 조정할 수 있는 일자는 최대 " + MAX_DAYS_PER_REQUEST + "일입니다.");
        }
        List<LocalDate> requestedDates = adjustments.stream()
                .map(InventoryAdjustRequest.DayAdjustment::stayDate)
                .toList();

        List<InventoryAdjustResponse.AdjustedDay> existing = findAdjusted(
                hotelId, staff.id(), idempotencyKey, requestHash, requestedDates);
        if (!existing.isEmpty()) {
            return new InventoryAdjustResponse(hotelId, request.roomTypeId(), existing, false);
        }

        // 총량을 내릴 때만 충돌 검사를 한다. 올리면 어떤 예약도 새 한도를 초과하지 않는다.
        int conflicts = countConflictingDays(request.roomTypeId(), adjustments);
        if (conflicts > 0) {
            throw new InventoryCapacityConflictException(conflicts);
        }

        List<InventoryAdjustResponse.AdjustedDay> adjusted = new java.util.ArrayList<>();
        for (InventoryAdjustRequest.DayAdjustment adjustment : adjustments) {
            adjusted.add(applyAdjustment(request.roomTypeId(), adjustment));
        }

        jdbc.update("""
                insert into inventory_command
                    (id, hotel_id, staff_id, room_type_id, idempotency_key, request_hash, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), hotelId, staff.id(), request.roomTypeId(),
                idempotencyKey, requestHash, java.sql.Timestamp.from(clock.instant()));

        return new InventoryAdjustResponse(hotelId, request.roomTypeId(), adjusted, true);
    }

    private InventoryAdjustResponse.AdjustedDay applyAdjustment(
            UUID roomTypeId, InventoryAdjustRequest.DayAdjustment adjustment) {
        LocalDate stayDate = adjustment.stayDate();
        int updated = jdbc.update("""
                update inventory_day set capacity = ?
                 where room_type_id = ? and stay_date = ?
                """, adjustment.capacity(), roomTypeId, stayDate);
        if (updated == 0) {
            // 시드되지 않은 일자는 재고 행이 없다. 늘리는 것도 불가능하다.
            throw new InventoryDayNotFoundException();
        }
        return jdbc.query("""
                select capacity, held, confirmed, (capacity - held - confirmed) as remaining
                  from inventory_day
                 where room_type_id = ? and stay_date = ?
                """, rs -> rs.next() ? new InventoryAdjustResponse.AdjustedDay(
                        stayDate, rs.getInt("capacity"), rs.getInt("held"),
                        rs.getInt("confirmed"), rs.getInt("remaining")) : null, roomTypeId, stayDate);
    }

    /**
     * 새 총량이 확정·보류 건수보다 작은 일자 수를 센다.
     * <p>
     * 총량을 올리면 어떤 일자도 새 한도를 초과하지 않으므로 검사하지 않는다.
     * 재고는 음수가 될 수 없고, {@code held + confirmed <= capacity} 제약이 있다.
     */
    private int countConflictingDays(UUID roomTypeId, List<InventoryAdjustRequest.DayAdjustment> adjustments) {
        int conflicts = 0;
        for (InventoryAdjustRequest.DayAdjustment adjustment : adjustments) {
            Integer used = jdbc.query("""
                    select coalesce(held, 0) + coalesce(confirmed, 0) as used
                      from inventory_day
                     where room_type_id = ? and stay_date = ?
                    """, rs -> rs.next() ? rs.getInt("used") : null,
                    roomTypeId, adjustment.stayDate());
            // 재고 행이 없는 일자는 총량을 정해도 충돌이 없다.
            if (used != null && adjustment.capacity() < used) {
                conflicts += 1;
            }
        }
        return conflicts;
    }

    private List<InventoryAdjustResponse.AdjustedDay> findAdjusted(
            UUID hotelId, UUID staffId, String idempotencyKey, String requestHash,
            List<LocalDate> requestedDates) {
        // 같은 멱원 키로 이미 처리됐으면 저장된 결과를 그대로 돌려준다.
        UUID byKey = jdbc.query("""
                select room_type_id from inventory_command
                 where hotel_id = ? and staff_id = ? and idempotency_key = ?
                """, rs -> rs.next() ? rs.getObject("room_type_id", UUID.class) : null,
                hotelId, staffId, idempotencyKey);
        if (byKey == null) {
            // 멱원 키는 달라도 같은 직원의 같은 내용 재시도면 같은 결과를 돌려준다.
            byKey = jdbc.query("""
                    select room_type_id from inventory_command
                     where hotel_id = ? and staff_id = ? and request_hash = ?
                    """, rs -> rs.next() ? rs.getObject("room_type_id", UUID.class) : null,
                    hotelId, staffId, requestHash);
        }
        if (byKey == null) {
            return List.of();
        }
        final UUID roomTypeId = byKey;
        // 재호출은 요청한 일자만 돌려줘야 멱원이 성립한다.
        // 전체 일자를 내보내면 다른 날짜의 재고가 바뀐 뒤 재호출 응답이 달라진다.
        String placeholders = String.join(",", java.util.Collections.nCopies(requestedDates.size(), "?"));
        return jdbc.query("""
                select stay_date, capacity, held, confirmed, (capacity - held - confirmed) as remaining
                  from inventory_day
                 where room_type_id = ? and stay_date in (%s)
                 order by stay_date
                """.formatted(placeholders),
                new PreparedStatementSetter(roomTypeId, requestedDates),
                (rs, rowNumber) -> new InventoryAdjustResponse.AdjustedDay(
                        rs.getDate("stay_date").toLocalDate(),
                        rs.getInt("capacity"), rs.getInt("held"),
                        rs.getInt("confirmed"), rs.getInt("remaining")));
    }

    /**
     * 재호출이 요청했던 일자만 조회하도록 바인딩한다.
     */
    private record PreparedStatementSetter(UUID roomTypeId, List<LocalDate> stayDates)
            implements org.springframework.jdbc.core.PreparedStatementSetter {

        @Override
        public void setValues(java.sql.PreparedStatement statement) throws java.sql.SQLException {
            statement.setObject(1, roomTypeId);
            for (int index = 0; index < stayDates.size(); index++) {
                statement.setObject(index + 2, java.sql.Date.valueOf(stayDates.get(index)));
            }
        }
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

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("올바른 Idempotency-Key가 필요합니다.");
        }
    }

    static String requestHash(UUID staffId, UUID hotelId, InventoryAdjustRequest request) {
        StringBuilder payload = new StringBuilder()
                .append(staffId).append('|').append(hotelId).append('|')
                .append(request.roomTypeId()).append('|');
        for (InventoryAdjustRequest.DayAdjustment adjustment : request.adjustments()) {
            payload.append(adjustment.stayDate()).append(':').append(adjustment.capacity()).append(',');
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
