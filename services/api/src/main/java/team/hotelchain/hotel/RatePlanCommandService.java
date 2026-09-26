package team.hotelchain.hotel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * 본사가 객실 유형에 요금제를 만들고 이름을 바꾼다.
 * <p>
 * 객실 유형 하나에 요금제가 여러 개일 수 있다. 조식 포함 여부·취소 규정·금액이
 * 다른 요금제를 같은 객실에서 동시에 팔아야 하기 때문이다. 예약은
 * {@code rate_plan_id}만 가지고 계약 조건을 판단한다.
 * <p>
 * 가격·재고·예약 확정에 닿는 쓰기 동작이므로 권한은 {@link StaffAccessService}가
 * 검증하고, 같은 요청의 중복 처리는 {@code rate_plan_command}의 멱원 키와
 * 요청 지문으로 막는다.
 * <p>
 * 모든 동작은 호출자의 트랜잭션 안에서 일어난다.
 */
@Service
public class RatePlanCommandService {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 100;

    static final String KIND_CREATE = "CREATE";
    static final String KIND_RENAME = "RENAME";

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;
    private final Clock clock;

    public RatePlanCommandService(JdbcTemplate jdbc, StaffAccessService access, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.clock = clock;
    }

    /**
     * 새 요금제를 만들고 그 기간의 일자별 금액을 심는다.
     * <p>
     * 요금제 행과 금액은 같은 트랜잭션에 묶인다. 실패하면 요금제 행도
     * 롤백돼서 "요금제는 있는데 금액이 없어 오퍼가 안 나오는" 상태가
     * 생기지 않는다. 유형 시드와 같은 원칙이다.
     * <p>
     * 재고는 심지 않는다. 재고는 객실 유형 단위이고 요금제가 공유한다.
     */
    @Transactional
    public RatePlanCreateResponse create(String token, UUID hotelId, UUID roomTypeId,
            String idempotencyKey, RatePlanCreateRequest request) {
        StaffPrincipal staff = access.requireHeadquarters(token);
        requireIdempotencyKey(idempotencyKey);
        request.validate();
        requireRoomType(hotelId, roomTypeId);

        // 객실 유형 단위로 쓰기를 직렬화해 같은 내용의 동시 재시도가
        // 한 쪽만 저장되게 한다.
        jdbc.query("select id from room_type where id = ? for update", rs -> { }, roomTypeId);

        // 멱원 재호출은 이름 중복 검사 앞에서 확인한다.
        // 이미 만들어진 요금제를 중복 검사로 거부하면 재호출이 409가 된다.
        // 시작일을 보내지 않으면 지점 현지 시간대 기준 오늘로 취급한다.
        // Clock이 UTC이고 지점이 Asia/Seoul이면 UTC 자정이 한국 시간 전일 09:00가
        // 돼서 오늘 도착 검색이 빈 결과를 돌려받는다.
        LocalDate fromDate = request.fromDate() != null
                ? request.fromDate()
                : LocalDate.now(clock.withZone(hotelTimezone(hotelId)));

        String requestHash = requestHash(staff.id(), hotelId, roomTypeId, request, fromDate);
        RatePlanCreateResponse existing = findExisting(hotelId, roomTypeId, staff.id(), idempotencyKey, requestHash);
        if (existing != null) {
            return existing;
        }

        if (nameConflict(roomTypeId, request.name().trim(), null)) {
            throw new RatePlanNameConflictException(request.name().trim());
        }

        UUID ratePlanId = UUID.randomUUID();
        java.sql.Timestamp now = java.sql.Timestamp.from(clock.instant());
        jdbc.update("""
                insert into rate_plan (id, room_type_id, name, breakfast_included, policy_version, created_at)
                values (?, ?, ?, ?, ?, ?)
                """, ratePlanId, roomTypeId, request.name().trim(),
                request.breakfastIncludedOrFalse(), request.policyVersion().trim(), now);

        List<RatePlanCreateResponse.RateDayRow> days = seedDays(ratePlanId, roomTypeId, request, fromDate);

        jdbc.update("""
                insert into rate_plan_command
                    (id, hotel_id, staff_id, room_type_id, rate_plan_id, kind,
                     idempotency_key, request_hash, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), hotelId, staff.id(), roomTypeId, ratePlanId, KIND_CREATE,
                idempotencyKey, requestHash, java.sql.Timestamp.from(clock.instant()));

        return RatePlanCreateResponse.created(ratePlanId, hotelId, roomTypeId, request, days, fromDate);
    }

    /**
     * 요금제의 이름을 바꾼다.
     * <p>
     * 조식 포함 여부·정책 버전은 확정 예약의 계약 조건이어서 여기서 바꾸지
     * 않는다. 계약 조건이 다른 요금제가 필요하면 새 요금제를 만드는 것이 맞다.
     * 실제로 바꾸는 값이 없으면 DB를 건드리지 않고 멱원 기록만 남긴다.
     */
    @Transactional
    public RatePlanCreateResponse rename(String token, UUID hotelId, UUID roomTypeId, UUID ratePlanId,
            String idempotencyKey, RatePlanRenameRequest request) {
        StaffPrincipal staff = access.requireHeadquarters(token);
        requireIdempotencyKey(idempotencyKey);
        request.validate();
        requireRoomType(hotelId, roomTypeId);
        if (!ratePlanBelongsToRoomType(roomTypeId, ratePlanId)) {
            throw new RatePlanNotFoundException(ratePlanId);
        }

        // 요금제 단위로 쓰기를 직렬화해 같은 내용의 동시 재시도가
        // 한 쪽만 저장되게 한다.
        jdbc.query("select id from rate_plan where id = ? for update", rs -> { }, ratePlanId);

        // 멱원 재호출은 이름 중복 검사 앞에서 확인한다.
        // 같은 이름으로 다시 보내면 저장된 결과를 돌려줘야 하는데,
        // 중복 검사가 먼저면 재호출이 409가 된다.
        String requestHash = requestHash(staff.id(), hotelId, roomTypeId, ratePlanId, request);
        RatePlanCreateResponse existing = findExistingRename(hotelId, staff.id(), idempotencyKey, requestHash);
        if (existing != null) {
            return existing;
        }

        String trimmed = request.name().trim();
        if (nameConflict(roomTypeId, trimmed, ratePlanId)) {
            throw new RatePlanNameConflictException(trimmed);
        }

        int updated = jdbc.update("update rate_plan set name = ? where id = ?", trimmed, ratePlanId);

        jdbc.update("""
                insert into rate_plan_command
                    (id, hotel_id, staff_id, room_type_id, rate_plan_id, kind,
                     idempotency_key, request_hash, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), hotelId, staff.id(), roomTypeId, ratePlanId, KIND_RENAME,
                idempotencyKey, requestHash, java.sql.Timestamp.from(clock.instant()));

        return RatePlanCreateResponse.renamed(ratePlanId, hotelId, roomTypeId, trimmed, updated > 0);
    }

