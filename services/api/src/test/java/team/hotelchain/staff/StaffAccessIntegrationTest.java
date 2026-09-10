package team.hotelchain.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootTest
class StaffAccessIntegrationTest {

    private static final UUID SOKCHO = UUID.fromString("11000000-0000-0000-0000-000000000001");
    private static final UUID JEJU = UUID.fromString("11000000-0000-0000-0000-000000000003");

    @Autowired JdbcTemplate jdbc;
    @Autowired StaffAccessService staffAccess;

    @BeforeEach
    void seedStaff() {
        clean();
        jdbc.update("insert into hotel values (?, ?, ?, ?)", SOKCHO, "속초 테스트", "속초", "Asia/Seoul");
        jdbc.update("insert into hotel values (?, ?, ?, ?)", JEJU, "제주 테스트", "제주도", "Asia/Seoul");
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "hq@example.com", "본사 관리자", encoder.encode("hq-password"), "HQ_ADMIN", null);
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "sokcho@example.com", "속초 직원", encoder.encode("branch-password"), "BRANCH_STAFF", SOKCHO);
    }

    @AfterEach
    void cleanAfter() {
        clean();
    }

    @Test
    void branchStaffCannotAccessAnotherHotelButHeadOfficeCan() {
        StaffSessionView branch = staffAccess.login("sokcho@example.com", "branch-password");
        staffAccess.requireHotel(branch.token(), SOKCHO);
        assertThatThrownBy(() -> staffAccess.requireHotel(branch.token(), JEJU))
                .isInstanceOf(StaffAccessDeniedException.class);

        StaffSessionView headOffice = staffAccess.login("hq@example.com", "hq-password");
        assertThat(staffAccess.requireHotel(headOffice.token(), JEJU).role()).isEqualTo("HQ_ADMIN");
    }

    private void clean() {
        jdbc.update("delete from staff_session");
        jdbc.update("delete from staff_member");
        jdbc.update("delete from hotel where id in (?, ?)", SOKCHO, JEJU);
    }
}
