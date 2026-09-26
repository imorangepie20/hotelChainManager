package team.hotelchain.inventory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.hotel.HotelNotFoundException;
import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

/**
 * 본사가 CSV로 여러 객실 유형의 여러 날짜 재고를 한 번에 바꾼다.
 * <p>
 * 한 파일의 모든 행을 <strong>한 트랜잭션</strong>에 처리한다. 100행 중
 * 99행만 성공하고 1행이 거부되면 본사가 어느 날짜까지 반영됐는지 알 수
 * 없으므로 전부 성공하거나 전부 실패한다.
 * <p>
 * 총량을 내릴 때는 기존 {@code PATCH .../inventory}와 같은 기준으로
 * 확정·보류 건수 아래로 내릴 수 없다. 재고는 음수가 될 수 없기 때문이다.
 * <p>
 * 같은 요청의 중복 처리는 {@code inventory_command}의 멱원 키와
 * 요청 지문으로 막는다. 모든 동작은 호출자의 트랜잭션 안에서 일어난다.
 */
@Service
public class InventoryImportCommandService {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 100;

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;
    private final InventoryCsvService csv;
    private final Clock clock;

    public InventoryImportCommandService(JdbcTemplate jdbc, StaffAccessService access,
            InventoryCsvService csv, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.csv = csv;
        this.clock = clock;
    }