    /**
     * 요금제의 일자별 금액을 심는다.
     * <p>
     * 재고가 없는 날짜는 거부한다. {@code rate_day}와 {@code inventory_day}가
     * 같은 날짜에 있어야 고객 검색이 오퍼를 만든다. 재고를 심지 않는 이유는
     * 재고가 객실 유형 단위이고 요금제가 공유하기 때문이다.
     */
    private List<RatePlanCreateResponse.RateDayRow> seedDays(
            UUID ratePlanId, UUID roomTypeId, RatePlanCreateRequest request, LocalDate fromDate) {
        List<RatePlanCreateResponse.RateDayRow> days = new ArrayList<>();
        for (int offset = 0; offset < request.daysOrDefault(); offset++) {
            LocalDate stayDate = fromDate.plusDays(offset);
            if (!inventoryDayExists(roomTypeId, stayDate)) {
                // 재고가 없는 날짜의 금액을 만들면 고객에게 보여주지도 못하는
                // 요금제가 생긴다. 롤백되지 않도록 호출자가 트랜잭션을 맡는다.
                throw new RatePlanInventoryDayNotFoundException(stayDate);
            }
            jdbc.update("""
                    insert into rate_day (rate_plan_id, stay_date, amount_krw)
                    values (?, ?, ?)
                    on conflict do nothing
                    """, ratePlanId, stayDate, request.defaultRateKrw());
            days.add(new RatePlanCreateResponse.RateDayRow(stayDate, request.defaultRateKrw()));
        }
        return days;
    }

