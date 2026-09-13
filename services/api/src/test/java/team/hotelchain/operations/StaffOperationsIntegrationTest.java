package team.hotelchain.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffSessionView;

@SpringBootTest
class StaffOperationsIntegrationTest {
    private static final UUID HOTEL = UUID.fromString("12000000-0000-0000-0000-000000000001");
    private static final UUID ROOM_TYPE = UUID.fromString("22000000-0000-0000-0000-000000000001");
    private static final UUID ROOM = UUID.fromString("32000000-0000-0000-0000-000000000001");
    private static final UUID RESERVATION = UUID.fromString("42000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_RESERVATION = UUID.fromString("42000000-0000-0000-0000-000000000002");

    @Autowired JdbcTemplate jdbc;
    @Autowired StaffAccessService staffAccess;
    @Autowired StaffOperationsService operations;
    @Autowired DailyOperationsService dailyOperations;
    @Autowired StaffReservationQueryService reservationQuery;
    @Autowired WebApplicationContext context;

    @BeforeEach
    void seed() {
        clean();
        jdbc.update("insert into hotel values (?, ?, ?, ?)", HOTEL, "운영 테스트 호텔", "속초", "Asia/Seoul");
        jdbc.update("insert into room_type values (?, ?, ?, ?)", ROOM_TYPE, HOTEL, "스탠다드", 2);
        jdbc.update("insert into physical_room (id, hotel_id, room_type_id, room_number, housekeeping_status) values (?, ?, ?, ?, 'CLEAN')",
                ROOM, HOTEL, ROOM_TYPE, "101");
        jdbc.update("insert into rate_plan values (?, ?, ?, false, ?)", ratePlan(), ROOM_TYPE, "테스트 요금", "TEST");
        jdbc.update("insert into reservation (id, room_type_id, rate_plan_id, check_in, check_out, adults, children, rooms, status, total_krw, currency, expires_at, guest_name, guest_email, management_token_hash, policy_snapshot) values (?, ?, ?, ?, ?, 2, 0, 1, 'CONFIRMED', 100000, 'KRW', now(), '테스트', 'guest@example.com', repeat('a', 64), '{}'::jsonb)",
                RESERVATION, ROOM_TYPE, ratePlan(), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3));
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, 'BRANCH_STAFF', ?)",
                UUID.randomUUID(), "operations@example.com", "운영 직원", new BCryptPasswordEncoder().encode("password"), HOTEL);
    }

    @AfterEach void tearDown() { clean(); }

    @Test
    void checkoutMarksTheAssignedRoomForCleaningUntilHousekeepingCompletes() {
        StaffSessionView session = staffAccess.login("operations@example.com", "password");
        operations.assign(session.token(), RESERVATION, ROOM);
        operations.checkIn(session.token(), RESERVATION);
        operations.checkOut(session.token(), RESERVATION);

        assertThat(jdbc.queryForObject("select status from reservation where id = ?", String.class, RESERVATION)).isEqualTo("CHECKED_OUT");
        assertThat(jdbc.queryForObject("select housekeeping_status from physical_room where id = ?", String.class, ROOM)).isEqualTo("NEEDS_CLEANING");

        operations.completeHousekeeping(session.token(), ROOM);
        assertThat(jdbc.queryForObject("select housekeeping_status from physical_room where id = ?", String.class, ROOM)).isEqualTo("CLEAN");
    }

    @Test
    void returnsOnlyTheSelectedHotelsDailyOperationalWork() {
        StaffSessionView session = staffAccess.login("operations@example.com", "password");

        DailyOperationsView view = dailyOperations.get(session.token(), HOTEL, LocalDate.of(2026, 11, 2));

        assertThat(view.arrivals()).singleElement().extracting(DailyOperationsView.ReservationItem::reservationId)
                .isEqualTo(RESERVATION);
        assertThat(view.departures()).isEmpty();
        assertThat(view.roomsNeedingCleaning()).isEmpty();
    }

    @Test
    void returnsOnlyCleanMatchingRoomsThatCanBeAssigned() {
        StaffSessionView session = staffAccess.login("operations@example.com", "password");

        assertThat(operations.assignableRooms(session.token(), RESERVATION))
                .singleElement()
                .extracting(AssignableRoom::roomNumber)
                .isEqualTo("101");
    }

    @Test
    void repeatingTheSameRoomAssignmentAfterCheckInDoesNotCreateAConflictOrDuplicate() {
        StaffSessionView session = staffAccess.login("operations@example.com", "password");

        operations.assign(session.token(), RESERVATION, ROOM);
        operations.checkIn(session.token(), RESERVATION);
        operations.assign(session.token(), RESERVATION, ROOM);

        assertThat(jdbc.queryForObject("""
                select count(*) from reservation_room_assignment
                where reservation_id = ? and physical_room_id = ?
                """, Integer.class, RESERVATION, ROOM)).isEqualTo(1);
    }

    @Test
    void rejectsANonCleanRoomEvenWhenItWasPreviouslyListedAsAssignable() {
        StaffSessionView session = staffAccess.login("operations@example.com", "password");
        assertThat(operations.assignableRooms(session.token(), RESERVATION)).hasSize(1);
        jdbc.update("update physical_room set housekeeping_status = 'NEEDS_CLEANING' where id = ?", ROOM);

        assertThatThrownBy(() -> operations.assign(session.token(), RESERVATION, ROOM))
                .isInstanceOf(team.hotelchain.reservation.BusinessConflictException.class)
                .hasMessage("청결 상태인 객실만 배정할 수 있습니다.");
        assertThat(jdbc.queryForObject("""
                select count(*) from reservation_room_assignment where reservation_id = ?
                """, Integer.class, RESERVATION)).isZero();
    }

    @Test
    void marksAConfirmedReservationAsNoShow() {
        StaffSessionView session = staffAccess.login("operations@example.com", "password");
        operations.assign(session.token(), RESERVATION, ROOM);

        operations.markNoShow(session.token(), RESERVATION);

        assertThat(jdbc.queryForObject("select status from reservation where id = ?", String.class, RESERVATION))
                .isEqualTo("NO_SHOW");
        assertThat(jdbc.queryForObject("select count(*) from reservation_room_assignment where reservation_id = ?", Integer.class, RESERVATION))
                .isZero();
    }

    @Test
    void reservationSearchIncludesReservationsDepartingOnTheSelectedDate() {
        insertReservation(OTHER_RESERVATION, LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 5),
                "CONFIRMED", "다른 고객", "other@example.com");
        StaffSessionView session = staffAccess.login("operations@example.com", "password");

        StaffReservationSearchView view = reservationQuery.search(
                session.token(), HOTEL, LocalDate.of(2026, 11, 3), null, null);

        assertThat(view.reservations()).singleElement()
                .extracting(StaffReservationSearchView.ReservationSummary::reservationId)
                .isEqualTo(RESERVATION);
    }

    @Test
    void reservationSearchCapsLargeResultSetsAndReportsTruncation() {
        for (int index = 1; index <= 200; index++) {
            insertReservation(new UUID(9L, index), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3),
                    "CONFIRMED", "대량 고객 " + index, "bulk" + index + "@example.com");
        }
        StaffSessionView session = staffAccess.login("operations@example.com", "password");

        StaffReservationSearchView view = reservationQuery.search(
                session.token(), HOTEL, LocalDate.of(2026, 11, 2), null, null);

        assertThat(view.reservations()).hasSize(200);
        assertThat(view.truncated()).isTrue();
    }

    @Test
    void reservationEndpointReturnsTheStaffSearchContract() throws Exception {
        StaffSessionView session = staffAccess.login("operations@example.com", "password");

        MockMvcBuilders.webAppContextSetup(context).build()
                .perform(get("/api/staff/hotels/{hotelId}/reservations", HOTEL)
                        .param("date", "2026-11-02")
                        .header("X-Staff-Session", session.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hotelId").value(HOTEL.toString()))
                .andExpect(jsonPath("$.reservations[0].reservationId").value(RESERVATION.toString()))
                .andExpect(jsonPath("$.reservations[0].guestEmail").value("guest@example.com"));
    }

    @Test
    void reservationEndpointRequiresAValidDate() throws Exception {
        StaffSessionView session = staffAccess.login("operations@example.com", "password");

        MockMvcBuilders.webAppContextSetup(context).build()
                .perform(get("/api/staff/hotels/{hotelId}/reservations", HOTEL)
                        .header("X-Staff-Session", session.token()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reservationEndpointRejectsAnotherHotelForBranchStaff() throws Exception {
        StaffSessionView session = staffAccess.login("operations@example.com", "password");

        MockMvcBuilders.webAppContextSetup(context).build()
                .perform(get("/api/staff/hotels/{hotelId}/reservations", UUID.randomUUID())
                        .param("date", "2026-11-02")
                        .header("X-Staff-Session", session.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void reservationSearchMatchesGuestDetailsWithoutCaseSensitivity() {
        insertReservation(OTHER_RESERVATION, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3),
                "CONFIRMED", "다른 고객", "other@example.com");
        StaffSessionView session = staffAccess.login("operations@example.com", "password");

        StaffReservationSearchView view = reservationQuery.search(
                session.token(), HOTEL, LocalDate.of(2026, 11, 2), "GUEST@EXAMPLE.COM", null);

        assertThat(view.reservations()).singleElement()
                .extracting(StaffReservationSearchView.ReservationSummary::reservationId)
                .isEqualTo(RESERVATION);
    }

    @Test
    void reservationSearchMatchesAPartialReservationNumber() {
        StaffSessionView session = staffAccess.login("operations@example.com", "password");

        StaffReservationSearchView view = reservationQuery.search(
                session.token(), HOTEL, LocalDate.of(2026, 11, 2), "42000000-0000", null);

        assertThat(view.reservations()).singleElement()
                .extracting(StaffReservationSearchView.ReservationSummary::reservationId)
                .isEqualTo(RESERVATION);
    }

    @Test
    void reservationSearchFiltersByStatus() {
        insertReservation(OTHER_RESERVATION, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3),
                "CANCELLED", "테스트", "guest@example.com");
        StaffSessionView session = staffAccess.login("operations@example.com", "password");

        StaffReservationSearchView view = reservationQuery.search(
                session.token(), HOTEL, LocalDate.of(2026, 11, 2), null, "CONFIRMED");

        assertThat(view.reservations()).singleElement()
                .extracting(StaffReservationSearchView.ReservationSummary::reservationId)
                .isEqualTo(RESERVATION);
    }

    private void insertReservation(UUID id, LocalDate checkIn, LocalDate checkOut, String status,
            String guestName, String guestEmail) {
        jdbc.update("insert into reservation (id, room_type_id, rate_plan_id, check_in, check_out, adults, children, rooms, status, total_krw, currency, expires_at, guest_name, guest_email, management_token_hash, policy_snapshot) values (?, ?, ?, ?, ?, 2, 0, 1, ?, 100000, 'KRW', now(), ?, ?, repeat('b', 64), '{}'::jsonb)",
                id, ROOM_TYPE, ratePlan(), checkIn, checkOut, status, guestName, guestEmail);
    }

    private UUID ratePlan() { return UUID.fromString("52000000-0000-0000-0000-000000000001"); }
    private void clean() {
        jdbc.update("delete from staff_session"); jdbc.update("delete from staff_member where email = 'operations@example.com'");
        jdbc.update("delete from reservation_room_assignment where reservation_id = ?", RESERVATION);
        jdbc.update("delete from reservation where room_type_id = ?", ROOM_TYPE);
        jdbc.update("delete from physical_room where id = ?", ROOM);
        jdbc.update("delete from rate_plan where id = ?", ratePlan());
        jdbc.update("delete from room_type where id = ?", ROOM_TYPE);
        jdbc.update("delete from hotel where id = ?", HOTEL);
    }
}
