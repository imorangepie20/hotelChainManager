package team.hotelchain.hotel;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hotels")
public class HotelController {

    private final JdbcTemplate jdbc;

    public HotelController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<HotelSummary> list() {
                // 판매 중지한 지점은 고객에게 보이지 않는다. 예약 화면에서 선택할 수 없어야 한다.
                return jdbc.query(
                        "select id, name, region, timezone from hotel where active order by name",
                        (rs, rowNumber) -> new HotelSummary(
                                rs.getObject("id", java.util.UUID.class),
                                rs.getString("name"), rs.getString("region"), rs.getString("timezone"), true));
    }
}
