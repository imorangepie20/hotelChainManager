package team.hotelchain.hotel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

/**
 * 본사가 객실 유형의 이름·최대 인원을 바꾼다.
 * <p>
 * {@code max_occupancy}는 예약 가능 조건({@code max_occupancy * rooms >= partySize})에
 * 직결되므로, 값을 내릴 때는 진행 중인 예약 중 새 인원을 초과하는 예약이 있으면
 * 거부한다. 올리는 것은 이미 확정된 예약의 인원이 새 한도를 초과할 수 없으므로
 * 제한 없이 허용한다.
 * <p>
 * 같은 요청의 중복 수정은 {@code room_type_command}의 멱원 키와 요청 지문으로 막는다.
 */
@Service
public class RoomTypeUpdateService {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 100;

    static final String KIND_UPDATE = "UPDATE";

    // 진행 중인 예약 상태. CHECKED_IN·CHECKED_OUT·CANCELLED·NO_SHOW·EXPIRED는
    // 더 이상 인원 조건을 바꿔도 예약에 영향을 주지 않는다.
    private static final String ACTIVE_STATUS = "CONFIRMED";

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;
    private final Clock clock;
    private final RoomTypeRatePlanService ratePlans;

    public RoomTypeUpdateService(JdbcTemplate jdbc, StaffAccessService access, Clock clock,
            RoomTypeRatePlanService ratePlans) {
        this.jdbc = jdbc;
        this.access = access;
        this.clock = clock;
        this.ratePlans = ratePlans;
    }

