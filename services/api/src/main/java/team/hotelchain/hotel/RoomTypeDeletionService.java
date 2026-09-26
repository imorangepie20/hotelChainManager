package team.hotelchain.hotel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

/**
 * 본사가 객실 유형을 지운다.
 * <p>
 * 객실 유형은 가격·재고·예약 가능 여부에 닿아 있어서 진행 중인 판매·예약·배정이
 * 하나라도 있으면 409로 거부하고 아무것도 지우지 않는다. 이 정책은
 * {@link RoomTypeUpdateService}가 최대 인원·조식 포함 여부를 바꿀 때 쓰는 것과
 * 같은 기준이다.
 * <p>
 * 종료된 예약·예약 변경 요청·감사 이력은 과거 운영 기록이므로 **삭제 후에도 그대로** 둔다.
 * 그래서 유형을 바로 지우지 못하고 조건을 둔다.
 * <p>
 * 같은 요청의 중복 삭제는 {@code room_type_command}의 멱원 키와 요청 지문으로 막는다.
 */
@Service
public class RoomTypeDeletionService {

    static final String KIND_DELETE = "DELETE";
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 100;

    private static final List<String> ACTIVE_CHANGE_STATUSES = List.of(
            "PENDING_APPROVAL", "APPROVED", "AWAITING_PAYMENT", "REFUND_PENDING",
            "READY_TO_APPLY", "APPLYING", "RECONCILIATION_REQUIRED");

    private static final List<String> CLOSED_CHANGE_STATUSES = List.of(
            "COMPLETED", "REJECTED", "CANCELLED", "EXPIRED");

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;
    private final Clock clock;

