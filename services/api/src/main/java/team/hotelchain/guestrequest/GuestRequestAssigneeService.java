package team.hotelchain.guestrequest;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import team.hotelchain.staff.StaffPrincipal;

/** 조회와 변경이 같은 담당자 권한 정책을 사용하게 한다. */
@Service
public class GuestRequestAssigneeService {

    private final JdbcTemplate jdbc;

    public GuestRequestAssigneeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public GuestRequestAssigneeListView list(StaffPrincipal actor, UUID hotelId) {
        String actorRole = requireOperationalRole(actor);
        List<GuestRequestAssigneeListView.AssigneeView> assignees;
        if ("HQ_ADMIN".equals(actorRole)) {
            assignees = jdbc.query("""
                    select id, display_name, role, hotel_id
                      from staff_member
                     where active and role <> 'REMOVED'
                       and (role = 'HQ_ADMIN' or (role = 'BRANCH_STAFF' and hotel_id = ?))
                     order by case role when 'BRANCH_STAFF' then 0 else 1 end, display_name, id
                    """, (rs, row) -> new GuestRequestAssigneeListView.AssigneeView(
                            rs.getObject("id", UUID.class), rs.getString("display_name"),
                            rs.getString("role"), rs.getObject("hotel_id", UUID.class)), hotelId);
        } else {
            assignees = jdbc.query("""
                    select id, display_name, role, hotel_id
                      from staff_member
                     where active and role = 'BRANCH_STAFF' and hotel_id = ?
                     order by display_name, id
                    """, (rs, row) -> new GuestRequestAssigneeListView.AssigneeView(
                            rs.getObject("id", UUID.class), rs.getString("display_name"),
                            rs.getString("role"), rs.getObject("hotel_id", UUID.class)), hotelId);
        }
        return new GuestRequestAssigneeListView(assignees);
    }

    public void requireAssignable(StaffPrincipal actor, UUID staffId, UUID hotelId) {
        String actorRole = requireOperationalRole(actor);
        String roleClause = "HQ_ADMIN".equals(actorRole)
                ? "(role = 'HQ_ADMIN' or (role = 'BRANCH_STAFF' and hotel_id = ?))"
                : "(role = 'BRANCH_STAFF' and hotel_id = ?)";
        Boolean assignable = jdbc.query("""
                select true from staff_member
                 where id = ? and active and role <> 'REMOVED' and %s
                 for share
                """.formatted(roleClause), rs -> rs.next() ? Boolean.TRUE : null, staffId, hotelId);
        if (assignable == null) {
            throw new IllegalArgumentException("지정 가능한 활성 직원을 선택해 주세요.");
        }
    }

    private String requireOperationalRole(StaffPrincipal actor) {
        if ("HQ_ADMIN".equals(actor.role()) || "BRANCH_STAFF".equals(actor.role())) {
            return actor.role();
        }
        throw new team.hotelchain.staff.StaffAccessDeniedException();
    }
}