    @Transactional
    public RoomTypeUpdateResponse update(String token, UUID hotelId, UUID roomTypeId,
            String idempotencyKey, RoomTypeUpdateRequest request) {
        StaffPrincipal staff = access.requireHeadquarters(token);
        requireIdempotencyKey(idempotencyKey);
        request.validate();

        // 다른 지점의 객실 유형을 실수로 바꾸지 않도록 hotel_id 쌍으로 찾는다.
        ExistingRoomType existing = loadRoomType(hotelId, roomTypeId);
        if (existing == null) {
            throw new HotelNotFoundException(hotelId);
        }

        // 동시 수정을 직렬화한다. 두 본사 관리자가 같은 유형을 동시에 바꾸면
        // 한 쪽의 잠금이 풀릴 때까지 다른 쪽이 기다린다.
        jdbc.query("select id from room_type where id = ? for update", rs -> { }, roomTypeId);

        String requestHash = requestHash(staff.id(), hotelId, roomTypeId, request);
        UUID handled = findHandledRoomType(hotelId, staff.id(), idempotencyKey, requestHash);
        if (handled != null) {
            return loadCurrent(handled, hotelId, false);
        }

        String trimmedName = request.name().trim();
        int newOccupancy = request.maxOccupancy();

        // 최대 인원을 내릴 때만 충돌 검사를 한다. 올리면 어떤 예약도
        // 새 한도를 초과하지 않는다.
        if (newOccupancy < existing.maxOccupancy()) {
            int conflicts = countConflictingReservations(roomTypeId, newOccupancy);
            if (conflicts > 0) {
                throw new RoomTypeOccupancyConflictException(conflicts);
            }
        }

        jdbc.update("""
                update room_type set name = ?, max_occupancy = ?
                 where id = ? and hotel_id = ?
                """, trimmedName, newOccupancy, roomTypeId, hotelId);

        // 조식 포함 여부와 기본 요금은 보냈을 때만 바꾼다. 객실 유형의 속성이
        // 아니라 기본 요금제의 속성이므로 보내지 않은 필드를 건드리지 않는다.
        applyRatePlanChanges(roomTypeId, request);

        jdbc.update("""
                insert into room_type_command
                    (id, hotel_id, staff_id, room_type_id, kind, idempotency_key, request_hash,
                     previous_name, previous_max_occupancy, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), hotelId, staff.id(), roomTypeId, KIND_UPDATE,
                idempotencyKey, requestHash, existing.name(), existing.maxOccupancy(),
                java.sql.Timestamp.from(clock.instant()));

        return new RoomTypeUpdateResponse(roomTypeId, hotelId, trimmedName, newOccupancy, true,
                ratePlanSummary(roomTypeId));
    }

    private void applyRatePlanChanges(UUID roomTypeId, RoomTypeUpdateRequest request) {
        boolean breakfastChanged = request.breakfastIncluded() != null;
        boolean rateChanged = request.defaultRateKrw() != null;
        if (!breakfastChanged && !rateChanged) {
            return;
        }

        RoomTypeRatePlanService.RatePlanDefaults defaults = ratePlans.loadDefaults(roomTypeId);
        if (defaults.ratePlanId() == null) {
            // 요금제가 없으면 조식·요금을 바꿀 대상이 없다. 404로 거부한다.
            throw new RoomTypeRatePlanNotFoundException(roomTypeId);
        }

        boolean breakfastIncluded = breakfastChanged ? request.breakfastIncluded() : defaults.breakfastIncluded();
        if (breakfastChanged) {
            // 조식 포함 여부는 예약의 계약 조건이다. 확정 예약이 현재 조건으로
            // 예약돼 있으면 새 조건과 충돌하므로 값을 바꾸지 않고 거부한다.
            int conflicts = ratePlans.countBreakfastConflicts(roomTypeId, breakfastIncluded);
            if (conflicts > 0) {
                throw new RoomTypeBreakfastConflictException(conflicts);
            }
        }

        if (rateChanged) {
            ratePlans.updateDefaults(roomTypeId, breakfastIncluded, request.defaultRateKrw());
        }
    }

    private ExistingRoomType loadRoomType(UUID hotelId, UUID roomTypeId) {
        return jdbc.query("""
                select id, name, max_occupancy from room_type
                 where id = ? and hotel_id = ?
                """, rs -> rs.next() ? new ExistingRoomType(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getInt("max_occupancy")) : null, roomTypeId, hotelId);
    }

    private int countConflictingReservations(UUID roomTypeId, int newOccupancy) {
        // 새 인원을 초과하는 확정 예약이 있으면 값을 내릴 수 없다.
        // 예약 가능 조건은 max_occupancy * rooms >= adults + children이므로
        // (adults + children) / rooms 가 newOccupancy를 초과하면 충돌이다.
        Integer count = jdbc.queryForObject("""
                select count(*) from reservation
                 where room_type_id = ?
                   and status = ?
                   and ceil((adults + children) * 1.0 / rooms) > ?
                """, Integer.class, roomTypeId, ACTIVE_STATUS, newOccupancy);
        return count == null ? 0 : count;
    }

    private UUID findHandledRoomType(UUID hotelId, UUID staffId, String idempotencyKey, String requestHash) {
        // 같은 멱원 키로 이미 처리됐으면 저장된 객실 유형을 그대로 돌려준다.
        UUID byKey = jdbc.query("""
                select room_type_id from room_type_command
                 where hotel_id = ? and staff_id = ? and kind = ? and idempotency_key = ?
                """, rs -> rs.next() ? rs.getObject("room_type_id", UUID.class) : null,
                hotelId, staffId, KIND_UPDATE, idempotencyKey);
        if (byKey != null) return byKey;
        // 멱원 키는 달라도 같은 직원의 같은 내용 재시도면 같은 결과를 돌려준다.
        return jdbc.query("""
                select room_type_id from room_type_command
                 where hotel_id = ? and staff_id = ? and kind = ? and request_hash = ?
                """, rs -> rs.next() ? rs.getObject("room_type_id", UUID.class) : null,
                hotelId, staffId, KIND_UPDATE, requestHash);
    }

    private RoomTypeUpdateResponse loadCurrent(UUID roomTypeId, UUID hotelId, boolean created) {
        return jdbc.query("""
                select id, name, max_occupancy from room_type where id = ? and hotel_id = ?
                """, rs -> rs.next() ? new RoomTypeUpdateResponse(
                        rs.getObject("id", UUID.class), hotelId,
                        rs.getString("name"), rs.getInt("max_occupancy"), created,
                        ratePlanSummary(roomTypeId)) : null,
                roomTypeId, hotelId);
    }

    // 멱원 재호출은 같은 결과를 돌려줘야 한다. 이미 저장된 요금제 상태를 다시 읽는다.
    private RoomTypeUpdateResponse.RatePlanSummary ratePlanSummary(UUID roomTypeId) {
        RoomTypeRatePlanService.RatePlanDefaults defaults = ratePlans.loadDefaults(roomTypeId);
        if (defaults.ratePlanId() == null) {
            return null;
        }
        return new RoomTypeUpdateResponse.RatePlanSummary(
                defaults.ratePlanId(),
                defaults.ratePlanName(),
                Boolean.TRUE.equals(defaults.breakfastIncluded()),
                defaults.minAmountKrw() == null ? 0 : defaults.minAmountKrw(),
                pricedDays(defaults.ratePlanId()));
    }

    private int pricedDays(UUID ratePlanId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from rate_day where rate_plan_id = ?", Integer.class, ratePlanId);
        return count == null ? 0 : count;
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("올바른 Idempotency-Key가 필요합니다.");
        }
    }

    static String requestHash(UUID staffId, UUID hotelId, UUID roomTypeId, RoomTypeUpdateRequest request) {
        String payload = staffId + "|" + hotelId + "|" + roomTypeId + "|"
                + request.name().trim() + "|" + request.maxOccupancy()
                + "|" + request.breakfastIncluded() + "|" + request.defaultRateKrw();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record ExistingRoomType(UUID id, String name, int maxOccupancy) {
    }
}