    public RoomTypeDeletionService(JdbcTemplate jdbc, StaffAccessService access, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public RoomTypeDeletionResponse delete(String token, UUID hotelId, UUID roomTypeId, String idempotencyKey) {
        StaffPrincipal staff = access.requireHeadquarters(token);
        requireIdempotencyKey(idempotencyKey);

        String requestHash = requestHash(staff.id(), hotelId, roomTypeId);
        // 멱원 재호출을 유형 조회 앞에 둔다. 유형이 이미 지워졌으면 loadRoomType가
        // null을 돌려줘서 재호출이 404로 착각하게 된다.
        if (alreadyHandled(hotelId, staff.id(), idempotencyKey, requestHash)) {
            return replayResponse(hotelId, roomTypeId, staff.id(), idempotencyKey, requestHash);
        }

        // 다른 지점의 객실 유형을 실수로 지우지 않도록 hotel_id 쌍으로 찾는다.
        ExistingRoomType existing = loadRoomType(hotelId, roomTypeId);
        if (existing == null) {
            throw new HotelNotFoundException(hotelId);
        }

        // 동시 삭제·수정을 직렬화한다. 두 본사 관리자가 같은 유형을 동시에 다루면
        // 한 쪽의 잠금이 풀릴 때까지 다른 쪽이 기다린다.
        jdbc.query("select id from room_type where id = ? and hotel_id = ? for update", rs -> { },
                roomTypeId, hotelId);

        if (alreadyHandled(hotelId, staff.id(), idempotencyKey, requestHash)) {
            // 멱원 재호출은 삭제 뒤 남은 유형 수를 다시 읽어서 같은 결과를 돌려준다.
            return replayResponse(hotelId, roomTypeId, staff.id(), idempotencyKey, requestHash);
        }

        // 지우다 말 수 없으므로 모든 충돌 검사를 삭제 앞에 둔다.
        Conflicts conflicts = countConflicts(roomTypeId);
        if (conflicts.hasAny()) {
            throw new RoomTypeDeletionConflictException(
                    conflicts.confirmedReservations(), conflicts.openChangeRequests(),
                    conflicts.heldInventoryDays(), conflicts.physicalRooms());
        }

        deleteRoomType(roomTypeId, existing.name(), hotelId, staff.id(), idempotencyKey, requestHash);

        return new RoomTypeDeletionResponse(hotelId, roomTypeId, existing.name(), true, remainingRoomTypes(hotelId));
    }

    // 멱원 재호출은 같은 결과를 돌려줘야 한다. 유형 행은 이미 없으므로
    // 멱원 기록에서 이름을 읽고 남은 유형 수는 지점에서 읽는다.
    private RoomTypeDeletionResponse replayResponse(UUID hotelId, UUID roomTypeId, UUID staffId,
                                                   String idempotencyKey, String requestHash) {
        String deletedName = jdbc.query("""
                select deleted_room_type_name from room_type_command
                 where hotel_id = ? and staff_id = ? and kind = ?
                   and (idempotency_key = ? or request_hash = ?)
                """, rs -> rs.next() ? rs.getString("deleted_room_type_name") : null,
                hotelId, staffId, KIND_DELETE, idempotencyKey, requestHash);
        return new RoomTypeDeletionResponse(hotelId, roomTypeId,
                deletedName == null ? "" : deletedName, false, remainingRoomTypes(hotelId));
    }

    // 재고는 유형 없이 의미가 없다. 유형이 사라지면 고객 검색도 예약도
    // 불가능하므로 inventory_day는 지운다. website_page_room_type 연결도 끊는다.
    // 페이지 자체는 두고 대상만 없앤다.
    // **요금제·예약은 남기고 room_type_id 연결만 끊는다.**
    // 종료된 예약은 room_type_id·rate_plan_id를 NOT NULL로 참조하므로 지우면
    // 과거 운영 기록이 깨진다. 남은 요금제·예약은 고객 검색에 나타나지 않는다.
    // AvailabilityService·InventoryQueryService가 room_type을 거쳐서
    // join하므로 유형이 없으면 함께 빠진다.
    private void deleteRoomType(UUID roomTypeId, String roomTypeName, UUID hotelId, UUID staffId,
                               String idempotencyKey, String requestHash) {
        jdbc.update("delete from website_page_room_type where room_type_id = ?", roomTypeId);
        jdbc.update("delete from inventory_day where room_type_id = ?", roomTypeId);
        jdbc.update("update rate_plan set room_type_id = null where room_type_id = ?", roomTypeId);
        // 종료된 예약이 유형을 가리키고 있으면 참조를 끊는다. 행은 보존한다.
        jdbc.update("update reservation set room_type_id = null where room_type_id = ?", roomTypeId);
        jdbc.update("delete from room_type where id = ?", roomTypeId);

        // 멱원 행은 유형 행을 지운 뒤에 만들어진다. room_type_command가
        // room_type을 참조하므로 삭제 기록은 room_type_id를 비우고
        // deleted_room_type_name으로 무엇을 지웠는지 보관한다. 트랜잭션이
        // 묶여 있어 멱원 insert가 실패하면 삭제도 함께 롤백된다.
        jdbc.update("""
                insert into room_type_command
                    (id, hotel_id, staff_id, room_type_id, kind, idempotency_key, request_hash,
                     deleted_room_type_name, created_at)
                values (?, ?, ?, null, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), hotelId, staffId, KIND_DELETE,
                idempotencyKey, requestHash, roomTypeName, java.sql.Timestamp.from(clock.instant()));
    }

    private Conflicts countConflicts(UUID roomTypeId) {
        Integer confirmed = jdbc.queryForObject("""
                select count(*) from reservation
                 where room_type_id = ? and status = 'CONFIRMED'
                """, Integer.class, roomTypeId);
        Integer openChangeRequests = jdbc.queryForObject("""
                select count(*) from reservation_change_request
                 where (previous_room_type_id = ? or target_room_type_id = ?)
                   and status not in (%s)
                """.formatted(placeholders(CLOSED_CHANGE_STATUSES.size())), Integer.class,
                withArguments(roomTypeId, roomTypeId, CLOSED_CHANGE_STATUSES));
        Integer held = jdbc.queryForObject(
                "select count(*) from inventory_day where room_type_id = ? and held > 0",
                Integer.class, roomTypeId);
        Integer physicalRooms = jdbc.queryForObject(
                "select count(*) from physical_room where room_type_id = ?",
                Integer.class, roomTypeId);
        return new Conflicts(
                confirmed == null ? 0 : confirmed,
                openChangeRequests == null ? 0 : openChangeRequests,
                held == null ? 0 : held,
                physicalRooms == null ? 0 : physicalRooms);
    }

    private boolean alreadyHandled(UUID hotelId, UUID staffId, String idempotencyKey, String requestHash) {
        // 같은 멱원 키로 이미 처리됐으면 200으로 같은 결과를 돌려준다.
        Integer byKey = jdbc.queryForObject("""
                select count(*) from room_type_command
                 where hotel_id = ? and staff_id = ? and kind = ? and idempotency_key = ?
                """, Integer.class, hotelId, staffId, KIND_DELETE, idempotencyKey);
        if (byKey != null && byKey > 0) {
            return true;
        }
        // 멱원 키는 달라도 같은 직원의 같은 내용 재시도면 같은 결과를 돌려준다.
        Integer byHash = jdbc.queryForObject("""
                select count(*) from room_type_command
                 where hotel_id = ? and staff_id = ? and kind = ? and request_hash = ?
                """, Integer.class, hotelId, staffId, KIND_DELETE, requestHash);
        return byHash != null && byHash > 0;
    }

    private int remainingRoomTypes(UUID hotelId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from room_type where hotel_id = ?", Integer.class, hotelId);
        return count == null ? 0 : count;
    }

    private ExistingRoomType loadRoomType(UUID hotelId, UUID roomTypeId) {
        return jdbc.query("select id, name from room_type where id = ? and hotel_id = ?",
                rs -> rs.next() ? new ExistingRoomType(
                        rs.getObject("id", UUID.class), rs.getString("name")) : null,
                roomTypeId, hotelId);
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("올바른 Idempotency-Key가 필요합니다.");
        }
    }

    private static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    // JDBC가 IN 절에 List를 바인딩하지 못하므로 전개한다.
    private static Object[] withArguments(UUID roomTypeId, UUID otherRoomTypeId, List<String> statuses) {
        Object[] arguments = new Object[2 + statuses.size()];
        arguments[0] = roomTypeId;
        arguments[1] = otherRoomTypeId;
        for (int index = 0; index < statuses.size(); index++) {
            arguments[index + 2] = statuses.get(index);
        }
        return arguments;
    }

    static String requestHash(UUID staffId, UUID hotelId, UUID roomTypeId) {
        String payload = staffId + "|" + hotelId + "|" + roomTypeId;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record ExistingRoomType(UUID id, String name) {
    }

    private record Conflicts(int confirmedReservations, int openChangeRequests,
                             int heldInventoryDays, int physicalRooms) {

        boolean hasAny() {
            return confirmedReservations > 0 || openChangeRequests > 0
                    || heldInventoryDays > 0 || physicalRooms > 0;
        }
    }
}
