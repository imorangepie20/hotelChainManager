package team.hotelchain.inventory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.hotel.HotelNotFoundException;
import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

/**
 * 본사가 객실 유형의 날짜 구간 판매 상태를 바꾸고 읽는다.
 * <p>
 * 총량({@code inventory_day.capacity})과 별개로 동작한다. {@code STOPPED}는
 * 해당 구간의 <strong>신규 판매만 막고</strong> 이미 확정·보류된 예약은
 * 그대로 둔다. 지점 전체 판매 중지({@code PATCH .../active})와 같은
 * 원칙이다. 취소는 전용 API가 있고, 중지가 예약을 취소하면 본사가 고객에게
 * 알리지 않은 채 환불 의무가 생기기 때문이다.
 * <p>
 * 총량을 0으로 내리는 것과의 차이는 복구다. 중지는 {@code capacity}를
 * 건드리지 않으므로 재개하면 중지 전과 같은 재고가 돌아온다.
 * <p>
 * 같은 요청의 중복 처리는 {@code room_type_sales_status_command}의 멱원 키와
 * 요청 지문으로 막는다. 모든 동작은 호출자의 트랜잭션 안에서 일어난다.
 */
@Service
public class SalesStatusCommandService {

    static final String STATUS_OPEN = "OPEN";
    static final String STATUS_STOPPED = "STOPPED";

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 100;

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;
    private final Clock clock;

    public SalesStatusCommandService(JdbcTemplate jdbc, StaffAccessService access, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.clock = clock;
    }

