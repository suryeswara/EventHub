package com.seatreserve.reservation;

import com.seatreserve.common.GlobalExceptionHandler;
import com.seatreserve.common.NotRegisteredException;
import com.seatreserve.config.SecurityConfig;
import com.seatreserve.security.AppUserDetails;
import com.seatreserve.security.RestAccessDeniedHandler;
import com.seatreserve.security.RestAuthenticationEntryPoint;
import com.seatreserve.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReservationController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class RegistrationPrerequisiteTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReservationService reservationService;

    @MockBean
    private ReservationRepository reservationRepository;

    @MockBean
    private com.seatreserve.payment.PaymentService paymentService;

    @Test
    @DisplayName("Attempting to hold a seat without event registration returns 403 Forbidden with RFC 9457 problem detail")
    void holdSeat_withoutRegistration_returns403ProblemDetail() throws Exception {
        Long eventId = 1L;
        Long seatId = 42L;
        Long userId = 99L;

        AppUserDetails customerPrincipal = new AppUserDetails(userId, "customer@test.com", "hash", User.Role.CUSTOMER);

        when(reservationService.holdSeat(seatId, userId))
                .thenThrow(new NotRegisteredException("User " + userId + " is not registered for event " + eventId));

        mockMvc.perform(post("/api/v1/events/{eventId}/seats/{seatId}/hold", eventId, seatId)
                        .with(csrf())
                        .with(user(customerPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://api.seatreserve.com/errors/not-registered"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Registration required"))
                .andExpect(jsonPath("$.detail").value("User 99 is not registered for event 1"))
                .andExpect(jsonPath("$.instance").value("/api/v1/events/1/seats/42/hold"));
    }
}