    private boolean inventoryDayExists(UUID roomTypeId, LocalDate stayDate) {
        Integer count = jdbc.queryForObject(
                "select count(*) from inventory_day where room_type_id = ? and stay_date = ?",
                Integer.class, roomTypeId, stayDate);
        return count != null && count > 0;
    }

    // 이름 중복은 같은 유형 안에서만 검사한다. 다른 유형·다른 지점의 같은 이름은
    // 허용한다. excludeId는 자기 자신의 현재 이름을 제외할 때 쓴다.
    private boolean nameConflict(UUID roomTypeId, String name, UUID excludeId) {
        if (excludeId == null) {
            Integer count = jdbc.queryForObject("""
                    select count(*) from rate_plan
                     where room_type_id = ? and lower(name) = lower(?)
                    """, Integer.class, roomTypeId, name);
            return count != null && count > 0;
        }
        Integer count = jdbc.queryForObject("""
                select count(*) from rate_plan
                 where room_type_id = ? and lower(name) = lower(?)
                   and id <> ?
                """, Integer.class, roomTypeId, name, excludeId);
        return count != null && count > 0;
    }

    private RatePlanCreateResponse findExisting(UUID hotelId, UUID roomTypeId, UUID staffId,
            String idempotencyKey, String requestHash) {
        // 같은 멱원 키로 이미 처리됐으면 저장된 요금제를 그대로 돌려준다.
        UUID byKey = findRatePlanIdByIdempotencyKey(hotelId, staffId, idempotencyKey);
        if (byKey == null) {
            // 멱원 키는 달라도 같은 직원의 같은 내용 재시도면 같은 결과를 돌려준다.
            byKey = findRatePlanIdByRequestHash(hotelId, staffId, requestHash);
        }
        if (byKey == null) {
            return null;
        }
        return loadExistingCreate(byKey, hotelId, roomTypeId);
    }

    private RatePlanCreateResponse findExistingRename(UUID hotelId, UUID staffId,
            String idempotencyKey, String requestHash) {
        UUID byKey = findRatePlanIdByIdempotencyKey(hotelId, staffId, idempotencyKey);
        if (byKey == null) {
            byKey = findRatePlanIdByRequestHash(hotelId, staffId, requestHash);
        }
        if (byKey == null) {
            return null;
        }
        return loadExistingRename(byKey, hotelId);
    }

    private UUID findRatePlanIdByIdempotencyKey(UUID hotelId, UUID staffId, String idempotencyKey) {
        return jdbc.query("""
                select rate_plan_id from rate_plan_command
                 where hotel_id = ? and staff_id = ? and idempotency_key = ?
                """, rs -> rs.next() ? rs.getObject("rate_plan_id", UUID.class) : null,
                hotelId, staffId, idempotencyKey);
    }

    private UUID findRatePlanIdByRequestHash(UUID hotelId, UUID staffId, String requestHash) {
        return jdbc.query("""
                select rate_plan_id from rate_plan_command
                 where hotel_id = ? and staff_id = ? and request_hash = ?
                """, rs -> rs.next() ? rs.getObject("rate_plan_id", UUID.class) : null,
                hotelId, staffId, requestHash);
    }