    /**
     * 객실 유형의 날짜 구간 판매 상태를 바꾼다.
     * <p>
     * 총량은 건드리지 않는다. {@code OPEN}으로 바꾸면 중지했던 구간의
     * 재고가 다시 판매된다. 실제로 바꾸는 값이 없으면 DB를 건드리지 않고
     * 멱원 기록만 남긴다.
     */
    @Transactional
    public SalesStatusResponse set(String token, UUID hotelId, UUID roomTypeId,
            String idempotencyKey, SalesStatusRequest request) {
        StaffPrincipal staff = access.requireHeadquarters(token);
        requireIdempotencyKey(idempotencyKey);
        request.validate();
        requireRoomType(hotelId, roomTypeId);

        // 객실 유형 단위로 쓰기를 직렬화해 같은 내용의 동시 재시도가
        // 한 쪽만 저장되게 한다.
        jdbc.query("select id from room_type where id = ? for update", rs -> { }, roomTypeId);

        String requestHash = requestHash(staff.id(), hotelId, roomTypeId, request);
        SalesStatusResponse existing = findExisting(hotelId, staff.id(), idempotencyKey, requestHash);
        if (existing != null) {
            return existing;
        }

        List<LocalDate> dates = datesInRange(request.fromDate(), request.toDate());
        applyStatus(roomTypeId, dates, request.status());

        try {
            jdbc.update("""
                    insert into room_type_sales_status_command
                        (id, hotel_id, staff_id, room_type_id, from_date, to_date,
                         status, idempotency_key, request_hash, created_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), hotelId, staff.id(), roomTypeId,
                    request.fromDate(), request.toDate(), request.status(),
                    idempotencyKey, requestHash, java.sql.Timestamp.from(clock.instant()));
        } catch (DuplicateKeyException exception) {
            // 같은 멱원 키를 동시에 두 번 보내면 한 쪽이 유일 인덱스에
            // 부딪힌다. 먼저 들어간 쪽의 결과를 돌려준다.
            SalesStatusResponse stored = findExisting(hotelId, staff.id(), idempotencyKey, requestHash);
            if (stored != null) {
                return stored;
            }
            // 같은 내용의 재시도가 동시에 들어왔으면 request_hash로 같은
            // 결과를 찾는다. 찾지 못하면 충돌로 알린다.
            throw new SalesStatusConflictException("판매 상태 변경이 동시에 처리돼서 다시 시도해 주세요.");
        }

        return new SalesStatusResponse(hotelId, roomTypeId, request.fromDate(),
                request.toDate(), request.status(), stoppedDayCount(roomTypeId), true);
    }

    /**
     * 객실 유형의 판매 중지 구간을 읽는다. SELECT만 사용한다.
     * <p>
     * 일자 단위로 저장한 상태를 연속된 구간으로 합쳐서 돌려준다. 본사가
     * 여러 번 중지·재개하면 구간이 여러 개 생긴다.
     */
    public SalesStatusView list(String token, UUID hotelId, UUID roomTypeId) {
        access.requireHotel(token, hotelId);
        requireRoomType(hotelId, roomTypeId);

        List<LocalDate> stoppedDates = jdbc.query("""
                select stay_date
                  from room_type_sales_status
                 where room_type_id = ? and status = ?
                 order by stay_date
                """, (rs, rowNumber) -> rs.getDate("stay_date").toLocalDate(),
                roomTypeId, STATUS_STOPPED);
        return new SalesStatusView(hotelId, roomTypeId, mergeRanges(stoppedDates));
    }

    // 연속된 일자를 구간으로 합친다. 중지 일자가 떨어져 있으면
    // 별도 구간이 된다.
    private List<SalesStatusView.StoppedRange> mergeRanges(List<LocalDate> dates) {
        List<SalesStatusView.StoppedRange> ranges = new java.util.ArrayList<>();
        LocalDate rangeStart = null;
        LocalDate previous = null;
        for (LocalDate date : dates) {
            if (rangeStart == null) {
                rangeStart = date;
            } else if (previous == null || !date.equals(previous.plusDays(1))) {
                ranges.add(new SalesStatusView.StoppedRange(rangeStart, previous));
                rangeStart = date;
            }
            previous = date;
        }
        if (rangeStart != null) {
            ranges.add(new SalesStatusView.StoppedRange(rangeStart, previous));
        }
        return ranges;
    }

    // 구간을 일자 단위로 쪼갠다. 판매 상태는 일자 단위로 저장하므로
    // 구간 요청을 각 날짜의 상태로 바꿔서 적용한다.
    private List<LocalDate> datesInRange(LocalDate fromDate, LocalDate toDate) {
        List<LocalDate> dates = new java.util.ArrayList<>();
        for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
            dates.add(date);
        }
        return dates;
    }

    // 이미 같은 상태인 일자는 덮어쓰지 않는다. 매번 같은 결과를 내도록
    // 상태를 확정적으로 만들면 멱원 재호출이 같은 결과를 갖게 한다.
    private void applyStatus(UUID roomTypeId, List<LocalDate> dates, String status) {
        for (LocalDate date : dates) {
            jdbc.update("""
                    insert into room_type_sales_status (room_type_id, stay_date, status)
                    values (?, ?, ?)
                    on conflict (room_type_id, stay_date)
                    do update set status = excluded.status
                    """, roomTypeId, date, status);
        }
    }

    private SalesStatusResponse findExisting(UUID hotelId, UUID staffId,
            String idempotencyKey, String requestHash) {
        // 같은 멱원 키로 이미 처리됐으면 저장된 구간을 그대로 돌려준다.
        SalesStatusResponse byKey = findCommand(hotelId, staffId, idempotencyKey);
        if (byKey != null) {
            return byKey;
        }
        // 멱원 키는 달라도 같은 직원의 같은 내용 재시도면 같은 결과를 돌려준다.
        return findCommand(hotelId, staffId, requestHash);
    }

    // 멱원 재호출은 본사가 보낸 값이 아니라 저장된 상태를 돌려줘야
    // 같은 결과가 성립한다. stoppedDays는 현재 중지된 일자 수다.
    private SalesStatusResponse findCommand(UUID hotelId, UUID staffId, String key) {
        return jdbc.query("""
                select room_type_id, from_date, to_date, status
                  from room_type_sales_status_command
                 where hotel_id = ? and staff_id = ? and (idempotency_key = ? or request_hash = ?)
                 order by created_at desc, id desc
                 limit 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            return new SalesStatusResponse(hotelId, rs.getObject("room_type_id", UUID.class),
                    rs.getDate("from_date").toLocalDate(),
                    rs.getDate("to_date").toLocalDate(),
                    rs.getString("status"),
                    stoppedDayCount(rs.getObject("room_type_id", UUID.class)),
                    false);
        }, hotelId, staffId, key, key);
    }

    private int stoppedDayCount(UUID roomTypeId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from room_type_sales_status where room_type_id = ? and status = ?",
                Integer.class, roomTypeId, STATUS_STOPPED);
        return count == null ? 0 : count;
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

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("올바른 Idempotency-Key가 필요합니다.");
        }
    }

    static String requestHash(UUID staffId, UUID hotelId, UUID roomTypeId, SalesStatusRequest request) {
        String payload = staffId + "|" + hotelId + "|" + roomTypeId + "|" + request.fromDate()
                + "|" + request.toDate() + "|" + request.status();
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
