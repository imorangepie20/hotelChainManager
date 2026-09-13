package team.hotelchain.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import team.hotelchain.reservation.BusinessConflictException;
import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffAccessDeniedException;
import team.hotelchain.staff.StaffSessionView;

@SpringBootTest
class StaffRoomReassignmentIntegrationTest {
    private static final UUID HOTEL = UUID.fromString("13000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_HOTEL = UUID.fromString("13000000-0000-0000-0000-000000000002");
    private static final UUID ROOM_TYPE = UUID.fromString("23000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ROOM_TYPE = UUID.fromString("23000000-0000-0000-0000-000000000002");
    private static final UUID CURRENT_ROOM = UUID.fromString("33000000-0000-0000-0000-000000000001");
    private static final UUID NEW_ROOM = UUID.fromString("33000000-0000-0000-0000-000000000002");
    private static final UUID THIRD_ROOM = UUID.fromString("33000000-0000-0000-0000-000000000003");
    private static final UUID WRONG_TYPE_ROOM = UUID.fromString("33000000-0000-0000-0000-000000000004");
    private static final UUID RESERVATION = UUID.fromString("43000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_RESERVATION = UUID.fromString("43000000-0000-0000-0000-000000000002");
    private static final UUID RATE_PLAN = UUID.fromString("53000000-0000-0000-0000-000000000001");

    @Autowired JdbcTemplate jdbc;
    @Autowired StaffAccessService staffAccess;
    @Autowired StaffRoomReassignmentService reassignment;
    @Autowired WebApplicationContext context;

    @BeforeEach
    void seed() {
        clean();
        jdbc.update("insert into hotel values (?, ?, ?, ?)", HOTEL, "재배정 테스트 호텔", "서울", "Asia/Seoul");
        jdbc.update("insert into hotel values (?, ?, ?, ?)", OTHER_HOTEL, "다른 테스트 호텔", "부산", "Asia/Seoul");
        jdbc.update("insert into room_type values (?, ?, ?, ?)", ROOM_TYPE, HOTEL, "스탠다드", 2);
        jdbc.update("insert into room_type values (?, ?, ?, ?)", OTHER_ROOM_TYPE, HOTEL, "스위트", 4);
        jdbc.update("insert into physical_room (id, hotel_id, room_type_id, room_number, housekeeping_status) values (?, ?, ?, '101', 'CLEAN')",
                CURRENT_ROOM, HOTEL, ROOM_TYPE);
        jdbc.update("insert into physical_room (id, hotel_id, room_type_id, room_number, housekeeping_status) values (?, ?, ?, '102', 'CLEAN')",
                NEW_ROOM, HOTEL, ROOM_TYPE);
        jdbc.update("insert into physical_room (id, hotel_id, room_type_id, room_number, housekeeping_status) values (?, ?, ?, '103', 'CLEAN')",
                THIRD_ROOM, HOTEL, ROOM_TYPE);
        jdbc.update("insert into physical_room (id, hotel_id, room_type_id, room_number, housekeeping_status) values (?, ?, ?, '201', 'CLEAN')",
                WRONG_TYPE_ROOM, HOTEL, OTHER_ROOM_TYPE);
        jdbc.update("insert into rate_plan values (?, ?, ?, false, ?)", RATE_PLAN, ROOM_TYPE, "테스트 요금", "TEST");
        jdbc.update("insert into reservation (id, room_type_id, rate_plan_id, check_in, check_out, adults, children, rooms, status, total_krw, currency, expires_at, guest_name, guest_email, management_token_hash, policy_snapshot) values (?, ?, ?, ?, ?, 2, 0, 1, 'CONFIRMED', 100000, 'KRW', now(), '테스트', 'guest@example.com', repeat('c', 64), '{}'::jsonb)",
                RESERVATION, ROOM_TYPE, RATE_PLAN, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3));
        jdbc.update("insert into reservation_room_assignment (reservation_id, physical_room_id) values (?, ?)", RESERVATION, CURRENT_ROOM);
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, 'BRANCH_STAFF', ?)",
                UUID.randomUUID(), "reassignment@example.com", "재배정 직원", new BCryptPasswordEncoder().encode("password"), HOTEL);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void listsOptionsAndReassignsOneRoomWithAnAuditRecord() {
        StaffSessionView session = staffAccess.login("reassignment@example.com", "password");

        RoomReassignmentOptions options = reassignment.options(session.token(), RESERVATION);
        RoomReassignmentResult result = reassignment.reassign(
                session.token(), RESERVATION, CURRENT_ROOM, "room-change-1",
                new RoomReassignmentRequest(NEW_ROOM));

        assertThat(options.assignments()).singleElement().extracting(AssignableRoom::roomNumber).isEqualTo("101");
        assertThat(options.candidates()).extracting(AssignableRoom::roomNumber).containsExactly("102", "103");
        assertThat(result.previousPhysicalRoomId()).isEqualTo(CURRENT_ROOM);
        assertThat(result.physicalRoomId()).isEqualTo(NEW_ROOM);
        assertThat(jdbc.queryForObject("select physical_room_id from reservation_room_assignment where reservation_id = ?",
                UUID.class, RESERVATION)).isEqualTo(NEW_ROOM);
        assertThat(jdbc.queryForObject("select count(*) from reservation_room_assignment_change where reservation_id = ?",
                Integer.class, RESERVATION)).isEqualTo(1);
        assertThat(jdbc.queryForMap("""
                select staff_id, previous_physical_room_id, previous_room_number, physical_room_id, room_number
                from reservation_room_assignment_change where reservation_id = ?
                """, RESERVATION)).containsEntry("staff_id", session.staff().id())
                .containsEntry("previous_physical_room_id", CURRENT_ROOM)
                .containsEntry("previous_room_number", "101")
                .containsEntry("physical_room_id", NEW_ROOM)
                .containsEntry("room_number", "102");
    }

    @Test
    void replaysTheSameRequestAndRejectsChangedPayloadOrActorForTheSameKey() {
        StaffSessionView session = staffAccess.login("reassignment@example.com", "password");
        RoomReassignmentRequest request = new RoomReassignmentRequest(NEW_ROOM);

        RoomReassignmentResult first = reassignment.reassign(
                session.token(), RESERVATION, CURRENT_ROOM, "room-change-retry", request);
        jdbc.update("update reservation set status = 'CHECKED_IN' where id = ?", RESERVATION);
        RoomReassignmentResult replay = reassignment.reassign(
                session.token(), RESERVATION, CURRENT_ROOM, "room-change-retry", request);

        assertThat(replay).isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from reservation_room_assignment_change where reservation_id = ?",
                Integer.class, RESERVATION)).isEqualTo(1);
        assertThatThrownBy(() -> reassignment.reassign(
                session.token(), RESERVATION, CURRENT_ROOM, "room-change-retry",
                new RoomReassignmentRequest(THIRD_ROOM)))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(exception -> ((BusinessConflictException) exception).code())
                .isEqualTo("IDEMPOTENCY_CONFLICT");

        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, 'BRANCH_STAFF', ?)",
                UUID.randomUUID(), "second-reassignment@example.com", "다른 직원",
                new BCryptPasswordEncoder().encode("password"), HOTEL);
        StaffSessionView secondSession = staffAccess.login("second-reassignment@example.com", "password");
        assertThatThrownBy(() -> reassignment.reassign(
                secondSession.token(), RESERVATION, CURRENT_ROOM, "room-change-retry", request))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(exception -> ((BusinessConflictException) exception).code())
                .isEqualTo("IDEMPOTENCY_CONFLICT");
    }

    @Test
    void rejectsDirtyOrOccupiedTargetsWithoutChangingTheAssignment() {
        StaffSessionView session = staffAccess.login("reassignment@example.com", "password");
        jdbc.update("update physical_room set housekeeping_status = 'NEEDS_CLEANING' where id = ?", NEW_ROOM);

        assertThatThrownBy(() -> reassignment.reassign(
                session.token(), RESERVATION, CURRENT_ROOM, "dirty-target",
                new RoomReassignmentRequest(NEW_ROOM)))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(exception -> ((BusinessConflictException) exception).code())
                .isEqualTo("ROOM_NOT_CLEAN");

        jdbc.update("update physical_room set housekeeping_status = 'CLEAN' where id = ?", NEW_ROOM);
        insertReservation(OTHER_RESERVATION, "CONFIRMED");
        jdbc.update("insert into reservation_room_assignment (reservation_id, physical_room_id) values (?, ?)",
                OTHER_RESERVATION, NEW_ROOM);
        assertThatThrownBy(() -> reassignment.reassign(
                session.token(), RESERVATION, CURRENT_ROOM, "occupied-target",
                new RoomReassignmentRequest(NEW_ROOM)))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(exception -> ((BusinessConflictException) exception).code())
                .isEqualTo("ROOM_ALREADY_ASSIGNED");

        assertThat(jdbc.queryForObject("select physical_room_id from reservation_room_assignment where reservation_id = ?",
                UUID.class, RESERVATION)).isEqualTo(CURRENT_ROOM);
        assertThat(jdbc.queryForObject("select count(*) from reservation_room_assignment_change where reservation_id = ?",
                Integer.class, RESERVATION)).isZero();
    }

    @Test
    void rejectsNewRequestsAfterCheckIn() {
        StaffSessionView session = staffAccess.login("reassignment@example.com", "password");
        jdbc.update("update reservation set status = 'CHECKED_IN' where id = ?", RESERVATION);

        assertThatThrownBy(() -> reassignment.reassign(
                session.token(), RESERVATION, CURRENT_ROOM, "after-check-in",
                new RoomReassignmentRequest(NEW_ROOM)))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(exception -> ((BusinessConflictException) exception).code())
                .isEqualTo("RESERVATION_NOT_REASSIGNABLE");
    }

    @Test
    void allowsOnlyOneOfTwoReservationsToClaimTheSameTargetConcurrently() throws Exception {
        insertReservation(OTHER_RESERVATION, "CONFIRMED");
        jdbc.update("insert into reservation_room_assignment (reservation_id, physical_room_id) values (?, ?)",
                OTHER_RESERVATION, THIRD_ROOM);
        StaffSessionView session = staffAccess.login("reassignment@example.com", "password");
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> concurrentReassignment(
                    start, session.token(), RESERVATION, CURRENT_ROOM, "concurrent-first"));
            Future<String> second = executor.submit(() -> concurrentReassignment(
                    start, session.token(), OTHER_RESERVATION, THIRD_ROOM, "concurrent-second"));
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder("SUCCESS", "ROOM_ALREADY_ASSIGNED");
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbc.queryForObject("""
                select count(*) from reservation_room_assignment where physical_room_id = ?
                """, Integer.class, NEW_ROOM)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from reservation_room_assignment_change
                where reservation_id in (?, ?)
                """, Integer.class, RESERVATION, OTHER_RESERVATION)).isEqualTo(1);
    }

    @Test
    void rejectsMissingCurrentAssignmentAndWrongRoomTypeWithoutAudit() {
        StaffSessionView session = staffAccess.login("reassignment@example.com", "password");

        assertThatThrownBy(() -> reassignment.reassign(
                session.token(), RESERVATION, THIRD_ROOM, "missing-current",
                new RoomReassignmentRequest(NEW_ROOM)))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(exception -> ((BusinessConflictException) exception).code())
                .isEqualTo("ROOM_ASSIGNMENT_NOT_FOUND");
        assertThatThrownBy(() -> reassignment.reassign(
                session.token(), RESERVATION, CURRENT_ROOM, "wrong-type",
                new RoomReassignmentRequest(WRONG_TYPE_ROOM)))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(exception -> ((BusinessConflictException) exception).code())
                .isEqualTo("ROOM_NOT_MATCHED");

        assertThat(jdbc.queryForObject("select count(*) from reservation_room_assignment_change where reservation_id = ?",
                Integer.class, RESERVATION)).isZero();
    }

    @Test
    void rejectsStaffFromAnotherHotelWithoutAudit() {
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, 'BRANCH_STAFF', ?)",
                UUID.randomUUID(), "other-hotel@example.com", "타 지점 직원",
                new BCryptPasswordEncoder().encode("password"), OTHER_HOTEL);
        StaffSessionView otherHotelSession = staffAccess.login("other-hotel@example.com", "password");

        assertThatThrownBy(() -> reassignment.reassign(
                otherHotelSession.token(), RESERVATION, CURRENT_ROOM, "other-hotel",
                new RoomReassignmentRequest(NEW_ROOM)))
                .isInstanceOf(StaffAccessDeniedException.class);
        assertThat(jdbc.queryForObject("select count(*) from reservation_room_assignment_change where reservation_id = ?",
                Integer.class, RESERVATION)).isZero();
    }

    @Test
    void exposesTheOptionsAndPatchContracts() throws Exception {
        StaffSessionView session = staffAccess.login("reassignment@example.com", "password");
        var mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        mockMvc.perform(get("/api/staff/reservations/{reservationId}/room-reassignment-options", RESERVATION)
                        .header("X-Staff-Session", session.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments[0].roomNumber").value("101"))
                .andExpect(jsonPath("$.candidates[0].roomNumber").value("102"));

        mockMvc.perform(patch("/api/staff/reservations/{reservationId}/assignments/{physicalRoomId}",
                        RESERVATION, CURRENT_ROOM)
                        .header("X-Staff-Session", session.token())
                        .header("Idempotency-Key", "http-room-change")
                        .contentType("application/json")
                        .content("{\"newPhysicalRoomId\":\"" + NEW_ROOM + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousRoomNumber").value("101"))
                .andExpect(jsonPath("$.roomNumber").value("102"));
    }

    private void insertReservation(UUID id, String status) {
        jdbc.update("insert into reservation (id, room_type_id, rate_plan_id, check_in, check_out, adults, children, rooms, status, total_krw, currency, expires_at, guest_name, guest_email, management_token_hash, policy_snapshot) values (?, ?, ?, ?, ?, 2, 0, 1, ?, 100000, 'KRW', now(), '다른 고객', 'other@example.com', repeat('d', 64), '{}'::jsonb)",
                id, ROOM_TYPE, RATE_PLAN, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3), status);
    }

    private String concurrentReassignment(
            CountDownLatch start,
            String token,
            UUID reservationId,
            UUID currentRoomId,
            String idempotencyKey) throws InterruptedException {
        start.await();
        try {
            reassignment.reassign(token, reservationId, currentRoomId, idempotencyKey,
                    new RoomReassignmentRequest(NEW_ROOM));
            return "SUCCESS";
        } catch (BusinessConflictException exception) {
            return exception.code();
        }
    }

    private void clean() {
        jdbc.update("delete from staff_session");
        jdbc.update("delete from reservation_room_assignment_change where reservation_id in (?, ?)", RESERVATION, OTHER_RESERVATION);
        jdbc.update("delete from staff_member where email in ('reassignment@example.com', 'second-reassignment@example.com', 'other-hotel@example.com')");
        jdbc.update("delete from reservation_room_assignment where reservation_id in (?, ?)", RESERVATION, OTHER_RESERVATION);
        jdbc.update("delete from reservation where id in (?, ?)", RESERVATION, OTHER_RESERVATION);
        jdbc.update("delete from physical_room where id in (?, ?, ?, ?)", CURRENT_ROOM, NEW_ROOM, THIRD_ROOM, WRONG_TYPE_ROOM);
        jdbc.update("delete from rate_plan where id = ?", RATE_PLAN);
        jdbc.update("delete from room_type where id in (?, ?)", ROOM_TYPE, OTHER_ROOM_TYPE);
        jdbc.update("delete from hotel where id in (?, ?)", HOTEL, OTHER_HOTEL);
    }
}
