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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffSessionView;

@SpringBootTest
class StaffReservationGuestUpdateIntegrationTest {
    private static final UUID HOTEL_ID = UUID.fromString("14000000-0000-0000-0000-000000000001");
    private static final UUID ROOM_TYPE_ID = UUID.fromString("24000000-0000-0000-0000-000000000001");
    private static final UUID RATE_PLAN_ID = UUID.fromString("34000000-0000-0000-0000-000000000001");
    private static final UUID RESERVATION_ID = UUID.fromString("44000000-0000-0000-0000-000000000001");
    private static final UUID STAFF_ID = UUID.fromString("54000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_HOTEL_ID = UUID.fromString("14000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_STAFF_ID = UUID.fromString("54000000-0000-0000-0000-000000000002");
    private static final UUID SAME_HOTEL_STAFF_ID = UUID.fromString("54000000-0000-0000-0000-000000000003");
    private static final UUID HQ_STAFF_ID = UUID.fromString("54000000-0000-0000-0000-000000000004");

    @Autowired JdbcTemplate jdbc;
    @Autowired StaffAccessService staffAccess;
    @Autowired StaffReservationGuestUpdateService guestUpdateService;
    @Autowired WebApplicationContext context;

    @BeforeEach
    void seed() {
        clean();
        jdbc.update("insert into hotel values (?, ?, ?, ?)", HOTEL_ID, "예약 정정 테스트", "속초", "Asia/Seoul");
        jdbc.update("insert into hotel values (?, ?, ?, ?)", OTHER_HOTEL_ID, "다른 지점", "제주", "Asia/Seoul");
        jdbc.update("insert into room_type values (?, ?, ?, ?)", ROOM_TYPE_ID, HOTEL_ID, "스탠다드", 2);
        jdbc.update("insert into rate_plan values (?, ?, ?, false, ?)", RATE_PLAN_ID, ROOM_TYPE_ID, "테스트 요금", "TEST");
        jdbc.update("""
                insert into reservation
                    (id, room_type_id, rate_plan_id, check_in, check_out, adults, children, rooms,
                     status, total_krw, currency, expires_at, guest_name, guest_email,
                     management_token_hash, policy_snapshot)
                values (?, ?, ?, ?, ?, 2, 0, 1, 'CONFIRMED', 100000, 'KRW', now(),
                        '기존 고객', 'before@example.com', repeat('c', 64), '{}'::jsonb)
                """, RESERVATION_ID, ROOM_TYPE_ID, RATE_PLAN_ID,
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3));
        jdbc.update("""
                insert into staff_member (id, email, display_name, password_hash, role, hotel_id)
                values (?, 'guest-update@example.com', '예약 정정 담당자', ?, 'BRANCH_STAFF', ?)
                """, STAFF_ID, new BCryptPasswordEncoder().encode("password"), HOTEL_ID);
        jdbc.update("""
                insert into staff_member (id, email, display_name, password_hash, role, hotel_id)
                values (?, 'other-guest-update@example.com', '다른 지점 담당자', ?, 'BRANCH_STAFF', ?)
                """, OTHER_STAFF_ID, new BCryptPasswordEncoder().encode("password"), OTHER_HOTEL_ID);
        jdbc.update("""
                insert into staff_member (id, email, display_name, password_hash, role, hotel_id)
                values (?, 'same-hotel-guest-update@example.com', '같은 지점 담당자', ?, 'BRANCH_STAFF', ?)
                """, SAME_HOTEL_STAFF_ID, new BCryptPasswordEncoder().encode("password"), HOTEL_ID);
        jdbc.update("""
                insert into staff_member (id, email, display_name, password_hash, role, hotel_id)
                values (?, 'hq-guest-update@example.com', '본사 담당자', ?, 'HQ_ADMIN', null)
                """, HQ_STAFF_ID, new BCryptPasswordEncoder().encode("password"));
    }

    @AfterEach
    void cleanAfter() {
        clean();
    }

