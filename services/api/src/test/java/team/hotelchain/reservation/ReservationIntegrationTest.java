package team.hotelchain.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class ReservationIntegrationTest {

    private static final UUID HOTEL_ID = UUID.fromString("11000000-0000-0000-0000-000000000001");
    private static final UUID ROOM_TYPE_ID = UUID.fromString("21000000-0000-0000-0000-000000000001");
    private static final UUID RATE_PLAN_ID = UUID.fromString("31000000-0000-0000-0000-000000000001");
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 11, 2);
    private static final String TOKEN = token(1);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ReservationService reservationService;

    @BeforeEach
    void seedOffer() {
        clean();
        jdbc.update("insert into hotel values (?, ?, ?, ?)", HOTEL_ID, "속초 테스트", "속초", "Asia/Seoul");
        jdbc.update("insert into room_type values (?, ?, ?, ?)", ROOM_TYPE_ID, HOTEL_ID, "스탠다드", 3);
        jdbc.update("insert into rate_plan values (?, ?, ?, ?, ?)", RATE_PLAN_ID, ROOM_TYPE_ID, "유연 요금", false, "FLEX-2026-01");
        for (int day = 0; day < 3; day++) {
            LocalDate date = CHECK_IN.plusDays(day);
            jdbc.update("insert into rate_day values (?, ?, ?)", RATE_PLAN_ID, date, 100_000);
            jdbc.update("insert into inventory_day values (?, ?, 1, 0, 0)", ROOM_TYPE_ID, date);
        }
    }

    @AfterEach
    void cleanAfter() {
        clean();
    }

    @Test
    void onlyOneConcurrentRequestCanHoldTheLastRoom() throws Exception {
        ReservationRequest request = request(CHECK_IN, CHECK_IN.plusDays(2), 200_000);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Object> first = executor.submit(() -> reserveAfter(start, "request-a", token(2), request));
            Future<Object> second = executor.submit(() -> reserveAfter(start, "request-b", token(3), request));
            start.countDown();

            List<Object> outcomes = List.of(first.get(), second.get());
            assertThat(outcomes.stream().filter(ReservationView.class::isInstance)).hasSize(1);
            assertThat(outcomes.stream().filter(BusinessConflictException.class::isInstance))
                    .singleElement()
                    .satisfies(error -> assertThat(((BusinessConflictException) error).code()).isEqualTo("SOLD_OUT"));
        }

        assertThat(jdbc.queryForObject("select sum(held) from inventory_day where room_type_id = ?", Integer.class, ROOM_TYPE_ID))
                .isEqualTo(2);
    }

    @Test
    void rollsBackEveryNightWhenOneNightIsSoldOut() {
        jdbc.update("update inventory_day set confirmed = 1 where stay_date = ?", CHECK_IN.plusDays(1));

        assertThatThrownBy(() -> reservationService.create("rollback", TOKEN, request(CHECK_IN, CHECK_IN.plusDays(3), 300_000)))
                .isInstanceOf(BusinessConflictException.class)
                .extracting("code").isEqualTo("SOLD_OUT");
        assertThat(jdbc.queryForObject("select sum(held) from inventory_day where room_type_id = ?", Integer.class, ROOM_TYPE_ID))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from reservation", Integer.class)).isZero();
    }

    @Test
    void returnsTheSameReservationForTheSameIdempotentRequestAndRejectsChangedContent() {
        ReservationRequest request = request(CHECK_IN, CHECK_IN.plusDays(2), 200_000);

        ReservationView first = reservationService.create("same-key", TOKEN, request);
        ReservationView repeated = reservationService.create("same-key", TOKEN, request);

        assertThat(repeated.id()).isEqualTo(first.id());
        assertThatThrownBy(() -> reservationService.create(
                "same-key", TOKEN, request(CHECK_IN, CHECK_IN.plusDays(2), 199_000)))
                .isInstanceOf(BusinessConflictException.class)
                .extracting("code").isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(jdbc.queryForObject("select sum(held) from inventory_day where room_type_id = ?", Integer.class, ROOM_TYPE_ID))
                .isEqualTo(2);
    }

    @Test
    void rejectsAChangedPriceWithoutHoldingInventory() {
        assertThatThrownBy(() -> reservationService.create(
                "changed-price", TOKEN, request(CHECK_IN, CHECK_IN.plusDays(2), 190_000)))
                .isInstanceOf(BusinessConflictException.class)
                .extracting("code").isEqualTo("PRICE_CHANGED");
        assertThat(jdbc.queryForObject("select sum(held) from inventory_day where room_type_id = ?", Integer.class, ROOM_TYPE_ID))
                .isZero();
    }

    @Test
    void hidesAReservationWhenTheManagementTokenDoesNotMatch() {
        ReservationView created = reservationService.create(
                "private-booking", TOKEN, request(CHECK_IN, CHECK_IN.plusDays(2), 200_000));

        assertThat(reservationService.get(created.id(), TOKEN).guest().email()).isEqualTo("guest@example.com");
        assertThatThrownBy(() -> reservationService.get(created.id(), token(9)))
                .isInstanceOf(ReservationNotFoundException.class);
    }

    private Object reserveAfter(CountDownLatch start, String key, String token, ReservationRequest request) {
        try {
            start.await();
            return reservationService.create(key, token, request);
        } catch (BusinessConflictException exception) {
            return exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private ReservationRequest request(LocalDate checkIn, LocalDate checkOut, long expectedTotal) {
        return new ReservationRequest(ROOM_TYPE_ID, RATE_PLAN_ID, checkIn, checkOut, 2, 0, 1,
                expectedTotal, new ReservationGuest("테스트 고객", "guest@example.com"));
    }

    private void clean() {
        jdbc.update("delete from reservation_idempotency");
        jdbc.update("delete from reservation_night");
        jdbc.update("delete from reservation");
        jdbc.update("delete from inventory_day where room_type_id = ?", ROOM_TYPE_ID);
        jdbc.update("delete from rate_day where rate_plan_id = ?", RATE_PLAN_ID);
        jdbc.update("delete from rate_plan where id = ?", RATE_PLAN_ID);
        jdbc.update("delete from room_type where id = ?", ROOM_TYPE_ID);
        jdbc.update("delete from hotel where id = ?", HOTEL_ID);
    }

    private static String token(int fill) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, (byte) fill);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
