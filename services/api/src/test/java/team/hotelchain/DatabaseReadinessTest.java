package team.hotelchain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class DatabaseReadinessTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void connectsOnlyToTheDedicatedTestDatabase() {
        String databaseName = jdbcTemplate.queryForObject("select current_database()", String.class);

        assertThat(databaseName).isEqualTo("hotel_chain_test");
    }
}
