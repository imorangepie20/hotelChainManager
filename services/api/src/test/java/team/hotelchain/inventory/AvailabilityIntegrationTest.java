package team.hotelchain.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AvailabilityIntegrationTest {

    private static final UUID HOTEL_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ROOM_TYPE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID RATE_PLAN_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 10, 2);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AvailabilityService availabilityService;

    @BeforeEach
    void seedBaseOffer() {
        jdbc.update("insert into hotel values (?, ?, ?, ?)", HOTEL_ID, "속초 오션", "속초", "Asia/Seoul");
        jdbc.update("insert into room_type values (?, ?, ?, ?)", ROOM_TYPE_ID, HOTEL_ID, "디럭스 오션", 3);
        jdbc.update("insert into rate_plan values (?, ?, ?, ?, ?)", RATE_PLAN_ID, ROOM_TYPE_ID, "조식 포함", true, "FLEX-2026-01");
        for (int day = 0; day < 3; day++) {
            LocalDate date = CHECK_IN.plusDays(day);
            jdbc.update("insert into rate_day values (?, ?, ?)", RATE_PLAN_ID, date, day == 1 ? 180_000 : 150_000);
            jdbc.update("insert into inventory_day values (?, ?, 2, 0, 0)", ROOM_TYPE_ID, date);
        }
    }

    @Test
    void sumsNightlyPricesAndUsesTheLowestRemainingInventory() {
        jdbc.update("update inventory_day set confirmed = 1 where stay_date = ?", CHECK_IN.plusDays(1));

        List<AvailabilityOffer> offers = availabilityService.search(
                new AvailabilityQuery(HOTEL_ID, CHECK_IN, CHECK_IN.plusDays(3), 2, 0, 1));

        assertThat(offers).singleElement().satisfies(offer -> {
            assertThat(offer.remaining()).isEqualTo(1);
            assertThat(offer.total()).isEqualTo(480_000);
            assertThat(offer.nightlyPrices()).extracting(NightlyPrice::amount).containsExactly(150_000, 180_000, 150_000);
        });
    }

    @Test
    void multipliesTheDisplayedTotalByRequestedRoomCount() {
        List<AvailabilityOffer> offers = availabilityService.search(
                new AvailabilityQuery(HOTEL_ID, CHECK_IN, CHECK_IN.plusDays(3), 2, 0, 2));

        assertThat(offers).singleElement().satisfies(offer -> {
            assertThat(offer.remaining()).isEqualTo(2);
            assertThat(offer.total()).isEqualTo(960_000);
        });
    }

    @Test
    void excludesAnOfferWhenAnyNightIsSoldOut() {
        jdbc.update("update inventory_day set confirmed = capacity where stay_date = ?", CHECK_IN.plusDays(1));

        assertThat(availabilityService.search(
                new AvailabilityQuery(HOTEL_ID, CHECK_IN, CHECK_IN.plusDays(3), 2, 0, 1))).isEmpty();
    }

    @Test
    void excludesAnOfferWhenAnyInventoryDayIsMissing() {
        jdbc.update("delete from inventory_day where stay_date = ?", CHECK_IN.plusDays(1));

        assertThat(availabilityService.search(
                new AvailabilityQuery(HOTEL_ID, CHECK_IN, CHECK_IN.plusDays(3), 2, 0, 1))).isEmpty();
    }

    @Test
    void excludesRoomTypesThatCannotHoldTheParty() {
        assertThat(availabilityService.search(
                new AvailabilityQuery(HOTEL_ID, CHECK_IN, CHECK_IN.plusDays(2), 3, 1, 1))).isEmpty();
    }

    @Test
    void rejectsAnInvalidDateRange() {
        AvailabilityQuery query = new AvailabilityQuery(HOTEL_ID, CHECK_IN, CHECK_IN, 2, 0, 1);

        assertThatThrownBy(() -> availabilityService.search(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("체크아웃 날짜는 체크인 날짜보다 이후여야 합니다.");
    }
}
