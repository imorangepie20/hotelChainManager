package team.hotelchain.hotel;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.staff.StaffAccessService;

/**
 * 본사가 지점 목록을 읽는다.
 * <p>
 * 모든 쿼리가 SELECT이므로 지점 상태를 변경하지 않는다. 지점 목록은 카탈로그·재고·
 * 보고서 화면의 지점 선택기가 쓴다.
 */
@Service
public class HotelQueryService {

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;

    public HotelQueryService(JdbcTemplate jdbc, StaffAccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<HotelSummary> list(String token) {
        access.requireHeadquarters(token);
        // 직원은 중지한 지점도 본다. 다시 판매하려면 상태를 알아야 한다.
        return jdbc.query("""
                select id, name, region, timezone, active
                  from hotel
                 order by active desc, name
                """, (rs, rowNumber) -> new HotelSummary(
                rs.getObject("id", java.util.UUID.class),
                rs.getString("name"), rs.getString("region"), rs.getString("timezone"),
                rs.getBoolean("active")));
    }
}
