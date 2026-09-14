package team.hotelchain.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

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
import team.hotelchain.staff.StaffAccessDeniedException;
import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffSessionView;

@SpringBootTest
class RoomOperationalStatusIntegrationTest {
    private static final UUID HOTEL = UUID.fromString("14000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_HOTEL = UUID.fromString("14000000-0000-0000-0000-000000000002");
    private static final UUID ROOM_TYPE = UUID.fromString("24000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ROOM_TYPE = UUID.fromString("24000000-0000-0000-0000-000000000002");
    private static final UUID ROOM = UUID.fromString("34000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_ROOM = UUID.fromString("34000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_ROOM = UUID.fromString("34000000-0000-0000-0000-000000000003");
    private static final UUID RESERVATION = UUID.fromString("44000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_RESERVATION = UUID.fromString("44000000-0000-0000-0000-000000000002");
    private static final UUID RATE_PLAN = UUID.fromString("54000000-0000-0000-0000-000000000001");
    private static final UUID STAFF = UUID.fromString("64000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_STAFF = UUID.fromString("64000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_STAFF = UUID.fromString("64000000-0000-0000-0000-000000000003");

    @Autowired JdbcTemplate jdbc;
    @Autowired StaffAccessService staffAccess;
    @Autowired RoomOperationsService roomOperations;
    @Autowired WebApplicationContext context;
    @Autowired Clock clock;

    @BeforeEach
    void seed() {
        clean();
        jdbc.update("insert into hotel values (?, ?, ?, ?)", HOTEL, "객실 운영 테스트 호텔", "서울", "Asia/Seoul");
        jdbc.update("insert into hotel values (?, ?, ?, ?)", OTHER_HOTEL, "다른 호텔", "부산", "Asia/Seoul");
        jdbc.update("insert into room_type values (?, ?, ?, ?)", ROOM_TYPE, HOTEL, "스탠다드", 2);
        jdbc.update("insert into room_type values (?, ?, ?, ?)", OTHER_ROOM_TYPE, OTHER_HOTEL, "스탠다드", 2);
        jdbc.update("""
                insert into physical_room (id, hotel_id, room_type_id, room_number, housekeeping_status)
                values (?, ?, ?, '901', 'CLEAN'), (?, ?, ?, '902', 'CLEAN'), (?, ?, ?, '801', 'CLEAN')
                """, ROOM, HOTEL, ROOM_TYPE, SECOND_ROOM, HOTEL, ROOM_TYPE,
                OTHER_ROOM, OTHER_HOTEL, OTHER_ROOM_TYPE);
        jdbc.update("insert into rate_plan values (?, ?, ?, false, ?)", RATE_PLAN, ROOM_TYPE, "테스트 요금", "TEST");
        var password = new BCryptPasswordEncoder().encode("password");
        jdbc.update("""
                insert into staff_member (id, email, display_name, password_hash, role, hotel_id)
                values (?, 'room-operations@example.com', '운영 직원', ?, 'BRANCH_STAFF', ?),
                       (?, 'room-operations-second@example.com', '다른 운영 직원', ?, 'BRANCH_STAFF', ?),
                       (?, 'room-operations-other@example.com', '타 지점 직원', ?, 'BRANCH_STAFF', ?)
                """, STAFF, password, HOTEL, SECOND_STAFF, password, HOTEL,
                OTHER_STAFF, password, OTHER_HOTEL);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void exposesSeparateHousekeepingAndOperationalStateSchema() {
        Map<String, Object> room = jdbc.queryForMap("""
                select housekeeping_status, operational_status, operational_reason,
                       expected_recovery_at, operational_version
                from physical_room where id = ?
                """, ROOM);

        assertThat(room)
                .containsEntry("housekeeping_status", "CLEAN")
                .containsEntry("operational_status", "AVAILABLE")
                .containsEntry("operational_version", 0L)
                .containsEntry("operational_reason", null)
                .containsEntry("expected_recovery_at", null);
    }

    @Test
    void rejectsLegacyHousekeepingStatusAndBlankOperationalReason() {
        assertThatThrownBy(() -> jdbc.update(
                "update physical_room set housekeeping_status = 'OUT_OF_SERVICE' where id = ?", ROOM))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update("""
                update physical_room
                set operational_status = 'INSPECTION_REQUIRED', operational_reason = ' '
                where id = ?
                """, ROOM))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void listsRoomStateImpactAndRecentEventsForTheAuthorizedHotel() {
        StaffSessionView session = login("room-operations@example.com");
        insertReservation(RESERVATION, "CONFIRMED", localToday().plusDays(1), localToday().plusDays(2), "현재 예약");
        jdbc.update("insert into reservation_room_assignment (reservation_id, physical_room_id) values (?, ?)",
                RESERVATION, ROOM);
        roomOperations.transition(session.token(), ROOM, "inspection-list",
                new RoomOperationalTransitionRequest("INSPECTION_REQUIRED", "창문 점검", null, 0));

        RoomOperationsView result = roomOperations.list(session.token(), HOTEL);

        assertThat(result.hotelId()).isEqualTo(HOTEL);
        assertThat(result.rooms()).extracting(RoomOperationsView.RoomItem::physicalRoomId)
                .containsExactly(ROOM, SECOND_ROOM);
        assertThat(result.summary().inspectionRequired()).isEqualTo(1);
        assertThat(result.summary().outOfService()).isZero();
        assertThat(result.summary().overdueRecovery()).isZero();
        assertThat(result.rooms().getFirst().impactedAssignments()).singleElement()
                .extracting(RoomOperationsView.ImpactedAssignment::reservationId).isEqualTo(RESERVATION);
        assertThat(result.rooms().getFirst().events()).singleElement()
                .extracting(RoomOperationsView.EventItem::status).isEqualTo("INSPECTION_REQUIRED");
    }

    @Test
    void marksInspectionRequiredAndRestoresOnlyAfterCleaning() {
        StaffSessionView session = login("room-operations@example.com");
        RoomOperationalTransitionResult inspection = roomOperations.transition(session.token(), ROOM, "inspection-1",
                new RoomOperationalTransitionRequest("INSPECTION_REQUIRED", "누수 흔적 점검", null, 0));

        assertThat(inspection.operationalVersion()).isEqualTo(1);
        assertThat(inspection.operationalStatus()).isEqualTo("INSPECTION_REQUIRED");
        assertThat(jdbc.queryForObject("select count(*) from physical_room_operational_event where physical_room_id = ?",
                Integer.class, ROOM)).isEqualTo(1);

        jdbc.update("update physical_room set housekeeping_status = 'NEEDS_CLEANING' where id = ?", ROOM);
        assertConflict("ROOM_NOT_CLEAN", () -> roomOperations.transition(session.token(), ROOM, "restore-dirty",
                new RoomOperationalTransitionRequest("AVAILABLE", "점검 완료", null, 1)));
        jdbc.update("update physical_room set housekeeping_status = 'CLEAN' where id = ?", ROOM);

        RoomOperationalTransitionResult restored = roomOperations.transition(session.token(), ROOM, "restore-clean",
                new RoomOperationalTransitionRequest("AVAILABLE", "점검 및 청소 완료", null, 1));
        assertThat(restored.operationalStatus()).isEqualTo("AVAILABLE");
        assertThat(restored.operationalReason()).isNull();
        assertThat(restored.operationalVersion()).isEqualTo(2);
    }

    @Test
    void blocksOutOfServiceWhenCheckedInOrFutureConfirmedAssignmentsExist() {
        StaffSessionView session = login("room-operations@example.com");
        insertReservation(RESERVATION, "CHECKED_IN", localToday().minusDays(1), localToday().plusDays(1), "투숙 고객");
        jdbc.update("insert into reservation_room_assignment (reservation_id, physical_room_id) values (?, ?)", RESERVATION, ROOM);

        assertThatThrownBy(() -> roomOperations.transition(session.token(), ROOM, "stop-checked-in",
                new RoomOperationalTransitionRequest("OUT_OF_SERVICE", "누수 점검", null, 0)))
                .isInstanceOf(RoomHasActiveAssignmentsException.class)
                .satisfies(error -> assertThat(((RoomHasActiveAssignmentsException) error).assignments())
                        .singleElement().extracting(RoomOperationsView.ImpactedAssignment::reservationId)
                        .isEqualTo(RESERVATION));

        jdbc.update("update reservation set status = 'CONFIRMED', check_in = ?, check_out = ? where id = ?",
                localToday().plusDays(2), localToday().plusDays(3), RESERVATION);
        assertConflict("ROOM_HAS_ACTIVE_ASSIGNMENTS", () -> roomOperations.transition(session.token(), ROOM,
                "stop-future", new RoomOperationalTransitionRequest("OUT_OF_SERVICE", "누수 점검", null, 0)));

        jdbc.update("update reservation set status = 'CHECKED_OUT', check_in = ?, check_out = ? where id = ?",
                localToday().minusDays(3), localToday().minusDays(2), RESERVATION);
        RoomOperationalTransitionResult stopped = roomOperations.transition(session.token(), ROOM, "stop-past",
                new RoomOperationalTransitionRequest("OUT_OF_SERVICE", "배관 교체", null, 0));
        assertThat(stopped.operationalStatus()).isEqualTo("OUT_OF_SERVICE");
    }

    @Test
    void replaysTheSameTransitionAndRejectsChangedPayloadActorOrVersion() {
        StaffSessionView firstStaff = login("room-operations@example.com");
        StaffSessionView secondStaff = login("room-operations-second@example.com");
        var request = new RoomOperationalTransitionRequest("INSPECTION_REQUIRED", "전기 점검", null, 0);

        RoomOperationalTransitionResult first = roomOperations.transition(firstStaff.token(), ROOM, "retry-1", request);
        roomOperations.transition(firstStaff.token(), ROOM, "available-after-retry",
                new RoomOperationalTransitionRequest("AVAILABLE", "점검 완료", null, 1));
        jdbc.update("update physical_room set housekeeping_status = 'NEEDS_CLEANING' where id = ?", ROOM);
        RoomOperationalTransitionResult replay = roomOperations.transition(firstStaff.token(), ROOM, "retry-1", request);

        assertThat(replay).isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from physical_room_operational_event where physical_room_id = ?",
                Integer.class, ROOM)).isEqualTo(2);
        assertConflict("IDEMPOTENCY_CONFLICT", () -> roomOperations.transition(firstStaff.token(), ROOM, "retry-1",
                new RoomOperationalTransitionRequest("OUT_OF_SERVICE", "전기 점검", null, 0)));
        assertConflict("IDEMPOTENCY_CONFLICT", () -> roomOperations.transition(secondStaff.token(), ROOM, "retry-1", request));
        assertConflict("ROOM_OPERATIONAL_VERSION_CONFLICT", () -> roomOperations.transition(firstStaff.token(), ROOM,
                "stale-version", new RoomOperationalTransitionRequest("AVAILABLE", "점검 완료", null, 0)));
    }

    @Test
    void rejectsAnotherHotelAndInvalidStatusWithoutWritingAnEvent() {
        StaffSessionView otherStaff = login("room-operations-other@example.com");
        StaffSessionView staff = login("room-operations@example.com");

        assertThatThrownBy(() -> roomOperations.transition(otherStaff.token(), ROOM, "wrong-hotel",
                new RoomOperationalTransitionRequest("INSPECTION_REQUIRED", "점검", null, 0)))
                .isInstanceOf(StaffAccessDeniedException.class);
        assertThatThrownBy(() -> roomOperations.transition(staff.token(), ROOM, "bad-status",
                new RoomOperationalTransitionRequest("BROKEN", "점검", null, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> roomOperations.transition(staff.token(), ROOM, "blank-reason",
                new RoomOperationalTransitionRequest("INSPECTION_REQUIRED", " ", null, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> roomOperations.transition(staff.token(), ROOM, "past-recovery",
                new RoomOperationalTransitionRequest("INSPECTION_REQUIRED", "점검", clock.instant().minusSeconds(1), 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("select count(*) from physical_room_operational_event where physical_room_id = ?",
                Integer.class, ROOM)).isZero();
    }

    @Test
    void rejectsAnHttpTransitionWithoutTheRequiredExpectedVersion() throws Exception {
        StaffSessionView session = login("room-operations@example.com");
        var mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        mockMvc.perform(post("/api/staff/rooms/{roomId}/operational-transitions", ROOM)
                        .header("X-Staff-Session", session.token())
                        .header("Idempotency-Key", "missing-version")
                        .contentType("application/json")
                        .content("""
                                {"targetStatus":"INSPECTION_REQUIRED","reason":"점검"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exposesRoomOperationsTransitionAndImpactConflictOverHttp() throws Exception {
        StaffSessionView session = login("room-operations@example.com");
        var mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        mockMvc.perform(get("/api/staff/hotels/{hotelId}/room-operations", HOTEL)
                        .header("X-Staff-Session", session.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.inspectionRequired").value(0))
                .andExpect(jsonPath("$.rooms[0].roomNumber").value("901"));

        mockMvc.perform(post("/api/staff/rooms/{roomId}/operational-transitions", SECOND_ROOM)
                        .header("X-Staff-Session", session.token())
                        .header("Idempotency-Key", "http-inspection")
                        .contentType("application/json")
                        .content("""
                                {"targetStatus":"INSPECTION_REQUIRED","reason":"시설 점검","expectedVersion":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationalStatus").value("INSPECTION_REQUIRED"));

        insertReservation(RESERVATION, "CHECKED_IN", localToday().minusDays(1), localToday().plusDays(1), "투숙 고객");
        jdbc.update("insert into reservation_room_assignment (reservation_id, physical_room_id) values (?, ?)", RESERVATION, ROOM);
        mockMvc.perform(post("/api/staff/rooms/{roomId}/operational-transitions", ROOM)
                        .header("X-Staff-Session", session.token())
                        .header("Idempotency-Key", "stop-room-1")
                        .contentType("application/json")
                        .content("""
                                {"targetStatus":"OUT_OF_SERVICE","reason":"누수 점검","expectedVersion":0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_HAS_ACTIVE_ASSIGNMENTS"))
                .andExpect(jsonPath("$.assignments[0].reservationId").value(RESERVATION.toString()));
    }

    private StaffSessionView login(String email) {
        return staffAccess.login(email, "password");
    }

    private LocalDate localToday() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Seoul"));
    }

    private void insertReservation(UUID id, String status, LocalDate checkIn, LocalDate checkOut, String guestName) {
        String hashChar = id.equals(RESERVATION) ? "e" : "f";
        jdbc.update("""
                insert into reservation
                    (id, room_type_id, rate_plan_id, check_in, check_out, adults, children, rooms, status,
                     total_krw, currency, expires_at, guest_name, guest_email, management_token_hash, policy_snapshot)
                values (?, ?, ?, ?, ?, 2, 0, 1, ?, 100000, 'KRW', now(), ?, 'guest@example.com', repeat(?, 64), '{}'::jsonb)
                """, id, ROOM_TYPE, RATE_PLAN, checkIn, checkOut, status, guestName, hashChar);
    }

    private void assertConflict(String code, org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessConflictException.class)
                .extracting(error -> ((BusinessConflictException) error).code())
                .isEqualTo(code);
    }

    private void clean() {
        jdbc.update("delete from staff_session where staff_id in (?, ?, ?)", STAFF, SECOND_STAFF, OTHER_STAFF);
        jdbc.update("delete from physical_room_operational_event where physical_room_id in (?, ?, ?)", ROOM, SECOND_ROOM, OTHER_ROOM);
        jdbc.update("delete from reservation_room_assignment where reservation_id in (?, ?)", RESERVATION, SECOND_RESERVATION);
        jdbc.update("delete from reservation where id in (?, ?)", RESERVATION, SECOND_RESERVATION);
        jdbc.update("delete from staff_member where id in (?, ?, ?)", STAFF, SECOND_STAFF, OTHER_STAFF);
        jdbc.update("delete from physical_room where id in (?, ?, ?)", ROOM, SECOND_ROOM, OTHER_ROOM);
        jdbc.update("delete from rate_plan where id = ?", RATE_PLAN);
        jdbc.update("delete from room_type where id in (?, ?)", ROOM_TYPE, OTHER_ROOM_TYPE);
        jdbc.update("delete from hotel where id in (?, ?)", HOTEL, OTHER_HOTEL);
    }
}
