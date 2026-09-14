package team.hotelchain.reservationchange;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import team.hotelchain.inventory.NightlyPrice;

@SpringBootTest
class ReservationStayQuoteIntegrationTest {
    private static final UUID HOTEL = UUID.fromString("81000000-0000-0000-0000-000000000001");
    private static final UUID ROOM_TYPE = UUID.fromString("82000000-0000-0000-0000-000000000001");
    private static final UUID RATE_PLAN = UUID.fromString("83000000-0000-0000-0000-000000000001");
    private static final UUID RESERVATION = UUID.fromString("84000000-0000-0000-0000-000000000001");

    @Autowired JdbcTemplate jdbc;
    @Autowired ReservationStayQuoteService quotes;

    private LocalDate checkIn;
    private LocalDate checkOut;

    @BeforeEach
    void seed() {
        clean();
        checkIn = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(20);
        checkOut = checkIn.plusDays(2);
        jdbc.update("insert into hotel values (?, ?, ?, ?)", HOTEL, "공통 견적 테스트 호텔", "속초", "Asia/Seoul");
        jdbc.update("insert into room_type values (?, ?, ?, ?)", ROOM_TYPE, HOTEL, "스탠다드", 4);
        jdbc.update("insert into rate_plan values (?, ?, ?, false, ?)", RATE_PLAN, ROOM_TYPE, "룸 온리", "FLEX");
        jdbc.update("insert into inventory_day values (?, ?, 2, 0, 2)", ROOM_TYPE, checkIn);
        jdbc.update("insert into inventory_day values (?, ?, 2, 0, 2)", ROOM_TYPE, checkIn.plusDays(1));
        jdbc.update("insert into inventory_day values (?, ?, 2, 0, 0)", ROOM_TYPE, checkIn.plusDays(2));
        jdbc.update("insert into rate_day values (?, ?, 100000)", RATE_PLAN, checkIn);
        jdbc.update("insert into rate_day values (?, ?, 100000)", RATE_PLAN, checkIn.plusDays(1));
        jdbc.update("insert into rate_day values (?, ?, 110000)", RATE_PLAN, checkIn.plusDays(2));
        jdbc.update("""
                insert into reservation
                    (id, room_type_id, rate_plan_id, check_in, check_out, adults, children, rooms,
                     status, total_krw, currency, expires_at, guest_name, guest_email,
                     management_token_hash, policy_snapshot)
                values (?, ?, ?, ?, ?, 2, 1, 2, 'CONFIRMED', 400000, 'KRW', now(),
                        '투숙객', 'quote-guest@example.com', repeat('c', 64),
                        '{"version":"FLEX","timezone":"Asia/Seoul"}'::jsonb)
                """, RESERVATION, ROOM_TYPE, RATE_PLAN, checkIn, checkOut);
        jdbc.update("insert into reservation_night values (?, ?, 100000)", RESERVATION, checkIn);
        jdbc.update("insert into reservation_night values (?, ?, 100000)", RESERVATION, checkIn.plusDays(1));
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void returnsServerPricedOfferAndCountsOwnOverlappingInventory() {
        ReservationStayQuote quote = quotes.quote(
                RESERVATION, checkIn.plusDays(1), checkOut.plusDays(1), false);

        ReservationStayQuote.SelectedStayOffer offer = quote.selected(ROOM_TYPE, RATE_PLAN);

        assertThat(quote.hotelId()).isEqualTo(HOTEL);
        assertThat(quote.previousTotalKrw()).isEqualTo(400_000L);
        assertThat(offer.remaining()).isEqualTo(2);
        assertThat(offer.totalKrw()).isEqualTo(420_000L);
        assertThat(offer.differenceKrw()).isEqualTo(20_000L);
        assertThat(offer.nightlyPrices()).extracting(NightlyPrice::amount)
                .containsExactly(100_000, 110_000);
    }

    @Test
    void usesRequestedPartyForCapacityAndQuoteSnapshot() {
        ReservationStayQuote quote = quotes.quote(
                RESERVATION, checkIn, checkOut, 3, 1, false);

        assertThat(quote.adults()).isEqualTo(3);
        assertThat(quote.children()).isEqualTo(1);
        assertThat(quote.offers()).hasSize(1);

        ReservationStayQuote unavailable = quotes.quote(
                RESERVATION, checkIn, checkOut, 8, 1, false);
        assertThat(unavailable.offers()).isEmpty();
    }

    private void clean() {
        jdbc.update("delete from reservation_night where reservation_id = ?", RESERVATION);
        jdbc.update("delete from reservation where id = ?", RESERVATION);
        jdbc.update("delete from rate_day where rate_plan_id = ?", RATE_PLAN);
        jdbc.update("delete from inventory_day where room_type_id = ?", ROOM_TYPE);
        jdbc.update("delete from rate_plan where id = ?", RATE_PLAN);
        jdbc.update("delete from room_type where id = ?", ROOM_TYPE);
        jdbc.update("delete from hotel where id = ?", HOTEL);
    }
}
