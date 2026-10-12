package com.seatreserve.session;

import com.seatreserve.common.GlobalExceptionHandler;
import com.seatreserve.config.SecurityConfig;
import com.seatreserve.seat.SeatDtos.SeatResponse;
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

import java.util.List;

import static com.seatreserve.session.SessionDtos.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SessionController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class SeatGenerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SessionService sessionService;

    private final AppUserDetails organizerPrincipal = new AppUserDetails(
            1L, "organizer@test.com", "hash", User.Role.ORGANIZER);

    @Test
    @DisplayName("POST /sessions/{sessionId}/seats/generate returns 201 Created with generated seats list")
    void generateSeats_returns201_whenValid() throws Exception {
        Long sessionId = 10L;
        GenerateSeatsResponse response = new GenerateSeatsResponse(
                sessionId,
                30,
                List.of(new SeatResponse(100L, sessionId, "A", "1", 1, com.seatreserve.seat.Seat.SeatStatus.AVAILABLE))
        );

        when(sessionService.generateSeats(eq(sessionId), any(GenerateSeatsRequest.class), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/seats/generate", sessionId)
                        .with(csrf())
                        .with(user(organizerPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "sections": [
                                        {
                                            "name": "A",
                                            "rows": 3,
                                            "seatsPerRow": 10
                                        }
                                    ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value(10))
                .andExpect(jsonPath("$.totalSeatsGenerated").value(30))
                .andExpect(jsonPath("$.seats").isArray());
    }

    @Test
    @DisplayName("POST /sessions/{sessionId}/seats/generate returns 400 Bad Request RFC 9457 when capacity mismatches")
    void generateSeats_returns400_whenCapacityMismatches() throws Exception {
        Long sessionId = 10L;

        when(sessionService.generateSeats(eq(sessionId), any(GenerateSeatsRequest.class), any()))
                .thenThrow(new IllegalArgumentException("Generated seat count (20) does not match session capacity (30)"));

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/seats/generate", sessionId)
                        .with(csrf())
                        .with(user(organizerPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "sections": [
                                        {
                                            "name": "A",
                                            "rows": 2,
                                            "seatsPerRow": 10
                                        }
                                    ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.seatreserve.com/errors/invalid-argument"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Generated seat count (20) does not match session capacity (30)"));
    }

    @Test
    @DisplayName("POST /sessions/{sessionId}/seats/generate returns 409 Conflict RFC 9457 when session already has seats")
    void generateSeats_returns409_whenAlreadySeeded() throws Exception {
        Long sessionId = 10L;

        when(sessionService.generateSeats(eq(sessionId), any(GenerateSeatsRequest.class), any()))
                .thenThrow(new IllegalStateException("Session 10 already has 30 seats generated"));

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/seats/generate", sessionId)
                        .with(csrf())
                        .with(user(organizerPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "sections": [
                                        {
                                            "name": "A",
                                            "rows": 3,
                                            "seatsPerRow": 10
                                        }
                                    ]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.seatreserve.com/errors/invalid-state"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Session 10 already has 30 seats generated"));
    }
}