    private RatePlanCreateResponse loadExistingCreate(UUID ratePlanId, UUID hotelId, UUID roomTypeId) {
        return jdbc.query("""
                select id, name, breakfast_included, policy_version
                  from rate_plan
                 where id = ? and room_type_id = ?
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            return RatePlanCreateResponse.replayed(
                    rs.getObject("id", UUID.class), hotelId, roomTypeId,
                    rs.getString("name"),
                    rs.getBoolean("breakfast_included"),
                    rs.getString("policy_version"),
                    requestDefaultRateKrw(ratePlanId),
                    requestFrom(ratePlanId),
                    requestDays(ratePlanId));
        }, ratePlanId, roomTypeId);
    }

    private RatePlanCreateResponse loadExistingRename(UUID ratePlanId, UUID hotelId) {
        return jdbc.query("""
                select id, name, breakfast_included, policy_version, room_type_id
                  from rate_plan
                 where id = ?
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            // 멱원 재호출은 본사가 보낸 이름이 아니라 저장된 이름을 돌려줘야
            // 같은 결과가 성립한다.
            return RatePlanCreateResponse.renamed(
                    rs.getObject("id", UUID.class), hotelId, rs.getObject("room_type_id", UUID.class),
                    rs.getString("name"), true);
        }, ratePlanId);
    }

    // 멱원 재호출이 만들었던 금액을 다시 보여주기 위해 쓴다.
    private int requestDefaultRateKrw(UUID ratePlanId) {
        Integer amount = jdbc.queryForObject(
                "select min(amount_krw) from rate_day where rate_plan_id = ?", Integer.class, ratePlanId);
        return amount == null ? 0 : amount;
    }

    private java.time.LocalDate requestFrom(UUID ratePlanId) {
        return jdbc.query(
                "select min(stay_date) from rate_day where rate_plan_id = ?",
                rs -> rs.next() ? rs.getDate(1) == null ? null : rs.getDate(1).toLocalDate() : null,
                ratePlanId);
    }

    private List<RatePlanCreateResponse.RateDayRow> requestDays(UUID ratePlanId) {
        return jdbc.query("""
                select stay_date, amount_krw
                  from rate_day
                 where rate_plan_id = ?
                 order by stay_date
                """, (rs, rowNumber) -> new RatePlanCreateResponse.RateDayRow(
                        rs.getDate("stay_date").toLocalDate(),
                        rs.getInt("amount_krw")), ratePlanId);
    }

    private void requireRoomType(UUID hotelId, UUID roomTypeId) {
        if (!hotelExists(hotelId)) {
            throw new HotelNotFoundException(hotelId);
        }
        if (!roomTypeBelongsToHotel(hotelId, roomTypeId)) {
            throw new HotelNotFoundException(hotelId);
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

    private boolean ratePlanBelongsToRoomType(UUID roomTypeId, UUID ratePlanId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from rate_plan where id = ? and room_type_id = ?", Integer.class, ratePlanId, roomTypeId);
        return count != null && count > 0;
    }

    private java.time.ZoneId hotelTimezone(UUID hotelId) {
        // 재고는 지점 현지 날짜 기준이어야 한다. Clock이 UTC이고 지점이 Asia/Seoul이면
        // UTC 자정은 한국 시간 전일 09:00이므로 LocalDate.now(clock)가 하루 앞선다.
        // 요금 일자도 같은 규칙을 따라야 오늘 도착 검색이 빈 결과를 돌려받지 않는다.
        String timezone = jdbc.query(
                "select timezone from hotel where id = ?",
                rs -> rs.next() ? rs.getString("timezone") : null, hotelId);
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of("Asia/Seoul");
        }
        try {
            return ZoneId.of(timezone);
        } catch (Exception exception) {
            return ZoneId.of("Asia/Seoul");
        }
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("올바른 Idempotency-Key가 필요합니다.");
        }
    }

    static String requestHash(UUID staffId, UUID hotelId, UUID roomTypeId, RatePlanCreateRequest request,
            LocalDate fromDate) {
        String payload = staffId + "|" + hotelId + "|" + roomTypeId + "|" + request.name().trim()
                + "|" + request.breakfastIncluded() + "|" + request.policyVersion().trim()
                + "|" + request.defaultRateKrw() + "|" + fromDate + "|" + request.daysOrDefault();
        return sha256(payload);
    }

    static String requestHash(UUID staffId, UUID hotelId, UUID roomTypeId, UUID ratePlanId,
            RatePlanRenameRequest request) {
        String payload = staffId + "|" + hotelId + "|" + roomTypeId + "|" + ratePlanId + "|" + request.name().trim();
        return sha256(payload);
    }

    private static String sha256(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
