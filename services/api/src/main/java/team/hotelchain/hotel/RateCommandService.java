package team.hotelchain.hotel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

/**
 * 본사가 객실 유형의 일자별 요금을 바꾼다.
 * <p>
 * 가격은 특정 날짜의 판매 금액이므로, 이미 확정된 예약의 금액은
 * 건드리지 않는다. 예약은 결제 시점의 금액을 스냅샷으로 가지므로
 * 이후의 가격 변경은 새 예약에만 적용된다.
 * <p>
 * 같은 요청의 중복 변경은 {@code rate_command}의 멱원 키와 요청 지문으로 막는다.
 */
@Service
public class RateCommandService {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 100;

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;
    private final Clock clock;
    private final RoomTypeRatePlanService ratePlans;

    public RateCommandService(JdbcTemplate jdbc, StaffAccessService access, Clock clock,
            RoomTypeRatePlanService ratePlans) {
        this.jdbc = jdbc;
        this.access = access;
        this.clock = clock;
        this.ratePlans = ratePlans;
    }

    @Transactional
    public RateAdjustResponse adjust(String token, UUID hotelId, String idempotencyKey,
            RateAdjustRequest request) {
        StaffPrincipal staff = access.requireHeadquarters(token);
        requireIdempotencyKey(idempotencyKey);
        request.validate();
        if (!hotelExists(hotelId)) {
            throw new HotelNotFoundException(hotelId);
        }
        if (!roomTypeBelongsToHotel(hotelId, request.roomTypeId())) {
            throw new HotelNotFoundException(hotelId);
        }

        // 요금제를 명시하지 않으면 기본 요금제를 쓴다. 둘 이상의 요금제가
        // 있는 유형에서 어느 요금제의 금액을 바꾸는지 지정해야 한다.
        UUID ratePlanId = request.ratePlanId() != null
                ? request.ratePlanId()
                : ratePlans.loadDefaults(request.roomTypeId()).ratePlanId();
        if (ratePlanId == null) {
            // 요금제가 없는 유형은 가격을 바꿀 대상이 없다.
            throw new RoomTypeRatePlanNotFoundException(request.roomTypeId());
        }
        // 명시한 요금제가 해당 유형에 속하지 않으면 다른 유형의 금액을
        // 바꾸는 것이다.
        if (request.ratePlanId() != null && !ratePlanBelongsToRoomType(request.roomTypeId(), ratePlanId)) {
            throw new RatePlanNotFoundException(ratePlanId);
        }

        // 객실 유형 단위로 쓰기를 직렬화해 같은 내용의 동시 재시도가 한 쪽만 저장되게 한다.
        jdbc.query("select id from room_type where id = ? for update", rs -> { }, request.roomTypeId());

        String requestHash = requestHash(staff.id(), hotelId, ratePlanId, request);
        List<RateAdjustRequest.DayRate> adjustments = dedupe(request.adjustments());

        List<RateAdjustResponse.AdjustedRate> existing = findAdjusted(
                hotelId, staff.id(), idempotencyKey, requestHash, adjustments);
        if (!existing.isEmpty()) {
            return new RateAdjustResponse(hotelId, request.roomTypeId(), ratePlanId, existing, false);
        }

        List<RateAdjustResponse.AdjustedRate> adjusted = new ArrayList<>();
        for (RateAdjustRequest.DayRate rate : adjustments) {
            adjusted.add(applyRate(ratePlanId, rate));
        }

        jdbc.update("""
                insert into rate_command
                    (id, hotel_id, staff_id, room_type_id, rate_plan_id, idempotency_key, request_hash, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), hotelId, staff.id(), request.roomTypeId(), ratePlanId,
                idempotencyKey, requestHash, java.sql.Timestamp.from(clock.instant()));

        return new RateAdjustResponse(hotelId, request.roomTypeId(), ratePlanId, adjusted, true);
    }

    private RateAdjustResponse.AdjustedRate applyRate(
            UUID ratePlanId, RateAdjustRequest.DayRate rate) {
        LocalDate stayDate = rate.stayDate();
        int updated = jdbc.update("""
                update rate_day set amount_krw = ?
                 where rate_plan_id = ? and stay_date = ?
                """, rate.amountKrw(), ratePlanId, stayDate);
        if (updated == 0) {
            // 시드 범위를 벗어난 날짜는 요금 행이 없다. 새로 만들지 않고 거부한다.
            throw new RateDayNotFoundException();
        }
        return new RateAdjustResponse.AdjustedRate(stayDate, rate.amountKrw());
    }

    // 같은 날짜가 두 번 들어오면 마지막 값이 이기도록 중복을 합친다.
    private List<RateAdjustRequest.DayRate> dedupe(List<RateAdjustRequest.DayRate> adjustments) {
        Set<LocalDate> seen = new HashSet<>();
        List<RateAdjustRequest.DayRate> unique = new ArrayList<>();
        for (int index = adjustments.size() - 1; index >= 0; index--) {
            RateAdjustRequest.DayRate rate = adjustments.get(index);
            if (seen.add(rate.stayDate())) {
                unique.add(0, rate);
            }
        }
        return unique;
    }

    private List<RateAdjustResponse.AdjustedRate> findAdjusted(
            UUID hotelId, UUID staffId, String idempotencyKey, String requestHash,
            List<RateAdjustRequest.DayRate> adjustments) {
        // 같은 멱원 키로 이미 처리됐으면 저장된 결과를 그대로 돌려준다.
        UUID byKey = jdbc.query("""
                select rate_plan_id from rate_command
                 where hotel_id = ? and staff_id = ? and idempotency_key = ?
                """, rs -> rs.next() ? rs.getObject("rate_plan_id", UUID.class) : null,
                hotelId, staffId, idempotencyKey);
        if (byKey == null) {
            // 멱원 키는 달라도 같은 직원의 같은 내용 재시도면 같은 결과를 돌려준다.
            byKey = jdbc.query("""
                    select rate_plan_id from rate_command
                     where hotel_id = ? and staff_id = ? and request_hash = ?
                    """, rs -> rs.next() ? rs.getObject("rate_plan_id", UUID.class) : null,
                    hotelId, staffId, requestHash);
        }
        if (byKey == null) {
            return List.of();
        }
        final UUID ratePlanId = byKey;
        // 재호출은 요청한 일자만 돌려줘야 멱원이 성립한다.
        String placeholders = String.join(",", java.util.Collections.nCopies(adjustments.size(), "?"));
        return jdbc.query("""
                select stay_date, amount_krw
                  from rate_day
                 where rate_plan_id = ? and stay_date in (%s)
                 order by stay_date
                """.formatted(placeholders),
                new PreparedStatementSetter(ratePlanId, adjustments),
                (rs, rowNumber) -> new RateAdjustResponse.AdjustedRate(
                        rs.getDate("stay_date").toLocalDate(),
                        rs.getInt("amount_krw")));
    }

    /**
     * 재호출이 요청했던 일자만 조회하도록 바인딩한다.
     */
    private record PreparedStatementSetter(UUID ratePlanId, List<RateAdjustRequest.DayRate> rates)
            implements org.springframework.jdbc.core.PreparedStatementSetter {

        @Override
        public void setValues(java.sql.PreparedStatement statement) throws java.sql.SQLException {
            statement.setObject(1, ratePlanId);
            for (int index = 0; index < rates.size(); index++) {
                statement.setObject(index + 2, java.sql.Date.valueOf(rates.get(index).stayDate()));
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

    private boolean ratePlanBelongsToRoomType(UUID roomTypeId, UUID ratePlanId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from rate_plan where id = ? and room_type_id = ?",
                Integer.class, ratePlanId, roomTypeId);
        return count != null && count > 0;
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("올바른 Idempotency-Key가 필요합니다.");
        }
    }

    static String requestHash(UUID staffId, UUID hotelId, UUID ratePlanId, RateAdjustRequest request) {
        StringBuilder payload = new StringBuilder()
                .append(staffId).append('|').append(hotelId).append('|')
                .append(request.roomTypeId()).append('|').append(ratePlanId).append('|');
        for (RateAdjustRequest.DayRate rate : request.adjustments()) {
            payload.append(rate.stayDate()).append(':').append(rate.amountKrw()).append(',');
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
