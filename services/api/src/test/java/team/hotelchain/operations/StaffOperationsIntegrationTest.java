package team.hotelchain.operations;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffSessionView;

@SpringBootTest
class StaffOperationsIntegrationTest {
    private static final UUID HOTEL = UUID.fromString("12000000-0000-0000-0000-000000000001");
    private static final UUID ROOM_TYPE = UUID.fromString("22000000-0000-0000-0000-000000000001");
    private static final UUID ROOM = UUID.fromString("32000000-0000-0000-0000-000000000001");
    private static final UUID RESERVATION = UUID.fromString("42000000-0000-0000-0000-000000000001");

    @Autowired JdbcTemplate jdbc;
    @Autowired StaffAccessService staffAccess;
    @Autowired StaffOperationsService operations;
    @Autowired DailyOperationsService dailyOperations;

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

    private UUID ratePlan() { return UUID.fromString("52000000-0000-0000-0000-000000000001"); }
    private void clean() {
        jdbc.update("delete from staff_session"); jdbc.update("delete from staff_member where email = 'operations@example.com'");
        jdbc.update("delete from reservation_room_assignment where reservation_id = ?", RESERVATION);
        jdbc.update("delete from reservation where id = ?", RESERVATION);
        jdbc.update("delete from physical_room where id = ?", ROOM);
        jdbc.update("delete from rate_plan where id = ?", ratePlan());
        jdbc.update("delete from room_type where id = ?", ROOM_TYPE);
        jdbc.update("delete from hotel where id = ?", HOTEL);
    }
}
