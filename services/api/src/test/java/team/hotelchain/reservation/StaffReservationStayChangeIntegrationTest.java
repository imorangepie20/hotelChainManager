package team.hotelchain.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffSessionView;

@SpringBootTest
class StaffReservationStayChangeIntegrationTest {
    private static final UUID HOTEL = UUID.fromString("16000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_HOTEL = UUID.fromString("16000000-0000-0000-0000-000000000002");
    private static final UUID ROOM_TYPE = UUID.fromString("26000000-0000-0000-0000-000000000001");
    private static final UUID TARGET_ROOM_TYPE = UUID.fromString("26000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_ROOM_TYPE = UUID.fromString("26000000-0000-0000-0000-000000000003");
    private static final UUID RATE_PLAN = UUID.fromString("36000000-0000-0000-0000-000000000001");
    private static final UUID TARGET_RATE_PLAN = UUID.fromString("36000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_RATE_PLAN = UUID.fromString("36000000-0000-0000-0000-000000000003");
    private static final UUID RESERVATION = UUID.fromString("46000000-0000-0000-0000-000000000001");
    private static final UUID STAFF = UUID.fromString("56000000-0000-0000-0000-000000000001");
    private static final UUID SAME_HOTEL_STAFF = UUID.fromString("56000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_HOTEL_STAFF = UUID.fromString("56000000-0000-0000-0000-000000000003");
    private static final UUID HQ_STAFF = UUID.fromString("56000000-0000-0000-0000-000000000004");
    private static final UUID PHYSICAL_ROOM = UUID.fromString("66000000-0000-0000-0000-000000000001");

    @Autowired JdbcTemplate jdbc;
    @Autowired StaffAccessService staffAccess;
    @Autowired StaffReservationStayChangeService stayChange;
    @Autowired WebApplicationContext context;

    private LocalDate today;
    private LocalDate oldCheckIn;
    private LocalDate oldCheckOut;
    private LocalDate newCheckIn;
    private LocalDate newCheckOut;

    @BeforeEach
    void seed() {
        clean();
        today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        oldCheckIn = today.plusDays(20);
        oldCheckOut = today.plusDays(22);
        newCheckIn = today.plusDays(25);
        newCheckOut = today.plusDays(28);

        jdbc.update("insert into hotel values (?, ?, ?, ?)", HOTEL, "숙박 변경 테스트 호텔", "속초", "Asia/Seoul");
        jdbc.update("insert into hotel values (?, ?, ?, ?)", OTHER_HOTEL, "다른 지점", "제주", "Asia/Seoul");
        jdbc.update("insert into room_type values (?, ?, ?, ?)", ROOM_TYPE, HOTEL, "스탠다드", 2);
        jdbc.update("insert into room_type values (?, ?, ?, ?)", TARGET_ROOM_TYPE, HOTEL, "스위트", 4);
        jdbc.update("insert into room_type values (?, ?, ?, ?)", OTHER_ROOM_TYPE, OTHER_HOTEL, "타 지점 객실", 4);
        jdbc.update("insert into rate_plan values (?, ?, ?, false, ?)", RATE_PLAN, ROOM_TYPE, "룸 온리", "FLEX");
        jdbc.update("insert into rate_plan values (?, ?, ?, true, ?)", TARGET_RATE_PLAN, TARGET_ROOM_TYPE, "조식 포함", "FLEX");
        jdbc.update("insert into rate_plan values (?, ?, ?, false, ?)", OTHER_RATE_PLAN, OTHER_ROOM_TYPE, "타 지점 요금", "FLEX");

        seedInventory(ROOM_TYPE, RATE_PLAN, oldCheckIn, oldCheckOut, 2, 2, 100_000);
        seedInventory(TARGET_ROOM_TYPE, TARGET_RATE_PLAN, newCheckIn, newCheckOut, 3, 0, 150_000);
        seedInventory(OTHER_ROOM_TYPE, OTHER_RATE_PLAN, newCheckIn, newCheckOut, 3, 0, 120_000);
        jdbc.update("""
                insert into reservation
                    (id, room_type_id, rate_plan_id, check_in, check_out, adults, children, rooms,
                     status, total_krw, currency, expires_at, guest_name, guest_email,
                     management_token_hash, policy_snapshot)
                values (?, ?, ?, ?, ?, 2, 0, 2, 'CONFIRMED', 400000, 'KRW', now(),
                        '투숙객', 'stay-change@example.com', repeat('f', 64),
                        '{"version":"FLEX","timezone":"Asia/Seoul","refundCutoffDaysBefore":1,"refundCutoffLocalTime":"18:00"}'::jsonb)
                """, RESERVATION, ROOM_TYPE, RATE_PLAN, oldCheckIn, oldCheckOut);
        for (LocalDate date = oldCheckIn; date.isBefore(oldCheckOut); date = date.plusDays(1)) {
            jdbc.update("insert into reservation_night values (?, ?, 100000)", RESERVATION, date);
        }
        jdbc.update("insert into physical_room values (?, ?, ?, '1001', 'CLEAN')", PHYSICAL_ROOM, HOTEL, ROOM_TYPE);
        seedStaff(STAFF, "stay-change@example.com", "숙박 변경 담당자", "BRANCH_STAFF", HOTEL);
        seedStaff(SAME_HOTEL_STAFF, "same-stay-change@example.com", "같은 지점 담당자", "BRANCH_STAFF", HOTEL);
        seedStaff(OTHER_HOTEL_STAFF, "other-stay-change@example.com", "다른 지점 담당자", "BRANCH_STAFF", OTHER_HOTEL);
        seedStaff(HQ_STAFF, "hq-stay-change@example.com", "본사 담당자", "HQ_ADMIN", null);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void previewsAndChangesDatesAndRoomTypeWithServerPricesInventoryAndAudit() {
        StaffSessionView session = staffAccess.login("stay-change@example.com", "password");

        StaffReservationStayChangePreview preview = stayChange.preview(
                session.token(), RESERVATION, new StaffReservationStayChangePreviewRequest(newCheckIn, newCheckOut));
        StaffReservationStayChangeOffer offer = preview.offers().stream()
                .filter(candidate -> candidate.roomTypeId().equals(TARGET_ROOM_TYPE))
                .findFirst().orElseThrow();

        assertThat(offer.totalKrw()).isEqualTo(960_000);
        assertThat(offer.differenceKrw()).isEqualTo(560_000);
        assertThat(offer.nightlyPrices()).extracting("amount").containsExactly(150_000, 160_000, 170_000);

        StaffReservationStayChangeResult result = stayChange.update(
                session.token(), RESERVATION, "stay-change-1",
                new StaffReservationStayChangeRequest(
                        newCheckIn, newCheckOut, TARGET_ROOM_TYPE, TARGET_RATE_PLAN, 960_000));

        assertThat(result.totalKrw()).isEqualTo(960_000);
        assertThat(result.differenceKrw()).isEqualTo(560_000);
        assertThat(jdbc.queryForMap("""
                select room_type_id, rate_plan_id, check_in, check_out, total_krw
                from reservation where id = ?
                """, RESERVATION))
                .containsEntry("room_type_id", TARGET_ROOM_TYPE)
                .containsEntry("rate_plan_id", TARGET_RATE_PLAN)
                .containsEntry("check_in", java.sql.Date.valueOf(newCheckIn))
                .containsEntry("check_out", java.sql.Date.valueOf(newCheckOut))
                .containsEntry("total_krw", 960_000L);
        assertThat(jdbc.queryForList("""
                select stay_date, amount_krw from reservation_night
                where reservation_id = ? order by stay_date
                """, RESERVATION)).containsExactly(
                        Map.of("stay_date", java.sql.Date.valueOf(newCheckIn), "amount_krw", 150_000),
                        Map.of("stay_date", java.sql.Date.valueOf(newCheckIn.plusDays(1)), "amount_krw", 160_000),
                        Map.of("stay_date", java.sql.Date.valueOf(newCheckIn.plusDays(2)), "amount_krw", 170_000));
        assertConfirmed(ROOM_TYPE, oldCheckIn, oldCheckOut, 0);
        assertConfirmed(TARGET_ROOM_TYPE, newCheckIn, newCheckOut, 2);
        assertThat(jdbc.queryForMap("""
                select staff_id, previous_room_type_id, room_type_id, previous_rate_plan_id, rate_plan_id,
                       previous_check_in, previous_check_out, check_in, check_out,
                       previous_total_krw, total_krw, difference_krw
                from reservation_stay_change where reservation_id = ?
                """, RESERVATION))
                .containsEntry("staff_id", STAFF)
                .containsEntry("previous_room_type_id", ROOM_TYPE)
                .containsEntry("room_type_id", TARGET_ROOM_TYPE)
                .containsEntry("previous_rate_plan_id", RATE_PLAN)
                .containsEntry("rate_plan_id", TARGET_RATE_PLAN)
                .containsEntry("previous_check_in", java.sql.Date.valueOf(oldCheckIn))
                .containsEntry("previous_check_out", java.sql.Date.valueOf(oldCheckOut))
                .containsEntry("check_in", java.sql.Date.valueOf(newCheckIn))
                .containsEntry("check_out", java.sql.Date.valueOf(newCheckOut))
                .containsEntry("previous_total_krw", 400_000L)
                .containsEntry("total_krw", 960_000L)
                .containsEntry("difference_krw", 560_000L);
    }

    @Test
    void countsTheReservationsOwnInventoryWhenNewDatesOverlap() {
        LocalDate overlapCheckIn = oldCheckIn.plusDays(1);
        LocalDate overlapCheckOut = oldCheckOut.plusDays(1);
        jdbc.update("insert into inventory_day values (?, ?, 2, 0, 0)", ROOM_TYPE, oldCheckOut);
        jdbc.update("insert into rate_day values (?, ?, 100000)", RATE_PLAN, oldCheckOut);
        StaffSessionView session = staffAccess.login("stay-change@example.com", "password");

        StaffReservationStayChangePreview preview = stayChange.preview(
                session.token(), RESERVATION,
                new StaffReservationStayChangePreviewRequest(overlapCheckIn, overlapCheckOut));
        assertThat(preview.offers()).anySatisfy(offer -> {
            assertThat(offer.roomTypeId()).isEqualTo(ROOM_TYPE);
            assertThat(offer.totalKrw()).isEqualTo(420_000);
        });

        stayChange.update(session.token(), RESERVATION, "overlap-change",
                new StaffReservationStayChangeRequest(
                        overlapCheckIn, overlapCheckOut, ROOM_TYPE, RATE_PLAN, 420_000));

        assertThat(confirmed(ROOM_TYPE, oldCheckIn)).isZero();
        assertThat(confirmed(ROOM_TYPE, overlapCheckIn)).isEqualTo(2);
        assertThat(confirmed(ROOM_TYPE, oldCheckOut)).isEqualTo(2);
    }

    @Test
    void rollsBackEveryDateWhenOneTargetNightIsUnavailableOrTheExpectedPriceIsStale() {
        StaffSessionView session = staffAccess.login("stay-change@example.com", "password");
        jdbc.update("update inventory_day set confirmed = capacity where room_type_id = ? and stay_date = ?",
                TARGET_ROOM_TYPE, newCheckIn.plusDays(1));

        assertThatThrownBy(() -> stayChange.update(
                session.token(), RESERVATION, "sold-out-change",
                new StaffReservationStayChangeRequest(
                        newCheckIn, newCheckOut, TARGET_ROOM_TYPE, TARGET_RATE_PLAN, 960_000)))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(exception -> ((BusinessConflictException) exception).code())
                .isEqualTo("SOLD_OUT");
        assertOriginalReservationAndNoAudit();
        assertConfirmed(ROOM_TYPE, oldCheckIn, oldCheckOut, 2);
        assertThat(confirmed(TARGET_ROOM_TYPE, newCheckIn)).isZero();

        jdbc.update("update inventory_day set confirmed = 0 where room_type_id = ?", TARGET_ROOM_TYPE);
        assertThatThrownBy(() -> stayChange.update(
                session.token(), RESERVATION, "stale-price-change",
                new StaffReservationStayChangeRequest(
                        newCheckIn, newCheckOut, TARGET_ROOM_TYPE, TARGET_RATE_PLAN, 950_000)))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(exception -> ((BusinessConflictException) exception).code())
                .isEqualTo("PRICE_CHANGED");
        assertOriginalReservationAndNoAudit();
    }

    @Test
    void replaysTheResultAndRejectsAnotherPayloadOrStaffForTheSameKey() {
        StaffSessionView firstSession = staffAccess.login("stay-change@example.com", "password");
        StaffSessionView secondSession = staffAccess.login("same-stay-change@example.com", "password");
        var request = new StaffReservationStayChangeRequest(
                newCheckIn, newCheckOut, TARGET_ROOM_TYPE, TARGET_RATE_PLAN, 960_000);

        StaffReservationStayChangeResult first = stayChange.update(
                firstSession.token(), RESERVATION, "stay-retry", request);
        jdbc.update("update reservation set status = 'CHECKED_IN' where id = ?", RESERVATION);
        StaffReservationStayChangeResult replay = stayChange.update(
                firstSession.token(), RESERVATION, "stay-retry", request);

        assertThat(replay).isEqualTo(first);
        assertThatThrownBy(() -> stayChange.update(
                firstSession.token(), RESERVATION, "stay-retry",
                new StaffReservationStayChangeRequest(
                        newCheckIn.plusDays(1), newCheckOut.plusDays(1), TARGET_ROOM_TYPE, TARGET_RATE_PLAN, 960_000)))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(exception -> ((BusinessConflictException) exception).code())
                .isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThatThrownBy(() -> stayChange.update(secondSession.token(), RESERVATION, "stay-retry", request))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(exception -> ((BusinessConflictException) exception).code())
                .isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(jdbc.queryForObject(
                "select count(*) from reservation_stay_change where reservation_id = ?", Integer.class, RESERVATION))
                .isEqualTo(1);
    }

    @Test
    void rejectsOtherHotelStatusArrivalDayAssignedRoomAndCapacityWithoutMutation() {
        StaffSessionView otherHotel = staffAccess.login("other-stay-change@example.com", "password");
        var request = new StaffReservationStayChangeRequest(
                newCheckIn, newCheckOut, TARGET_ROOM_TYPE, TARGET_RATE_PLAN, 960_000);

        assertThatThrownBy(() -> stayChange.update(otherHotel.token(), RESERVATION, "other-hotel", request))
                .isInstanceOf(team.hotelchain.staff.StaffAccessDeniedException.class);
        StaffSessionView session = staffAccess.login("stay-change@example.com", "password");

        jdbc.update("update reservation set status = 'CHECKED_IN' where id = ?", RESERVATION);
        assertConflict(session, "wrong-state", request, "RESERVATION_STATE_CONFLICT");
        jdbc.update("update reservation set status = 'CONFIRMED', check_in = ?, check_out = ? where id = ?",
                today, today.plusDays(2), RESERVATION);
        assertConflict(session, "arrival-day", request, "RESERVATION_STAY_CHANGE_TOO_LATE");
        jdbc.update("update reservation set check_in = ?, check_out = ? where id = ?", oldCheckIn, oldCheckOut, RESERVATION);
        jdbc.update("insert into reservation_room_assignment values (?, ?, now())", RESERVATION, PHYSICAL_ROOM);
        assertConflict(session, "assigned-room", request, "RESERVATION_HAS_ROOM_ASSIGNMENT");
        jdbc.update("delete from reservation_room_assignment where reservation_id = ?", RESERVATION);

        var tooSmall = new StaffReservationStayChangeRequest(
                newCheckIn, newCheckOut, ROOM_TYPE, RATE_PLAN, 600_000);
        jdbc.update("update reservation set adults = 5 where id = ?", RESERVATION);
        assertConflict(session, "capacity", tooSmall, "RESERVATION_PARTY_CAPACITY_EXCEEDED");
        assertThat(jdbc.queryForObject(
                "select count(*) from reservation_stay_change where reservation_id = ?", Integer.class, RESERVATION))
                .isZero();
    }

    @Test
    void headquartersCanChangeTheStayAndIsAudited() {
        StaffSessionView session = staffAccess.login("hq-stay-change@example.com", "password");

        stayChange.update(session.token(), RESERVATION, "hq-stay-change",
                new StaffReservationStayChangeRequest(
                        newCheckIn, newCheckOut, TARGET_ROOM_TYPE, TARGET_RATE_PLAN, 960_000));

        assertThat(jdbc.queryForObject(
                "select staff_id from reservation_stay_change where reservation_id = ?", UUID.class, RESERVATION))
                .isEqualTo(HQ_STAFF);
    }

    @Test
    void endpointsPreviewAndApplyTheChange() throws Exception {
        StaffSessionView session = staffAccess.login("stay-change@example.com", "password");
        var mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        mockMvc.perform(post("/api/staff/reservations/{reservationId}/stay-change-preview", RESERVATION)
                        .header("X-Staff-Session", session.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"checkIn":"%s","checkOut":"%s"}
                                """.formatted(newCheckIn, newCheckOut)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(RESERVATION.toString()))
                .andExpect(jsonPath("$.offers[?(@.roomTypeId == '%s')].totalKrw".formatted(TARGET_ROOM_TYPE))
                        .value(960_000));

        mockMvc.perform(patch("/api/staff/reservations/{reservationId}/stay", RESERVATION)
                        .header("X-Staff-Session", session.token())
                        .header("Idempotency-Key", "http-stay-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"checkIn":"%s","checkOut":"%s","roomTypeId":"%s",
                                 "ratePlanId":"%s","expectedTotal":960000}
                                """.formatted(newCheckIn, newCheckOut, TARGET_ROOM_TYPE, TARGET_RATE_PLAN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkIn").value(newCheckIn.toString()))
                .andExpect(jsonPath("$.checkOut").value(newCheckOut.toString()))
                .andExpect(jsonPath("$.roomTypeId").value(TARGET_ROOM_TYPE.toString()))
                .andExpect(jsonPath("$.totalKrw").value(960_000))
                .andExpect(jsonPath("$.differenceKrw").value(560_000));
    }

    private void assertConflict(
            StaffSessionView session,
            String key,
            StaffReservationStayChangeRequest request,
            String code) {
        assertThatThrownBy(() -> stayChange.update(session.token(), RESERVATION, key, request))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(exception -> ((BusinessConflictException) exception).code())
                .isEqualTo(code);
    }

    private void assertOriginalReservationAndNoAudit() {
        assertThat(jdbc.queryForMap("""
                select room_type_id, rate_plan_id, check_in, check_out, total_krw
                from reservation where id = ?
                """, RESERVATION))
                .containsEntry("room_type_id", ROOM_TYPE)
                .containsEntry("rate_plan_id", RATE_PLAN)
                .containsEntry("check_in", java.sql.Date.valueOf(oldCheckIn))
                .containsEntry("check_out", java.sql.Date.valueOf(oldCheckOut))
                .containsEntry("total_krw", 400_000L);
        assertThat(jdbc.queryForObject(
                "select count(*) from reservation_stay_change where reservation_id = ?", Integer.class, RESERVATION))
                .isZero();
    }

    private void assertConfirmed(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int expected) {
        for (LocalDate date = checkIn; date.isBefore(checkOut); date = date.plusDays(1)) {
            assertThat(confirmed(roomTypeId, date)).isEqualTo(expected);
        }
    }

    private int confirmed(UUID roomTypeId, LocalDate date) {
        return jdbc.queryForObject(
                "select confirmed from inventory_day where room_type_id = ? and stay_date = ?",
                Integer.class, roomTypeId, date);
    }

    private void seedInventory(
            UUID roomTypeId,
            UUID ratePlanId,
            LocalDate checkIn,
            LocalDate checkOut,
            int capacity,
            int confirmed,
            int firstAmount) {
        int amount = firstAmount;
        for (LocalDate date = checkIn; date.isBefore(checkOut); date = date.plusDays(1)) {
            jdbc.update("insert into inventory_day values (?, ?, ?, 0, ?)", roomTypeId, date, capacity, confirmed);
            jdbc.update("insert into rate_day values (?, ?, ?)", ratePlanId, date, amount);
            amount += 10_000;
        }
    }

    private void seedStaff(UUID id, String email, String name, String role, UUID hotelId) {
        jdbc.update("""
                insert into staff_member (id, email, display_name, password_hash, role, hotel_id)
                values (?, ?, ?, ?, ?, ?)
                """, id, email, name, new BCryptPasswordEncoder().encode("password"), role, hotelId);
    }

    private void clean() {
        jdbc.update("delete from staff_session");
        jdbc.update("delete from reservation_room_assignment where reservation_id = ?", RESERVATION);
        jdbc.update("delete from reservation_stay_change where reservation_id = ?", RESERVATION);
        jdbc.update("delete from reservation_night where reservation_id = ?", RESERVATION);
        jdbc.update("delete from reservation where id = ?", RESERVATION);
        jdbc.update("delete from physical_room where id = ?", PHYSICAL_ROOM);
        jdbc.update("delete from inventory_day where room_type_id in (?, ?, ?)", ROOM_TYPE, TARGET_ROOM_TYPE, OTHER_ROOM_TYPE);
        jdbc.update("delete from rate_day where rate_plan_id in (?, ?, ?)", RATE_PLAN, TARGET_RATE_PLAN, OTHER_RATE_PLAN);
        jdbc.update("delete from rate_plan where id in (?, ?, ?)", RATE_PLAN, TARGET_RATE_PLAN, OTHER_RATE_PLAN);
        jdbc.update("delete from room_type where id in (?, ?, ?)", ROOM_TYPE, TARGET_ROOM_TYPE, OTHER_ROOM_TYPE);
        jdbc.update("delete from staff_member where id in (?, ?, ?, ?)",
                STAFF, SAME_HOTEL_STAFF, OTHER_HOTEL_STAFF, HQ_STAFF);
        jdbc.update("delete from hotel where id in (?, ?)", HOTEL, OTHER_HOTEL);
    }
}