    @Test
    void confirmedReservationGuestDetailsAreNormalizedAndAudited() {
        StaffSessionView session = staffAccess.login("guest-update@example.com", "password");

        StaffReservationGuestUpdateResult result = guestUpdateService.update(
                session.token(), RESERVATION_ID, "guest-update-1",
                new StaffReservationGuestUpdateRequest("  변경 고객  ", "  changed@example.com  "));

        assertThat(result.guestName()).isEqualTo("변경 고객");
        assertThat(result.guestEmail()).isEqualTo("changed@example.com");
        assertThat(jdbc.queryForMap("select guest_name, guest_email from reservation where id = ?", RESERVATION_ID))
                .containsEntry("guest_name", "변경 고객")
                .containsEntry("guest_email", "changed@example.com");
        assertThat(jdbc.queryForMap("""
                select staff_id, previous_guest_name, previous_guest_email, guest_name, guest_email
                from reservation_guest_change where reservation_id = ?
                """, RESERVATION_ID))
                .containsEntry("staff_id", STAFF_ID)
                .containsEntry("previous_guest_name", "기존 고객")
                .containsEntry("previous_guest_email", "before@example.com")
                .containsEntry("guest_name", "변경 고객")
                .containsEntry("guest_email", "changed@example.com");
    }

    @Test
    void repeatedRequestReturnsTheOriginalResultAndConflictingPayloadIsRejected() {
        StaffSessionView session = staffAccess.login("guest-update@example.com", "password");
        var firstRequest = new StaffReservationGuestUpdateRequest("변경 고객", "changed@example.com");

        StaffReservationGuestUpdateResult first = guestUpdateService.update(
                session.token(), RESERVATION_ID, "same-guest-update", firstRequest);
        StaffReservationGuestUpdateResult repeated = guestUpdateService.update(
                session.token(), RESERVATION_ID, "same-guest-update", firstRequest);

        assertThat(repeated).isEqualTo(first);
        assertThatThrownBy(() -> guestUpdateService.update(
                session.token(), RESERVATION_ID, "same-guest-update",
                new StaffReservationGuestUpdateRequest("다른 고객", "other@example.com")))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("같은 요청 키에 다른 예약자 정보가 사용되었습니다.");
        assertThat(jdbc.queryForObject("select count(*) from reservation_guest_change where reservation_id = ?",
                Integer.class, RESERVATION_ID)).isEqualTo(1);
    }

