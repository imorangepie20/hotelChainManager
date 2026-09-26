package team.hotelchain.staff;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 본사가 직원 목록을 읽기 전용으로 확인한다.
 * SELECT만 사용하고 직원 정보를 변경하지 않는다.
 */
@Service
public class StaffAccountQueryService {

    private static final int MAX_LIMIT = 200;

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;

    public StaffAccountQueryService(JdbcTemplate jdbc, StaffAccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    public List<StaffAccountView> list(String token, Integer limit) {
        access.requireHeadquarters(token);
        int pageSize = limit == null ? MAX_LIMIT : Math.min(MAX_LIMIT, Math.max(1, limit));
        // active는 V56까지 항상 true였다. 이제 DB에서 읽는다.
        // 삭제된 직원은 role이 REMOVED다. 목록에서 빼서 본사가 살아 있는
        // 계정만 보게 한다. SELECT만 사용한다.
        return jdbc.query("""
                select m.id, m.email, m.display_name, m.role, m.hotel_id, h.name as hotel_name, m.active
                  from staff_member m
                  left join hotel h on h.id = m.hotel_id
                 where m.role <> 'REMOVED'
                 order by m.email
                 limit ?
                """, (rs, row) -> new StaffAccountView(
                        rs.getObject("id", java.util.UUID.class).toString(),
                        rs.getString("email"),
                        rs.getString("display_name"),
                        rs.getString("role"),
                        rs.getObject("hotel_id", java.util.UUID.class) == null
                                ? null : rs.getObject("hotel_id", java.util.UUID.class).toString(),
                        rs.getString("hotel_name"),
                        rs.getBoolean("active")),
                pageSize);
    }
}
