package team.hotelchain.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffSessionView;

@SpringBootTest
class StaffReservationPartyUpdateIntegrationTest {
    private static final UUID HOTEL = UUID.fromString("15000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_HOTEL = UUID.fromString("15000000-0000-0000-0000-000000000002");
    private static final UUID ROOM_TYPE = UUID.fromString("25000000-0000-0000-0000-000000000001");
    private static final UUID RATE_PLAN = UUID.fromString("35000000-0000-0000-0000-000000000001");
    private static final UUID RESERVATION = UUID.fromString("45000000-0000-0000-0000-000000000001");
    private static final UUID STAFF = UUID.fromString("55000000-0000-0000-0000-000000000001");
    private static final UUID SAME_HOTEL_STAFF = UUID.fromString("55000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_HOTEL_STAFF = UUID.fromString("55000000-0000-0000-0000-000000000003");
    private static final UUID HQ_STAFF = UUID.fromString("55000000-0000-0000-0000-000000000004");

    @Autowired JdbcTemplate jdbc;
    @Autowired StaffAccessService staffAccess;
    @Autowired StaffReservationPartyUpdateService partyUpdate;
    @Autowired WebApplicationContext context;

    @BeforeEach
    void seed() {
        clean();
        jdbc.update("insert into hotel values (?, ?, ?, ?)", HOTEL, "투숙 인원 테스트 호텔", "속초", "Asia/Seoul");
        jdbc.update("insert into hotel values (?, ?, ?, ?)", OTHER_HOTEL, "다른 지점", "제주", "Asia/Seoul");
        jdbc.update("insert into room_type values (?, ?, ?, ?)", ROOM_TYPE, HOTEL, "스탠다드", 2);
        jdbc.update("insert into rate_plan values (?, ?, ?, false, ?)", RATE_PLAN, ROOM_TYPE, "테스트 요금", "TEST");
        jdbc.update("""
                insert into reservation
                    (id, room_type_id, rate_plan_id, check_in, check_out, adults, children, rooms,
                     status, total_krw, currency, expires_at, guest_name, guest_email,
                     management_token_hash, policy_snapshot)
                values (?, ?, ?, ?, ?, 2, 0, 2, 'CONFIRMED', 200000, 'KRW', now(),
                        '투숙객', 'party@example.com', repeat('e', 64), '{}'::jsonb)
                """, RESERVATION, ROOM_TYPE, RATE_PLAN,
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3));
        jdbc.update("""
                insert into staff_member (id, email, display_name, password_hash, role, hotel_id)
                values (?, 'party-update@example.com', '인원 변경 담당자', ?, 'BRANCH_STAFF', ?)
                """, STAFF, new BCryptPasswordEncoder().encode("password"), HOTEL);
        jdbc.update("""
                insert into staff_member (id, email, display_name, password_hash, role, hotel_id)
                values (?, 'same-party-update@example.com', '같은 지점 담당자', ?, 'BRANCH_STAFF', ?)
                """, SAME_HOTEL_STAFF, new BCryptPasswordEncoder().encode("password"), HOTEL);
        jdbc.update("""
                insert into staff_member (id, email, display_name, password_hash, role, hotel_id)
                values (?, 'other-party-update@example.com', '다른 지점 담당자', ?, 'BRANCH_STAFF', ?)
                """, OTHER_HOTEL_STAFF, new BCryptPasswordEncoder().encode("password"), OTHER_HOTEL);
        jdbc.update("""
                insert into staff_member (id, email, display_name, password_hash, role, hotel_id)
                values (?, 'hq-party-update@example.com', '본사 담당자', ?, 'HQ_ADMIN', null)
                """, HQ_STAFF, new BCryptPasswordEncoder().encode("password"));
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void updatesThePartyWithinTheReservedRoomsCapacityAndAuditsTheChange() {
        StaffSessionView session = staffAccess.login("party-update@example.com", "password");

        StaffReservationPartyUpdateResult result = partyUpdate.update(
                session.token(), RESERVATION, "party-update-1",
                new StaffReservationPartyUpdateRequest(3, 1));

        assertThat(result.adults()).isEqualTo(3);
        assertThat(result.children()).isEqualTo(1);
        assertThat(jdbc.queryForMap("select adults, children from reservation where id = ?", RESERVATION))
                .containsEntry("adults", 3)
                .containsEntry("children", 1);
        assertThat(jdbc.queryForMap("""
                select staff_id, previous_adults, previous_children, adults, children
                from reservation_party_change where reservation_id = ?
                """, RESERVATION))
                .containsEntry("staff_id", STAFF)
                .containsEntry("previous_adults", 2)
                .containsEntry("previous_children", 0)
                .containsEntry("adults", 3)
                .containsEntry("children", 1);
    }

    @Test
    void replaysTheOriginalResultAndRejectsAnotherPayloadOrStaffForTheSameKey() {
        StaffSessionView firstSession = staffAccess.login("party-update@example.com", "password");
        StaffSessionView secondSession = staffAccess.login("same-party-update@example.com", "password");
        var request = new StaffReservationPartyUpdateRequest(3, 1);

        StaffReservationPartyUpdateResult first = partyUpdate.update(
                firstSession.token(), RESERVATION, "party-retry", request);
        jdbc.update("update reservation set status = 'CHECKED_IN' where id = ?", RESERVATION);
        StaffReservationPartyUpdateResult replay = partyUpdate.update(
                firstSession.token(), RESERVATION, "party-retry", request);

        assertThat(replay).isEqualTo(first);
        assertThatThrownBy(() -> partyUpdate.update(
                firstSession.token(), RESERVATION, "party-retry",
                new StaffReservationPartyUpdateRequest(2, 1)))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(exception -> ((BusinessConflictException) exception).code())
                .isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThatThrownBy(() -> partyUpdate.update(
                secondSession.token(), RESERVATION, "party-retry", request))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(exception -> ((BusinessConflictException) exception).code())
                .isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(jdbc.queryForObject("select count(*) from reservation_party_change where reservation_id = ?",
                Integer.class, RESERVATION)).isEqualTo(1);
    }

    @Test
    void rejectsInvalidUnchangedOrOverCapacityPartyWithoutMutation() {
        StaffSessionView session = staffAccess.login("party-update@example.com", "password");

        assertThatThrownBy(() -> partyUpdate.update(
                session.token(), RESERVATION, "invalid-party",
                new StaffReservationPartyUpdateRequest(0, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> partyUpdate.update(
                session.token(), RESERVATION, "unchanged-party",
                new StaffReservationPartyUpdateRequest(2, 0)))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(exception -> ((BusinessConflictException) exception).code())
                .isEqualTo("RESERVATION_PARTY_UNCHANGED");
        assertThatThrownBy(() -> partyUpdate.update(
                session.token(), RESERVATION, "over-capacity-party",
                new StaffReservationPartyUpdateRequest(4, 1)))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(exception -> ((BusinessConflictException) exception).code())
                .isEqualTo("RESERVATION_PARTY_CAPACITY_EXCEEDED");

        assertPartyAndNoAudit(2, 0);
    }

    @Test
    void rejectsCheckedInReservationOrStaffFromAnotherHotelWithoutMutation() {
        StaffSessionView otherHotelSession = staffAccess.login("other-party-update@example.com", "password");

        assertThatThrownBy(() -> partyUpdate.update(
                otherHotelSession.token(), RESERVATION, "other-hotel-party",
                new StaffReservationPartyUpdateRequest(3, 0)))
                .isInstanceOf(team.hotelchain.staff.StaffAccessDeniedException.class);
        StaffSessionView session = staffAccess.login("party-update@example.com", "password");
        jdbc.update("update reservation set status = 'CHECKED_IN' where id = ?", RESERVATION);
        assertThatThrownBy(() -> partyUpdate.update(
                session.token(), RESERVATION, "checked-in-party",
                new StaffReservationPartyUpdateRequest(3, 0)))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(exception -> ((BusinessConflictException) exception).code())
                .isEqualTo("RESERVATION_STATE_CONFLICT");

        assertPartyAndNoAudit(2, 0);
    }

    @Test
    void serviceChecksAuthenticationBeforeRequestValidation() {
        assertThatThrownBy(() -> partyUpdate.update(
                "invalid-session", RESERVATION, "",
                new StaffReservationPartyUpdateRequest(0, -1)))
                .isInstanceOf(team.hotelchain.staff.StaffAuthenticationException.class);
    }

    @Test
    void headquartersStaffCanUpdateThePartyAndIsRecordedInTheAudit() {
        StaffSessionView session = staffAccess.login("hq-party-update@example.com", "password");

        StaffReservationPartyUpdateResult result = partyUpdate.update(
                session.token(), RESERVATION, "hq-party-update",
                new StaffReservationPartyUpdateRequest(3, 0));

        assertThat(result.adults()).isEqualTo(3);
        assertThat(jdbc.queryForObject("select staff_id from reservation_party_change where reservation_id = ?",
                UUID.class, RESERVATION)).isEqualTo(HQ_STAFF);
    }

    @Test
    void endpointUpdatesTheParty() throws Exception {
        StaffSessionView session = staffAccess.login("party-update@example.com", "password");

        MockMvcBuilders.webAppContextSetup(context).build()
                .perform(patch("/api/staff/reservations/{reservationId}/party", RESERVATION)
                        .header("X-Staff-Session", session.token())
                        .header("Idempotency-Key", "http-party-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"adults":3,"children":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(RESERVATION.toString()))
                .andExpect(jsonPath("$.adults").value(3))
                .andExpect(jsonPath("$.children").value(1));
    }

    @Test
    void endpointRejectsMissingNullOrFractionalPartyFieldsWithoutMutation() throws Exception {
        StaffSessionView session = staffAccess.login("party-update@example.com", "password");
        var mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        for (String body : new String[] {
                "{\"adults\":3}",
                "{\"adults\":3,\"children\":null}",
                "{\"adults\":3.5,\"children\":0}"
        }) {
            mockMvc.perform(patch("/api/staff/reservations/{reservationId}/party", RESERVATION)
                            .header("X-Staff-Session", session.token())
                            .header("Idempotency-Key", "invalid-http-party-" + body.length())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
        assertPartyAndNoAudit(2, 0);
    }

    private void assertPartyAndNoAudit(int adults, int children) {
        assertThat(jdbc.queryForMap("select adults, children from reservation where id = ?", RESERVATION))
                .containsEntry("adults", adults)
                .containsEntry("children", children);
        assertThat(jdbc.queryForObject("select count(*) from reservation_party_change where reservation_id = ?",
                Integer.class, RESERVATION)).isZero();
    }

    private void clean() {
        jdbc.update("delete from staff_session");
        jdbc.update("delete from reservation_party_change where reservation_id = ?", RESERVATION);
        jdbc.update("delete from staff_member where id in (?, ?, ?, ?)",
                STAFF, SAME_HOTEL_STAFF, OTHER_HOTEL_STAFF, HQ_STAFF);
        jdbc.update("delete from reservation where id = ?", RESERVATION);
        jdbc.update("delete from rate_plan where id = ?", RATE_PLAN);
        jdbc.update("delete from room_type where id = ?", ROOM_TYPE);
        jdbc.update("delete from hotel where id in (?, ?)", HOTEL, OTHER_HOTEL);
    }
}