    /**
     * CSV 본문을 읽어 여러 객실 유형의 재고를 바꾼다.
     * <p>
     * 한 행이라도 거부되면 전체를 롤백한다. 멱원 재호출은 저장된 결과를
     * 그대로 돌려준다.
     */
    @Transactional
    public InventoryImportResponse importCsv(String token, UUID hotelId,
            String idempotencyKey, byte[] body) {
        StaffPrincipal staff = access.requireHeadquarters(token);
        requireIdempotencyKey(idempotencyKey);
        if (!hotelExists(hotelId)) {
            throw new HotelNotFoundException(hotelId);
        }

        InventoryCsvService.ParsedCsv parsed = csv.parse(body);
        Map<UUID, List<InventoryAdjustRequest.DayAdjustment>> grouped =
                parsed.groupedAdjustments();

        for (UUID roomTypeId : grouped.keySet()) {
            if (!roomTypeBelongsToHotel(hotelId, roomTypeId)) {
                throw new HotelNotFoundException(hotelId);
            }
        }

        // 파일 전체를 하나의 요청 지문으로 다루기 위해 본문을 해시한다.
        // 같은 파일을 다시 올리면 같은 지문이 나온다.
        String requestHash = requestHash(staff.id(), hotelId, body);
        InventoryImportResponse existing = findExisting(hotelId, staff.id(), idempotencyKey, requestHash);
        if (existing != null) {
            return existing;
        }

        List<InventoryAdjustResponse.AdjustedDay> applied = new ArrayList<>();
        for (Map.Entry<UUID, List<InventoryAdjustRequest.DayAdjustment>> entry : grouped.entrySet()) {
            applied.addAll(applyAdjustments(entry.getKey(), entry.getValue()));
        }

        jdbc.update("""
                insert into inventory_command
                    (id, hotel_id, staff_id, room_type_id, idempotency_key, request_hash, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), hotelId, staff.id(), applied.isEmpty() ? null : firstRoomTypeId(grouped),
                idempotencyKey, requestHash, java.sql.Timestamp.from(clock.instant()));

        return new InventoryImportResponse(hotelId, parsed.rows().size(), applied.size(),
                parsed.skippedRows(), applied, true);
    }

    private UUID firstRoomTypeId(Map<UUID, List<InventoryAdjustRequest.DayAdjustment>> grouped) {
        return grouped.keySet().iterator().next();
    }

    // 기존 PATCH .../inventory와 같은 충돌 기준을 쓴다.
    private List<InventoryAdjustResponse.AdjustedDay> applyAdjustments(
            UUID roomTypeId, List<InventoryAdjustRequest.DayAdjustment> adjustments) {
        // 객실 유형 단위로 쓰기를 직렬화한다.
        jdbc.query("select id from room_type where id = ? for update", rs -> { }, roomTypeId);

        for (InventoryAdjustRequest.DayAdjustment adjustment : adjustments) {
            validateCapacity(adjustment);
            if (capacityConflict(roomTypeId, adjustment)) {
                throw new InventoryCapacityConflictException(1);
            }
        }

        List<InventoryAdjustResponse.AdjustedDay> applied = new ArrayList<>();
        for (InventoryAdjustRequest.DayAdjustment adjustment : adjustments) {
            applied.add(applyAdjustment(roomTypeId, adjustment));
        }
        return applied;
    }

    private void validateCapacity(InventoryAdjustRequest.DayAdjustment adjustment) {
        if (adjustment.capacity() < InventoryAdjustRequest.MIN_CAPACITY
                || adjustment.capacity() > InventoryAdjustRequest.MAX_CAPACITY) {
            throw new IllegalArgumentException(
                    "재고 총량은 " + InventoryAdjustRequest.MIN_CAPACITY + " 이상 "
                            + InventoryAdjustRequest.MAX_CAPACITY + " 이하여야 합니다.");
        }
    }

    // 총량을 내릴 때만 충돌을 검사한다. 올리면 어떤 예약도 새 한도를
    // 초과하지 않는다.
    private boolean capacityConflict(UUID roomTypeId, InventoryAdjustRequest.DayAdjustment adjustment) {
        Integer used = jdbc.query("""
                select coalesce(held, 0) + coalesce(confirmed, 0) as used
                  from inventory_day
                 where room_type_id = ? and stay_date = ?
                """, rs -> rs.next() ? rs.getInt("used") : null,
                roomTypeId, adjustment.stayDate());
        return used != null && adjustment.capacity() < used;
    }

    private InventoryAdjustResponse.AdjustedDay applyAdjustment(
            UUID roomTypeId, InventoryAdjustRequest.DayAdjustment adjustment) {
        int updated = jdbc.update("""
                update inventory_day set capacity = ?
                 where room_type_id = ? and stay_date = ?
                """, adjustment.capacity(), roomTypeId, adjustment.stayDate());
        if (updated == 0) {
            // 시드되지 않은 일자는 재고 행이 없다.
            throw new InventoryDayNotFoundException();
        }
        return jdbc.query("""
                select stay_date, capacity, held, confirmed, (capacity - held - confirmed) as remaining
                  from inventory_day
                 where room_type_id = ? and stay_date = ?
                """, rs -> rs.next() ? new InventoryAdjustResponse.AdjustedDay(
                        adjustment.stayDate(), rs.getInt("capacity"), rs.getInt("held"),
                        rs.getInt("confirmed"), rs.getInt("remaining")) : null,
                roomTypeId, adjustment.stayDate());
    }

    private InventoryImportResponse findExisting(UUID hotelId, UUID staffId,
            String idempotencyKey, String requestHash) {
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
            return null;
        }
        // 멱원 재호출은 첫 번째 객실 유형의 결과를 돌려준다.
        return loadExisting(hotelId, byKey);
    }

    private InventoryImportResponse loadExisting(UUID hotelId, UUID roomTypeId) {
        List<InventoryAdjustResponse.AdjustedDay> days = jdbc.query("""
                select stay_date, capacity, held, confirmed, (capacity - held - confirmed) as remaining
                  from inventory_day
                 where room_type_id = ?
                 order by stay_date
                 """, (rs, rowNumber) -> new InventoryAdjustResponse.AdjustedDay(
                        rs.getDate("stay_date").toLocalDate(),
                        rs.getInt("capacity"), rs.getInt("held"),
                        rs.getInt("confirmed"), rs.getInt("remaining")), roomTypeId);
        return new InventoryImportResponse(hotelId, days.size(), days.size(), 0, days, false);
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

    static String requestHash(UUID staffId, UUID hotelId, byte[] body) {
        String payload = staffId + "|" + hotelId + "|" + new String(body, StandardCharsets.UTF_8);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