    @Test
    void idempotencyKeyCannotBeReplayedByAnotherAuthorizedStaffMember() {
        StaffSessionView firstSession = staffAccess.login("guest-update@example.com", "password");
        StaffSessionView secondSession = staffAccess.login("same-hotel-guest-update@example.com", "password");
        var request = new StaffReservationGuestUpdateRequest("변경 고객", "changed@example.com");

        guestUpdateService.update(firstSession.token(), RESERVATION_ID, "shared-guest-update", request);

        assertThatThrownBy(() -> guestUpdateService.update(
                secondSession.token(), RESERVATION_ID, "shared-guest-update", request))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("같은 요청 키에 다른 예약자 정보가 사용되었습니다.");
        assertThat(jdbc.queryForObject(
                "select count(*) from reservation_guest_change where reservation_id = ?",
                Integer.class, RESERVATION_ID)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select staff_id from reservation_guest_change where reservation_id = ?",
                UUID.class, RESERVATION_ID)).isEqualTo(STAFF_ID);
    }

    @Test
    void headquartersStaffCanUpdateAConfirmedReservation() {
        StaffSessionView session = staffAccess.login("hq-guest-update@example.com", "password");

        StaffReservationGuestUpdateResult result = guestUpdateService.update(
                session.token(), RESERVATION_ID, "hq-guest-update",
                new StaffReservationGuestUpdateRequest("본사 변경 고객", "hq-changed@example.com"));

        assertThat(result.guestName()).isEqualTo("본사 변경 고객");
        assertThat(jdbc.queryForObject("""
                select staff_id from reservation_guest_change where reservation_id = ?
                """, UUID.class, RESERVATION_ID)).isEqualTo(HQ_STAFF_ID);
    }

    @Test
    void authenticationIsCheckedBeforeRequestValidation() {
        assertThatThrownBy(() -> guestUpdateService.update(
                "invalid-session", RESERVATION_ID, "",
                new StaffReservationGuestUpdateRequest("", "invalid")))
                .isInstanceOf(team.hotelchain.staff.StaffAuthenticationException.class);
    }

    @Test
    void unchangedOrInvalidGuestDetailsAreRejectedWithoutAudit() {
        StaffSessionView session = staffAccess.login("guest-update@example.com", "password");

        assertThatThrownBy(() -> guestUpdateService.update(
                session.token(), RESERVATION_ID, "unchanged-guest-update",
                new StaffReservationGuestUpdateRequest("기존 고객", "before@example.com")))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("변경할 예약자 정보를 입력해 주세요.");
        assertThatThrownBy(() -> guestUpdateService.update(
                session.token(), RESERVATION_ID, "invalid-guest-update",
                new StaffReservationGuestUpdateRequest("변경 고객", "invalid@")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("select count(*) from reservation_guest_change where reservation_id = ?",
                Integer.class, RESERVATION_ID)).isZero();
    }

    @Test
    void onlyTheReservationHotelCanUpdateAConfirmedReservation() {
        StaffSessionView otherSession = staffAccess.login("other-guest-update@example.com", "password");

        assertThatThrownBy(() -> guestUpdateService.update(
                otherSession.token(), RESERVATION_ID, "other-hotel-guest-update",
                new StaffReservationGuestUpdateRequest("변경 고객", "changed@example.com")))
                .isInstanceOf(team.hotelchain.staff.StaffAccessDeniedException.class);
        assertGuestDetailsAndNoAudit("기존 고객", "before@example.com");

        StaffSessionView session = staffAccess.login("guest-update@example.com", "password");
        jdbc.update("update reservation set status = 'CHECKED_IN' where id = ?", RESERVATION_ID);
        assertThatThrownBy(() -> guestUpdateService.update(
                session.token(), RESERVATION_ID, "checked-in-guest-update",
                new StaffReservationGuestUpdateRequest("변경 고객", "changed@example.com")))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("확정된 예약의 예약자 정보만 수정할 수 있습니다.");
        assertGuestDetailsAndNoAudit("기존 고객", "before@example.com");
    }

    @Test
    void endpointUpdatesTheGuestDetails() throws Exception {
        StaffSessionView session = staffAccess.login("guest-update@example.com", "password");

        MockMvcBuilders.webAppContextSetup(context).build()
                .perform(patch("/api/staff/reservations/{reservationId}/guest", RESERVATION_ID)
                        .header("X-Staff-Session", session.token())
                        .header("Idempotency-Key", "http-guest-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"guestName":"변경 고객","guestEmail":"changed@example.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(RESERVATION_ID.toString()))
                .andExpect(jsonPath("$.guestName").value("변경 고객"))
                .andExpect(jsonPath("$.guestEmail").value("changed@example.com"));
    }

    private void clean() {
        jdbc.update("delete from staff_session");
        jdbc.update("delete from reservation_guest_change where reservation_id = ?", RESERVATION_ID);
        jdbc.update("delete from staff_member where id in (?, ?, ?, ?)",
                STAFF_ID, OTHER_STAFF_ID, SAME_HOTEL_STAFF_ID, HQ_STAFF_ID);
        jdbc.update("delete from reservation where id = ?", RESERVATION_ID);
        jdbc.update("delete from rate_plan where id = ?", RATE_PLAN_ID);
        jdbc.update("delete from room_type where id = ?", ROOM_TYPE_ID);
        jdbc.update("delete from hotel where id in (?, ?)", HOTEL_ID, OTHER_HOTEL_ID);
    }

    private void assertGuestDetailsAndNoAudit(String guestName, String guestEmail) {
        assertThat(jdbc.queryForMap("select guest_name, guest_email from reservation where id = ?", RESERVATION_ID))
                .containsEntry("guest_name", guestName)
                .containsEntry("guest_email", guestEmail);
        assertThat(jdbc.queryForObject("select count(*) from reservation_guest_change where reservation_id = ?",
                Integer.class, RESERVATION_ID)).isZero();
    }
}
