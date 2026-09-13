package team.hotelchain.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class RoomOperationalStatusIntegrationTest {
    private static final UUID HOTEL = UUID.fromString("14000000-0000-0000-0000-000000000001");
    private static final UUID ROOM_TYPE = UUID.fromString("24000000-0000-0000-0000-000000000001");
    private static final UUID ROOM = UUID.fromString("34000000-0000-0000-0000-000000000001");

    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        clean();
        jdbc.update("insert into hotel values (?, ?, ?, ?)", HOTEL, "객실 운영 테스트 호텔", "서울", "Asia/Seoul");
        jdbc.update("insert into room_type values (?, ?, ?, ?)", ROOM_TYPE, HOTEL, "스탠다드", 2);
        jdbc.update("""
                insert into physical_room (id, hotel_id, room_type_id, room_number, housekeeping_status)
                values (?, ?, ?, '901', 'CLEAN')
                """, ROOM, HOTEL, ROOM_TYPE);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void exposesSeparateHousekeepingAndOperationalStateSchema() {
        Map<String, Object> room = jdbc.queryForMap("""
                select housekeeping_status, operational_status, operational_reason,
                       expected_recovery_at, operational_version
                from physical_room where id = ?
                """, ROOM);

        assertThat(room)
                .containsEntry("housekeeping_status", "CLEAN")
                .containsEntry("operational_status", "AVAILABLE")
                .containsEntry("operational_version", 0L)
                .containsEntry("operational_reason", null)
                .containsEntry("expected_recovery_at", null);
    }

    @Test
    void rejectsLegacyHousekeepingStatusAndBlankOperationalReason() {
        assertThatThrownBy(() -> jdbc.update(
                "update physical_room set housekeeping_status = 'OUT_OF_SERVICE' where id = ?", ROOM))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update("""
                update physical_room
                set operational_status = 'INSPECTION_REQUIRED', operational_reason = ' '
                where id = ?
                """, ROOM))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void clean() {
        jdbc.update("delete from physical_room where id = ?", ROOM);
        jdbc.update("delete from room_type where id = ?", ROOM_TYPE);
        jdbc.update("delete from hotel where id = ?", HOTEL);
    }
}
