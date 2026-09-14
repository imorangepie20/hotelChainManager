package team.hotelchain.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import team.hotelchain.reservation.BusinessConflictException;
import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffSessionView;

@SpringBootTest
class CheckedInRoomMoveIntegrationTest {
    private static final UUID HOTEL = UUID.fromString("15000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_HOTEL = UUID.fromString("15000000-0000-0000-0000-000000000002");
    private static final UUID ROOM_TYPE = UUID.fromString("25000000-0000-0000-0000-000000000001");
    private static final UUID WRONG_TYPE = UUID.fromString("25000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_HOTEL_TYPE = UUID.fromString("25000000-0000-0000-0000-000000000003");
    private static final UUID CURRENT_ROOM = UUID.fromString("35000000-0000-0000-0000-000000000001");
    private static final UUID NEW_ROOM = UUID.fromString("35000000-0000-0000-0000-000000000002");
    private static final UUID DIRTY_ROOM = UUID.fromString("35000000-0000-0000-0000-000000000003");
    private static final UUID INSPECTION_ROOM = UUID.fromString("35000000-0000-0000-0000-000000000004");
    private static final UUID OUT_OF_SERVICE_ROOM = UUID.fromString("35000000-0000-0000-0000-000000000005");
    private static final UUID WRONG_TYPE_ROOM = UUID.fromString("35000000-0000-0000-0000-000000000006");
    private static final UUID OTHER_HOTEL_ROOM = UUID.fromString("35000000-0000-0000-0000-000000000007");
    private static final UUID RESERVATION = UUID.fromString("45000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_RESERVATION = UUID.fromString("45000000-0000-0000-0000-000000000002");
    private static final UUID RATE_PLAN = UUID.fromString("55000000-0000-0000-0000-000000000001");
    private static final UUID STAFF = UUID.fromString("65000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_STAFF = UUID.fromString("65000000-0000-0000-0000-000000000002");

    @Autowired JdbcTemplate jdbc;
    @Autowired StaffAccessService staffAccess;
    @Autowired CheckedInRoomMoveService roomMove;
    @Autowired StaffOperationsService operations;
    @Autowired WebApplicationContext context;

    @BeforeEach
    void seed() {
        clean();
        jdbc.update("insert into hotel values (?, '이동 테스트 호텔', '서울', 'Asia/Seoul')", HOTEL);
        jdbc.update("insert into hotel values (?, '다른 호텔', '부산', 'Asia/Seoul')", OTHER_HOTEL);
        jdbc.update("insert into room_type values (?, ?, '스탠다드', 2)", ROOM_TYPE, HOTEL);
        jdbc.update("insert into room_type values (?, ?, '스위트', 4)", WRONG_TYPE, HOTEL);
        jdbc.update("insert into room_type values (?, ?, '타 지점 객실', 2)", OTHER_HOTEL_TYPE, OTHER_HOTEL);
        insertRoom(CURRENT_ROOM, HOTEL, ROOM_TYPE, "101", "CLEAN", "AVAILABLE", null);
        insertRoom(NEW_ROOM, HOTEL, ROOM_TYPE, "102", "CLEAN", "AVAILABLE", null);
        insertRoom(DIRTY_ROOM, HOTEL, ROOM_TYPE, "103", "NEEDS_CLEANING", "AVAILABLE", null);
        insertRoom(INSPECTION_ROOM, HOTEL, ROOM_TYPE, "104", "CLEAN", "INSPECTION_REQUIRED", "시설 점검");
        insertRoom(OUT_OF_SERVICE_ROOM, HOTEL, ROOM_TYPE, "105", "CLEAN", "OUT_OF_SERVICE", "장기 수리");
        insertRoom(WRONG_TYPE_ROOM, HOTEL, WRONG_TYPE, "201", "CLEAN", "AVAILABLE", null);
        insertRoom(OTHER_HOTEL_ROOM, OTHER_HOTEL, OTHER_HOTEL_TYPE, "301", "CLEAN", "AVAILABLE", null);
        jdbc.update("insert into rate_plan values (?, ?, '테스트 요금', false, 'TEST')", RATE_PLAN, ROOM_TYPE);
        insertReservation(RESERVATION, "CHECKED_IN", "투숙 고객", 'a');
        jdbc.update("insert into reservation_room_assignment (reservation_id, physical_room_id) values (?, ?)",
                RESERVATION, CURRENT_ROOM);
        String password = new BCryptPasswordEncoder().encode("password");
        jdbc.update("""
                insert into staff_member (id, email, display_name, password_hash, role, hotel_id)
                values (?, 'checked-in-move@example.com', '이동 직원', ?, 'BRANCH_STAFF', ?),
                       (?, 'checked-in-move-second@example.com', '다른 이동 직원', ?, 'BRANCH_STAFF', ?)
                """, STAFF, password, HOTEL, SECOND_STAFF, password, HOTEL);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void listsOnlyCleanAvailableSameTypeCandidates() {
        insertReservation(OTHER_RESERVATION, "CHECKED_IN", "다른 고객", 'b');
        jdbc.update("insert into reservation_room_assignment (reservation_id, physical_room_id) values (?, ?)",
                OTHER_RESERVATION, NEW_ROOM);
        StaffSessionView session = login("checked-in-move@example.com");

        CheckedInRoomMoveOptions options = roomMove.options(session.token(), RESERVATION);

        assertThat(options.assignments()).singleElement().extracting(AssignableRoom::id).isEqualTo(CURRENT_ROOM);
        assertThat(options.candidates()).isEmpty();
        jdbc.update("delete from reservation_room_assignment where reservation_id = ?", OTHER_RESERVATION);
        assertThat(roomMove.options(session.token(), RESERVATION).candidates())
                .singleElement().extracting(AssignableRoom::id).isEqualTo(NEW_ROOM);
    }

    @Test
    void movesOneCheckedInRoomAndMarksThePreviousRoomDirtyAndInspectionRequired() {
        StaffSessionView session = login("checked-in-move@example.com");

        CheckedInRoomMoveResult result = roomMove.move(session.token(), RESERVATION, "move-1",
                new CheckedInRoomMoveRequest(CURRENT_ROOM, NEW_ROOM, "에어컨 고장"));

        assertThat(result.previousPhysicalRoomId()).isEqualTo(CURRENT_ROOM);
        assertThat(result.physicalRoomId()).isEqualTo(NEW_ROOM);
        assertThat(result.reason()).isEqualTo("에어컨 고장");
        assertThat(jdbc.queryForObject("select physical_room_id from reservation_room_assignment where reservation_id = ?",
                UUID.class, RESERVATION)).isEqualTo(NEW_ROOM);
        assertThat(jdbc.queryForMap("select housekeeping_status, operational_status from physical_room where id = ?", CURRENT_ROOM))
                .containsEntry("housekeeping_status", "NEEDS_CLEANING")
                .containsEntry("operational_status", "INSPECTION_REQUIRED");
        assertThat(jdbc.queryForObject("select count(*) from checked_in_room_move where reservation_id = ?",
                Integer.class, RESERVATION)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from physical_room_operational_event where physical_room_id = ?",
                Integer.class, CURRENT_ROOM)).isEqualTo(1);
    }

    @Test
    void replaysTheSameMoveAndRejectsChangedRoomReasonOrActor() {
        StaffSessionView firstStaff = login("checked-in-move@example.com");
        StaffSessionView secondStaff = login("checked-in-move-second@example.com");
        var request = new CheckedInRoomMoveRequest(CURRENT_ROOM, NEW_ROOM, "소음 문제");
        CheckedInRoomMoveResult first = roomMove.move(firstStaff.token(), RESERVATION, "move-retry", request);

        assertThat(roomMove.move(firstStaff.token(), RESERVATION, "move-retry", request)).isEqualTo(first);
        assertConflict("IDEMPOTENCY_CONFLICT", () -> roomMove.move(firstStaff.token(), RESERVATION, "move-retry",
                new CheckedInRoomMoveRequest(CURRENT_ROOM, NEW_ROOM, "다른 사유")));
        assertConflict("IDEMPOTENCY_CONFLICT", () -> roomMove.move(secondStaff.token(), RESERVATION, "move-retry", request));
        assertThat(jdbc.queryForObject("select count(*) from checked_in_room_move where reservation_id = ?",
                Integer.class, RESERVATION)).isEqualTo(1);
    }

    @Test
    void rejectsWrongStatusTypeDirtyUnavailableOrOccupiedTargetsWithoutChangingAssignment() {
        StaffSessionView session = login("checked-in-move@example.com");
        jdbc.update("update reservation set status = 'CONFIRMED' where id = ?", RESERVATION);
        assertConflict("RESERVATION_NOT_CHECKED_IN", () -> move(session, "confirmed", NEW_ROOM));
        jdbc.update("update reservation set status = 'CHECKED_IN' where id = ?", RESERVATION);
        assertConflict("ROOM_NOT_MATCHED", () -> move(session, "wrong-type", WRONG_TYPE_ROOM));
        assertConflict("ROOM_NOT_CLEAN", () -> move(session, "dirty", DIRTY_ROOM));
        assertConflict("ROOM_NOT_OPERATIONALLY_AVAILABLE", () -> move(session, "inspection", INSPECTION_ROOM));
        assertConflict("ROOM_NOT_OPERATIONALLY_AVAILABLE", () -> move(session, "stopped", OUT_OF_SERVICE_ROOM));

        insertReservation(OTHER_RESERVATION, "CHECKED_IN", "다른 고객", 'b');
        jdbc.update("insert into reservation_room_assignment (reservation_id, physical_room_id) values (?, ?)",
                OTHER_RESERVATION, NEW_ROOM);
        assertConflict("ROOM_ALREADY_ASSIGNED", () -> move(session, "occupied", NEW_ROOM));
        assertThat(jdbc.queryForObject("select physical_room_id from reservation_room_assignment where reservation_id = ?",
                UUID.class, RESERVATION)).isEqualTo(CURRENT_ROOM);
        assertThat(jdbc.queryForObject("select count(*) from checked_in_room_move where reservation_id = ?",
                Integer.class, RESERVATION)).isZero();
    }

    @Test
    void rejectsATargetAlreadyAssignedToAnotherRoomOfTheSameReservation() {
        StaffSessionView session = login("checked-in-move@example.com");
        jdbc.update("insert into reservation_room_assignment (reservation_id, physical_room_id) values (?, ?)",
                RESERVATION, NEW_ROOM);

        assertConflict("ROOM_ALREADY_ASSIGNED", () -> move(session, "own-target", NEW_ROOM));

        assertThat(jdbc.queryForObject(
                "select count(*) from reservation_room_assignment where reservation_id = ?",
                Integer.class, RESERVATION)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from checked_in_room_move where reservation_id = ?",
                Integer.class, RESERVATION)).isZero();
    }

    @Test
    void rollsBackAssignmentRoomStateAndAuditsWhenOperationalEventInsertFails() {
        StaffSessionView session = login("checked-in-move@example.com");
        String eventKey = "checked-in-move:" + RESERVATION + ":rollback";
        jdbc.update("""
                insert into physical_room_operational_event
                    (id, physical_room_id, previous_status, status, reason, staff_id, idempotency_key, request_hash)
                values (?, ?, 'AVAILABLE', 'INSPECTION_REQUIRED', '실패 주입', ?, ?, repeat('c', 64))
                """, UUID.randomUUID(), CURRENT_ROOM, STAFF, eventKey);

        assertThatThrownBy(() -> move(session, "rollback", NEW_ROOM))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select physical_room_id from reservation_room_assignment where reservation_id = ?",
                UUID.class, RESERVATION)).isEqualTo(CURRENT_ROOM);
        assertThat(jdbc.queryForMap("select housekeeping_status, operational_status from physical_room where id = ?", CURRENT_ROOM))
                .containsEntry("housekeeping_status", "CLEAN")
                .containsEntry("operational_status", "AVAILABLE");
        assertThat(jdbc.queryForObject("select count(*) from checked_in_room_move where reservation_id = ?",
                Integer.class, RESERVATION)).isZero();
    }

    @Test
    void rejectsAStaleCurrentAssignmentAndChecksOutOnlyTheMovedRoom() {
        StaffSessionView session = login("checked-in-move@example.com");
        roomMove.move(session.token(), RESERVATION, "first-move",
                new CheckedInRoomMoveRequest(CURRENT_ROOM, NEW_ROOM, "객실 설비 문제"));

        assertConflict("ROOM_ASSIGNMENT_NOT_FOUND", () -> roomMove.move(session.token(), RESERVATION, "stale-current",
                new CheckedInRoomMoveRequest(CURRENT_ROOM, DIRTY_ROOM, "다시 이동")));
        operations.checkOut(session.token(), RESERVATION);

        assertThat(jdbc.queryForObject("select housekeeping_status from physical_room where id = ?", String.class, NEW_ROOM))
                .isEqualTo("NEEDS_CLEANING");
        assertThat(jdbc.queryForMap("select housekeeping_status, operational_status from physical_room where id = ?", CURRENT_ROOM))
                .containsEntry("housekeeping_status", "NEEDS_CLEANING")
                .containsEntry("operational_status", "INSPECTION_REQUIRED");
    }

    @Test
    void allowsOnlyOneOfTwoCheckedInReservationsToClaimTheSameRoom() throws Exception {
        insertReservation(OTHER_RESERVATION, "CHECKED_IN", "다른 고객", 'b');
        jdbc.update("insert into reservation_room_assignment (reservation_id, physical_room_id) values (?, ?)",
                OTHER_RESERVATION, DIRTY_ROOM);
        StaffSessionView session = login("checked-in-move@example.com");
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> concurrentMove(
                    start, session.token(), RESERVATION, CURRENT_ROOM, "concurrent-first"));
            Future<String> second = executor.submit(() -> concurrentMove(
                    start, session.token(), OTHER_RESERVATION, DIRTY_ROOM, "concurrent-second"));
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder("SUCCESS", "ROOM_ALREADY_ASSIGNED");
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from reservation_room_assignment where physical_room_id = ?",
                Integer.class, NEW_ROOM)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from checked_in_room_move where reservation_id in (?, ?)",
                Integer.class, RESERVATION, OTHER_RESERVATION)).isEqualTo(1);
    }

    @Test
    void exposesMoveOptionsAndMoveOverHttp() throws Exception {
        StaffSessionView session = login("checked-in-move@example.com");
        var mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        mockMvc.perform(get("/api/staff/reservations/{reservationId}/checked-in-room-move-options", RESERVATION)
                        .header("X-Staff-Session", session.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments[0].roomNumber").value("101"))
                .andExpect(jsonPath("$.candidates[0].roomNumber").value("102"));

        mockMvc.perform(post("/api/staff/reservations/{reservationId}/checked-in-room-moves", RESERVATION)
                        .header("X-Staff-Session", session.token())
                        .header("Idempotency-Key", "http-move")
                        .contentType("application/json")
                        .content("""
                                {"currentPhysicalRoomId":"%s","newPhysicalRoomId":"%s","reason":"냉방 문제"}
                                """.formatted(CURRENT_ROOM, NEW_ROOM)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousRoomNumber").value("101"))
                .andExpect(jsonPath("$.roomNumber").value("102"))
                .andExpect(jsonPath("$.reason").value("냉방 문제"));
    }

    private CheckedInRoomMoveResult move(StaffSessionView session, String key, UUID target) {
        return roomMove.move(session.token(), RESERVATION, key,
                new CheckedInRoomMoveRequest(CURRENT_ROOM, target, "객실 문제"));
    }

    private String concurrentMove(CountDownLatch start, String token, UUID reservationId,
            UUID currentRoomId, String key) throws InterruptedException {
        start.await();
        try {
            roomMove.move(token, reservationId, key,
                    new CheckedInRoomMoveRequest(currentRoomId, NEW_ROOM, "동시 이동"));
            return "SUCCESS";
        } catch (BusinessConflictException exception) {
            return exception.code();
        }
    }

    private StaffSessionView login(String email) {
        return staffAccess.login(email, "password");
    }

    private void insertRoom(UUID id, UUID hotelId, UUID roomTypeId, String number,
            String housekeeping, String operational, String reason) {
        jdbc.update("""
                insert into physical_room
                    (id, hotel_id, room_type_id, room_number, housekeeping_status, operational_status, operational_reason)
                values (?, ?, ?, ?, ?, ?, ?)
                """, id, hotelId, roomTypeId, number, housekeeping, operational, reason);
    }

    private void insertReservation(UUID id, String status, String guestName, char hashChar) {
        jdbc.update("""
                insert into reservation
                    (id, room_type_id, rate_plan_id, check_in, check_out, adults, children, rooms, status,
                     total_krw, currency, expires_at, guest_name, guest_email, management_token_hash, policy_snapshot)
                values (?, ?, ?, ?, ?, 2, 0, 1, ?, 100000, 'KRW', now(), ?, 'guest@example.com', repeat(?, 64), '{}'::jsonb)
                """, id, ROOM_TYPE, RATE_PLAN, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3),
                status, guestName, String.valueOf(hashChar));
    }

    private void assertConflict(String code, org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessConflictException.class)
                .extracting(error -> ((BusinessConflictException) error).code())
                .isEqualTo(code);
    }

    private void clean() {
        jdbc.update("delete from staff_session where staff_id in (?, ?)", STAFF, SECOND_STAFF);
        jdbc.update("delete from checked_in_room_move where reservation_id in (?, ?)", RESERVATION, OTHER_RESERVATION);
        jdbc.update("delete from physical_room_operational_event where physical_room_id in (?, ?, ?, ?, ?, ?, ?)",
                CURRENT_ROOM, NEW_ROOM, DIRTY_ROOM, INSPECTION_ROOM, OUT_OF_SERVICE_ROOM, WRONG_TYPE_ROOM, OTHER_HOTEL_ROOM);
        jdbc.update("delete from reservation_room_assignment where reservation_id in (?, ?)", RESERVATION, OTHER_RESERVATION);
        jdbc.update("delete from reservation where id in (?, ?)", RESERVATION, OTHER_RESERVATION);
        jdbc.update("delete from staff_member where id in (?, ?)", STAFF, SECOND_STAFF);
        jdbc.update("delete from physical_room where id in (?, ?, ?, ?, ?, ?, ?)",
                CURRENT_ROOM, NEW_ROOM, DIRTY_ROOM, INSPECTION_ROOM, OUT_OF_SERVICE_ROOM, WRONG_TYPE_ROOM, OTHER_HOTEL_ROOM);
        jdbc.update("delete from rate_plan where id = ?", RATE_PLAN);
        jdbc.update("delete from room_type where id in (?, ?, ?)", ROOM_TYPE, WRONG_TYPE, OTHER_HOTEL_TYPE);
        jdbc.update("delete from hotel where id in (?, ?)", HOTEL, OTHER_HOTEL);
    }
}
