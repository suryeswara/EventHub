package com.seatreserve.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatreserve.event.Event;
import com.seatreserve.event.EventRepository;
import com.seatreserve.registration.RegistrationDtos.RegisterRequest;
import com.seatreserve.registration.RegistrationService;
import com.seatreserve.reservation.Reservation;
import com.seatreserve.reservation.ReservationService;
import com.seatreserve.seat.Seat;
import com.seatreserve.seat.SeatRepository;
import com.seatreserve.security.AppUserDetails;
import com.seatreserve.session.Session;
import com.seatreserve.session.SessionRepository;
import com.seatreserve.user.User;
import com.seatreserve.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ObservabilityAndLoggingIntegrationTest {

    @DynamicPropertySource
    static void configureProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5433/seatreserve_test");
        registry.add("spring.datasource.username", () -> "seatreserve");
        registry.add("spring.datasource.password", () -> "seatreserve");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "10");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger rootLogger;

    private User organizer;
    private User customer;
    private AppUserDetails organizerDetails;
    private AppUserDetails customerDetails;
    private Event testEvent;

    @BeforeEach
    void setUp() {
        // Attach logback ListAppender to capture audit logs
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        listAppender = new ListAppender<>();
        listAppender.start();
        rootLogger.addAppender(listAppender);

        // Ensure test users exist
        organizer = userRepository.findByEmail("organizer@seatreserve.dev")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("organizer@seatreserve.dev")
                        .passwordHash(passwordEncoder.encode("Secret123!"))
                        .role(User.Role.ORGANIZER)
                        .build()));
        organizerDetails = AppUserDetails.from(organizer);

        customer = userRepository.findByEmail("customer@seatreserve.dev")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("customer@seatreserve.dev")
                        .passwordHash(passwordEncoder.encode("Secret123!"))
                        .role(User.Role.CUSTOMER)
                        .build()));
        customerDetails = AppUserDetails.from(customer);

        // Ensure test event exists
        testEvent = eventRepository.findAll().stream().findFirst().orElseGet(() ->
                eventRepository.save(Event.builder()
                        .name("Tech Conference 2026")
                        .venue("Grand Hall")
                        .startTime(Instant.now().plusSeconds(86400))
                        .organizer(organizer)
                        .build())
        );
    }

    @AfterEach
    void tearDown() {
        if (rootLogger != null && listAppender != null) {
            rootLogger.detachAppender(listAppender);
        }
    }

    // ==========================================
    // 1. Correlation ID Tests
    // ==========================================

    @Test
    @DisplayName("Correlation ID is automatically generated when absent in request")
    void autoGeneratesCorrelationId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/events"))
                .andExpect(status().isOk())
                .andReturn();

        String correlationId = result.getResponse().getHeader("X-Correlation-Id");
        assertThat(correlationId).isNotNull().isNotBlank();
        // Verify UUID format
        assertThat(UUID.fromString(correlationId)).isNotNull();
    }

    @Test
    @DisplayName("Incoming Correlation ID is preserved and echoed in response")
    void preservesIncomingCorrelationId() throws Exception {
        String testId = "req-test-uuid-999888";
        MvcResult result = mockMvc.perform(get("/api/v1/events")
                        .header("X-Correlation-Id", testId))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader("X-Correlation-Id")).isEqualTo(testId);
    }

    // ==========================================
    // 2. Actuator Security & Endpoint Exposure Tests
    // ==========================================

    @Test
    @DisplayName("/actuator/health is accessible without authentication")
    void actuatorHealthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("/actuator/info is accessible without authentication")
    void actuatorInfoIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/actuator/metrics is protected: 401 when anonymous")
    void actuatorMetricsRequiresAuth() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/actuator/metrics is protected: 403 when accessed by CUSTOMER")
    void actuatorMetricsForbiddenForCustomer() throws Exception {
        mockMvc.perform(get("/actuator/metrics")
                        .with(user(customerDetails)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("/actuator/metrics is accessible to ORGANIZER role")
    void actuatorMetricsAllowedForOrganizer() throws Exception {
        mockMvc.perform(get("/actuator/metrics")
                        .with(user(organizerDetails)))
                .andExpect(status().isOk());
    }

    // ==========================================
    // 3. Business-Event Audit Logging Tests
    // ==========================================

    @Test
    @DisplayName("Event registration logs both new registration and idempotent return")
    void logsEventRegistrationNewAndIdempotent() throws Exception {
        // Register customer for testEvent
        MvcResult firstResult = mockMvc.perform(post("/api/v1/events/" + testEvent.getId() + "/register")
                        .with(user(customerDetails))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest())))
                .andReturn();
        assertThat(firstResult.getResponse().getStatus()).isIn(200, 201);

        // Perform second (idempotent) registration for same user & event
        mockMvc.perform(post("/api/v1/events/" + testEvent.getId() + "/register")
                        .with(user(customerDetails))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest())))
                .andExpect(status().isOk());

        boolean hasRegistrationLog = listAppender.list.stream().anyMatch(event ->
                event.getFormattedMessage().contains("registration")
                        && (event.getFormattedMessage().contains("New registration created")
                        || event.getFormattedMessage().contains("Idempotent registration")));

        assertThat(hasRegistrationLog).isTrue();
    }

    @Test
    @DisplayName("Failed login attempt logs audit warning without exposing password")
    void logsFailedLoginWithoutSensitiveData() throws Exception {
        String secretPlainPassword = "MySuperSecretPlainPassword987!";
        mockMvc.perform(post("/api/v1/auth/login")
                        .param("username", "customer@seatreserve.dev")
                        .param("password", secretPlainPassword))
                .andExpect(status().isUnauthorized());

        // Verify warning log exists
        boolean hasFailureLog = listAppender.list.stream().anyMatch(event ->
                event.getLevel() == Level.WARN &&
                        (event.getFormattedMessage().contains("Login failed") ||
                         event.getFormattedMessage().contains("Authentication failure")));

        assertThat(hasFailureLog).isTrue();

        // Verify sensitive password was NEVER logged in any log entry
        boolean passwordLeaked = listAppender.list.stream().anyMatch(event ->
                event.getFormattedMessage().contains(secretPlainPassword));

        assertThat(passwordLeaked).isFalse();
    }

    @Test
    @DisplayName("Organizer authorization denial is logged at WARN level")
    void logsOrganizerAuthorizationDenial() throws Exception {
        String sessionJson = """
                {
                    "title": "Unauthorized Session",
                    "description": "Exploit attempt",
                    "startTime": "2026-10-15T09:00:00Z",
                    "endTime": "2026-10-15T10:00:00Z",
                    "capacity": 50
                }
                """;

        // Create a different organizer user (not owner of testEvent)
        User anotherOrganizer = userRepository.findByEmail("other_organizer@seatreserve.dev")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("other_organizer@seatreserve.dev")
                        .passwordHash(passwordEncoder.encode("Secret123!"))
                        .role(User.Role.ORGANIZER)
                        .build()));
        AppUserDetails otherOrganizerDetails = AppUserDetails.from(anotherOrganizer);

        mockMvc.perform(post("/api/v1/events/" + testEvent.getId() + "/sessions")
                        .with(user(otherOrganizerDetails))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionJson))
                .andExpect(status().isForbidden());

        boolean hasDenialLog = listAppender.list.stream().anyMatch(event ->
                event.getLevel() == Level.WARN &&
                        event.getFormattedMessage().contains("Organizer authorization denied"));

        assertThat(hasDenialLog).isTrue();
    }

    @Test
    @DisplayName("Seat hold succeeded, conflict, and confirm are logged at INFO level")
    void logsSeatHoldSuccessConflictAndConfirm() throws Exception {
        // Create session and seat for testEvent
        Session session = sessionRepository.save(Session.builder()
                .event(testEvent)
                .title("Observability Session")
                .startTime(Instant.now().plusSeconds(3600))
                .endTime(Instant.now().plusSeconds(7200))
                .capacity(1)
                .build());

        Seat seat = seatRepository.save(Seat.builder()
                .session(session)
                .section("A")
                .rowLabel("1")
                .seatNumber(1)
                .status(Seat.SeatStatus.AVAILABLE)
                .build());

        // Ensure customer is registered for testEvent
        registrationService.registerForEvent(testEvent.getId(), customer.getId());

        // 1. Hold seat -> should succeed
        Reservation reservation = reservationService.holdSeat(seat.getId(), customer.getId());
        assertThat(reservation).isNotNull();

        boolean hasHoldSuccessLog = listAppender.list.stream().anyMatch(event ->
                event.getLevel() == Level.INFO &&
                        event.getFormattedMessage().contains("Seat hold succeeded") &&
                        event.getFormattedMessage().contains("seatId=" + seat.getId()));
        assertThat(hasHoldSuccessLog).isTrue();

        // 2. Second hold on same seat -> should be rejected with conflict
        try {
            reservationService.holdSeat(seat.getId(), customer.getId());
        } catch (Exception ignored) {}

        boolean hasConflictLog = listAppender.list.stream().anyMatch(event ->
                event.getLevel() == Level.INFO &&
                        event.getFormattedMessage().contains("Seat hold rejected (conflict)") &&
                        event.getFormattedMessage().contains("seatId=" + seat.getId()));
        assertThat(hasConflictLog).isTrue();

        // 3. Confirm reservation
        reservationService.confirm(reservation.getId(), "client-key-" + UUID.randomUUID());

        boolean hasConfirmLog = listAppender.list.stream().anyMatch(event ->
                event.getLevel() == Level.INFO &&
                        event.getFormattedMessage().contains("Reservation confirmed") &&
                        event.getFormattedMessage().contains("reservationId=" + reservation.getId()));
        assertThat(hasConfirmLog).isTrue();
    }
}
