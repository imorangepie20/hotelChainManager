package team.hotelchain.reservationchange;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import team.hotelchain.reservation.*;
import team.hotelchain.web.ApiExceptionHandler;

class CustomerReservationChangeControllerTest {
    @Test void missingCookieOnCurrentAndCheckoutIsNotFoundWithoutDatabaseAccess() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var service = new ReservationChangeSettlementService(jdbc, null, new ReservationAccess(), null, null,
            Clock.systemUTC(), "http://127.0.0.1:4000", "fake", "");
        var mvc = MockMvcBuilders.standaloneSetup(new CustomerReservationChangePaymentController(service))
            .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/api/reservation-change-payments/current")).andExpect(status().isNotFound());
        mvc.perform(post("/api/reservation-change-payments/current/checkout")).andExpect(status().isNotFound());
        verifyNoInteractions(jdbc);
    }
    @Test void authorizedReservationWithoutChangeReturnsNoContentAndNoStore() throws Exception {
        var reservations = mock(ReservationService.class); var jdbc = mock(JdbcTemplate.class);
        UUID id = UUID.randomUUID();
        var mvc = MockMvcBuilders.standaloneSetup(new CustomerReservationChangeSummaryController(reservations, jdbc))
            .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/api/reservations/{id}/change-summary", id).header("X-Reservation-Token", "authorized-token"))
            .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", "no-store"));
        verify(reservations).get(id, "authorized-token");
    }
    @Test void summaryRejectsWrongManagementTokenBeforeReadingChangeData() throws Exception {
        var reservations = mock(ReservationService.class); var jdbc = mock(JdbcTemplate.class);
        UUID id = UUID.randomUUID();
        when(reservations.get(id, "wrong-token")).thenThrow(new ReservationNotFoundException());
        var mvc = MockMvcBuilders.standaloneSetup(new CustomerReservationChangeSummaryController(reservations, jdbc))
            .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/api/reservations/{id}/change-summary", id).header("X-Reservation-Token", "wrong-token"))
            .andExpect(status().isNotFound());
        verifyNoInteractions(jdbc);
    }
}
