package team.hotelchain.hotel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

/**
 * 본사가 객실 유형을 추가한다.
 * <p>
 * 가격·재고·예약 확정에 닿는 첫 쓰기 동작이므로 권한은 {@link StaffAccessService}가 검증하고,
 * 같은 요청의 중복 생성은 {@code room_type_command}의 멱원 키와 요청 지문으로 막는다.
 * <p>
 * 객실 유형 행만 만들면 고객 검색에 나타나지 않는다. {@code rate_plan}·{@code rate_day}·
 * {@code inventory_day}를 같은 트랜잭션에서 함께 심어야 즉시 예약 가능하다.
 */
@Service
public class RoomTypeCommandService {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 100;

    static final String SEED_RATE_PLAN_NAME = "기본 요금제";
    static final String SEED_POLICY_VERSION = "FLEX-2026-01";

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;
    private final Clock clock;
    private final RoomTypeSeedProperties seed;

    public RoomTypeCommandService(JdbcTemplate jdbc, StaffAccessService access, Clock clock,
            RoomTypeSeedProperties seed) {
        this.jdbc = jdbc;
        this.access = access;
        this.clock = clock;
        this.seed = seed;
    }

    @Transactional
    public RoomTypeCreateResponse create(String token, UUID hotelId, String idempotencyKey, RoomTypeCreateRequest request) {
        StaffPrincipal staff = access.requireHeadquarters(token);
        requireIdempotencyKey(idempotencyKey);
        request.validate();
        if (!hotelExists(hotelId)) {
            throw new HotelNotFoundException(hotelId);
        }

        // 지점 단위로 쓰기를 직렬화해 같은 내용의 동시 재시도가 한 쪽만 저장되게 한다.
        jdbc.query("select id from hotel where id = ? for update", rs -> { }, hotelId);

        String requestHash = requestHash(staff.id(), hotelId, request);
        UUID existing = findRoomTypeId(hotelId, staff.id(), idempotencyKey, requestHash);
        if (existing != null) {
            return loadExisting(existing, hotelId);
        }

        UUID roomTypeId = UUID.randomUUID();
        jdbc.update("insert into room_type (id, hotel_id, name, max_occupancy) values (?, ?, ?, ?)",
                roomTypeId, hotelId, request.name().trim(), request.maxOccupancy());

        // 시드는 객실 유형과 같은 트랜잭션에 묶인다. 실패하면 유형 행도 롤백돼서
        // "유형은 있는데 가격·재고가 없어 예약 불가" 상태가 생기지 않는다.
        java.time.ZoneId hotelZone = hotelTimezone(hotelId);
        RoomTypeCreateResponse.SeedBatch batch = seedDefaults(roomTypeId, hotelZone);

        jdbc.update("""
                insert into room_type_command
                    (id, hotel_id, staff_id, room_type_id, idempotency_key, request_hash, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), hotelId, staff.id(), roomTypeId, idempotencyKey, requestHash,
                java.sql.Timestamp.from(clock.instant()));

        return RoomTypeCreateResponse.created(roomTypeId, hotelId, request.name().trim(), request.maxOccupancy(),
                new RoomTypeCreateResponse.SeedSummary(
                        batch.ratePlanId(), batch.ratePlanName(), batch.rateDays().size(),
                        batch.defaultRateKrw(), batch.inventoryCapacity(), true));
    }

    /**
     * 기본 요금제와 그 기간의 일자 재고를 심는다.
     * <p>
     * 시작일은 생성일(UTC) 자정으로 정규화한다. 고객 검색이 체크인일을 오늘부터 잡으므로
     * 과거 일자는 의미가 없다. {@code ON CONFLICT DO NOTHING}으로 멱원 재시도가
     * 같은 일자를 두 번 만들지 않게 한다.
     */
    private RoomTypeCreateResponse.SeedBatch seedDefaults(UUID roomTypeId, java.time.ZoneId hotelZone) {
        UUID ratePlanId = UUID.randomUUID();
        jdbc.update("""
                insert into rate_plan (id, room_type_id, name, breakfast_included, policy_version)
                values (?, ?, ?, false, ?)
                on conflict do nothing
                """, ratePlanId, roomTypeId, SEED_RATE_PLAN_NAME, SEED_POLICY_VERSION);

        LocalDate firstDay = LocalDate.now(clock.withZone(hotelZone));
        List<RoomTypeCreateResponse.RateDayRow> rateDays = new ArrayList<>();
        List<RoomTypeCreateResponse.InventoryDayRow> inventoryDays = new ArrayList<>();
        for (int offset = 0; offset < seed.rateDays(); offset++) {
            LocalDate stayDate = firstDay.plusDays(offset);
            int inserted = jdbc.update("""
                    insert into rate_day (rate_plan_id, stay_date, amount_krw)
                    values (?, ?, ?)
                    on conflict do nothing
                    """, ratePlanId, stayDate, seed.defaultRateKrw());
            if (inserted > 0) {
                rateDays.add(new RoomTypeCreateResponse.RateDayRow(stayDate, seed.defaultRateKrw()));
            }
            int inventoryInserted = jdbc.update("""
                    insert into inventory_day (room_type_id, stay_date, capacity, held, confirmed)
                    values (?, ?, ?, 0, 0)
                    on conflict do nothing
                    """, roomTypeId, stayDate, seed.inventoryCapacity());
            if (inventoryInserted > 0) {
                inventoryDays.add(new RoomTypeCreateResponse.InventoryDayRow(stayDate, seed.inventoryCapacity()));
            }
        }

        jdbc.update("update room_type set seed_completed_at = ? where id = ?",
                java.sql.Timestamp.from(clock.instant()), roomTypeId);

        return new RoomTypeCreateResponse.SeedBatch(
                ratePlanId, SEED_RATE_PLAN_NAME, rateDays, inventoryDays,
                seed.defaultRateKrw(), seed.inventoryCapacity());
    }

    private UUID findRoomTypeId(UUID hotelId, UUID staffId, String idempotencyKey, String requestHash) {
        // 같은 멱원 키로 이미 처리됐으면 저장된 객실 유형을 그대로 돌려준다.
        UUID byKey = jdbc.query("""
                select room_type_id from room_type_command
                 where hotel_id = ? and staff_id = ? and idempotency_key = ?
                """, rs -> rs.next() ? rs.getObject("room_type_id", UUID.class) : null,
                hotelId, staffId, idempotencyKey);
        if (byKey != null) return byKey;
        // 멱원 키는 달라도 같은 직원의 같은 내용 재시도면 같은 결과를 돌려준다.
        return jdbc.query("""
                select room_type_id from room_type_command
                 where hotel_id = ? and staff_id = ? and request_hash = ?
                """, rs -> rs.next() ? rs.getObject("room_type_id", UUID.class) : null,
                hotelId, staffId, requestHash);
    }

    private RoomTypeCreateResponse loadExisting(UUID roomTypeId, UUID hotelId) {
        return jdbc.query("""
                select id, name, max_occupancy, seed_completed_at from room_type where id = ? and hotel_id = ?
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            UUID roomType = rs.getObject("id", UUID.class);
            java.sql.Timestamp seededAt = rs.getTimestamp("seed_completed_at");
            return RoomTypeCreateResponse.existing(roomType, hotelId, rs.getString("name"),
                    rs.getInt("max_occupancy"), seedSummary(roomType, seededAt));
        }, roomTypeId, hotelId);
    }

    private RoomTypeCreateResponse.SeedSummary seedSummary(UUID roomTypeId, java.sql.Timestamp seededAt) {
        // 시드가 끝나지 않은 유형은 빈 요약을 돌려준다. 이미 만든 뒤 시드가 꼬인 경우를 구분한다.
        if (seededAt == null) {
            return RoomTypeCreateResponse.noSeed();
        }
        return jdbc.query("""
                select rp.id as rate_plan_id, rp.name as rate_plan_name,
                       (select count(*) from rate_day rd where rd.rate_plan_id = rp.id) as priced_days,
                       coalesce((select min(rd.amount_krw) from rate_day rd where rd.rate_plan_id = rp.id), 0) as amount_krw,
                       (select max(i.capacity) from inventory_day i where i.room_type_id = ?) as inventory_capacity
                  from rate_plan rp
                 where rp.room_type_id = ?
                 order by rp.id
                 limit 1
                """, rs -> {
            if (!rs.next()) {
                return RoomTypeCreateResponse.noSeed();
            }
            return new RoomTypeCreateResponse.SeedSummary(
                    rs.getObject("rate_plan_id", UUID.class),
                    rs.getString("rate_plan_name"),
                    rs.getInt("priced_days"),
                    rs.getInt("amount_krw"),
                    rs.getInt("inventory_capacity") == 0 ? 0 : rs.getInt("inventory_capacity"),
                    true);
        }, roomTypeId, roomTypeId);
    }

    private java.time.ZoneId hotelTimezone(UUID hotelId) {
        // 재고는 지점 현지 날짜 기준이어야 한다. Clock이 UTC이고 지점이 Asia/Seoul이면
        // UTC 자정은 한국 시간 전일 09:00이므로 LocalDate.now(clock)가 하루 앞선다.
        // 시드 시작일도 같은 규칙을 따라야 오늘 도착 검색이 빈 결과를 돌려받지 않는다.
        String timezone = jdbc.query(
                "select timezone from hotel where id = ?",
                rs -> rs.next() ? rs.getString("timezone") : null, hotelId);
        if (timezone == null || timezone.isBlank()) {
            return java.time.ZoneId.of("Asia/Seoul");
        }
        try {
            return java.time.ZoneId.of(timezone);
        } catch (Exception exception) {
            return java.time.ZoneId.of("Asia/Seoul");
        }
    }

    private boolean hotelExists(UUID hotelId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from hotel where id = ?", Integer.class, hotelId);
        return count != null && count > 0;
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("올바른 Idempotency-Key가 필요합니다.");
        }
    }

    static String requestHash(UUID staffId, UUID hotelId, RoomTypeCreateRequest request) {
        String payload = staffId + "|" + hotelId + "|" + request.name().trim() + "|" + request.maxOccupancy();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
