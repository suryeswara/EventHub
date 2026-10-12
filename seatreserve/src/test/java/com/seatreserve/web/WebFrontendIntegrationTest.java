package com.seatreserve.web;

import com.seatreserve.event.Event;
import com.seatreserve.event.EventRepository;
import com.seatreserve.reservation.Reservation;
import com.seatreserve.reservation.ReservationRepository;
import com.seatreserve.seat.Seat;
import com.seatreserve.seat.SeatRepository;
import com.seatreserve.security.AppUserDetails;
import com.seatreserve.session.Session;
import com.seatreserve.session.SessionRepository;
import com.seatreserve.user.User;
import com.seatreserve.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WebFrontendIntegrationTest {

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
    private ReservationRepository reservationRepository;

    @Autowired
    private com.seatreserve.reservation.ReservationService reservationService;

    @Autowired
    private com.seatreserve.registration.RegistrationService registrationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.seatreserve.payment.PaymentGatewayService paymentGatewayService;

    private User organizer;
    private User customer;
    private AppUserDetails organizerDetails;
    private AppUserDetails customerDetails;
    private Event testEvent;
    private Session testSession;
    private Seat testSeat;

    @BeforeEach
    void setUp() {
        paymentGatewayService.setFailureRate(0.0);
        organizer = userRepository.findByEmail("organizer@seatreserve.dev")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("organizer@seatreserve.dev")
                        .passwordHash(passwordEncoder.encode("password123"))
                        .role(User.Role.ORGANIZER)
                        .build()));
        organizerDetails = AppUserDetails.from(organizer);

        customer = userRepository.findByEmail("customer@seatreserve.dev")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("customer@seatreserve.dev")
                        .passwordHash(passwordEncoder.encode("password123"))
                        .role(User.Role.CUSTOMER)
                        .build()));
        customerDetails = AppUserDetails.from(customer);

        testEvent = eventRepository.save(Event.builder()
                .name("Web UI Test Conference " + UUID.randomUUID())
                .venue("Convention Hall A")
                .startTime(Instant.now().plusSeconds(86400))
                .organizer(organizer)
                .build());

        testSession = sessionRepository.save(Session.builder()
                .event(testEvent)
                .title("Cloud Architecture Workshop")
                .description("Hands-on server-side rendered workshop")
                .startTime(Instant.now().plusSeconds(3600))
                .endTime(Instant.now().plusSeconds(7200))
                .capacity(10)
                .build());

        testSeat = seatRepository.save(Seat.builder()
                .session(testSession)
                .section("A")
                .rowLabel("1")
                .seatNumber(1)
                .status(Seat.SeatStatus.AVAILABLE)
                .build());
    }

    // ==========================================
    // 1. CSRF Token Presence Tests
    // ==========================================

    @Test
    @DisplayName("Rendered login form contains visible CSRF hidden input in HTML source")
    void loginPage_containsCsrfToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertThat(html).contains("name=\"_csrf\"");
        assertThat(html).contains("type=\"hidden\"");
        assertThat(html).contains("action=\"/login\"");
    }

    @Test
    @DisplayName("Rendered register form contains visible CSRF hidden input in HTML source")
    void registerPage_containsCsrfToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertThat(html).contains("name=\"_csrf\"");
        assertThat(html).contains("type=\"hidden\"");
        assertThat(html).contains("action=\"/register\"");
    }

    // ==========================================
    // 2. Role-Aware Navigation Bar Tests
    // ==========================================

    @Test
    @DisplayName("Navigation bar displays anonymous options (Sign In, Register) for guest users")
    void navBar_showsAnonymousLinks() throws Exception {
        MvcResult result = mockMvc.perform(get("/events"))
                .andExpect(status().isOk())
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertThat(html).contains("Sign In");
        assertThat(html).contains("Register");
        assertThat(html).doesNotContain("My Reservations");
        assertThat(html).doesNotContain("Create Event");
    }

    @Test
    @DisplayName("Navigation bar displays Customer options (My Reservations) for CUSTOMER role")
    void navBar_showsCustomerLinks() throws Exception {
        MvcResult result = mockMvc.perform(get("/events")
                        .with(user(customerDetails)))
                .andExpect(status().isOk())
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertThat(html).contains("My Reservations");
        assertThat(html).contains("Customer");
        assertThat(html).contains(customer.getEmail());
        assertThat(html).doesNotContain("Create Event");
    }

    @Test
    @DisplayName("Navigation bar displays Organizer options (Create Event) for ORGANIZER role")
    void navBar_showsOrganizerLinks() throws Exception {
        MvcResult result = mockMvc.perform(get("/events")
                        .with(user(organizerDetails)))
                .andExpect(status().isOk())
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertThat(html).contains("Create Event");
        assertThat(html).contains("Organizer");
        assertThat(html).contains(organizer.getEmail());
        assertThat(html).doesNotContain("My Reservations");
    }

    // ==========================================
    // 3. Full End-to-End Browser Flow (Zero JavaScript)
    // ==========================================

    @Test
    @DisplayName("Complete customer journey: Register -> Login -> Browse -> Register Event -> Hold Seat -> Confirm -> Booked")
    void fullCustomerFlow_endToEndWithoutJavaScript() throws Exception {
        String testEmail = "webflow_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";

        // Step 1: Submit web registration form
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("email", testEmail)
                        .param("password", "secretPassword123")
                        .param("role", "CUSTOMER"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        // Step 2: Form Login
        MvcResult loginResult = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", testEmail)
                        .param("password", "secretPassword123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/events"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();
        assertThat(session).isNotNull();

        // Step 3: Browse Events list
        mockMvc.perform(get("/events").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(testEvent.getName())));

        // Step 4: View Event detail
        mockMvc.perform(get("/events/" + testEvent.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Register for Event")));

        // Step 5: Register for Event via Form POST
        mockMvc.perform(post("/events/" + testEvent.getId() + "/register")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/events/" + testEvent.getId()));

        // Step 6: View Session Seat Map (Seat should be AVAILABLE)
        mockMvc.perform(get("/sessions/" + testSession.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("seat-available")));

        // Step 7: Form POST to Hold Seat (Simulates clicking seat button in pure HTML)
        MvcResult holdResult = mockMvc.perform(post("/events/" + testEvent.getId() + "/seats/" + testSeat.getId() + "/hold")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String redirectedConfirmUrl = holdResult.getResponse().getRedirectedUrl();
        assertThat(redirectedConfirmUrl).startsWith("/reservations/").endsWith("/confirm");

        // Step 8: View Confirmation Ticket Stub
        mockMvc.perform(get(redirectedConfirmUrl).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Official Conference Pass")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Confirm &amp; Finalize Booking")));

        // Extract reservation ID from URL
        String resIdStr = redirectedConfirmUrl.replace("/reservations/", "").replace("/confirm", "");
        Long reservationId = Long.parseLong(resIdStr);

        // Step 9: Form POST to Confirm Reservation
        mockMvc.perform(post("/reservations/" + reservationId + "/confirm")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reservations/my"));

        // Step 10: View My Reservations (Ticket confirmed)
        mockMvc.perform(get("/reservations/my").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("CONFIRMED TICKET")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(testEvent.getName())));

        // Step 11: Re-visit Seat Map -> Seat must now be reflected as BOOKED
        mockMvc.perform(get("/sessions/" + testSession.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("seat-booked")));

        // Database verification
        Reservation dbReservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(dbReservation.getStatus()).isEqualTo(Reservation.Status.CONFIRMED);

        Seat dbSeat = seatRepository.findById(testSeat.getId()).orElseThrow();
        assertThat(dbSeat.getStatus()).isEqualTo(Seat.SeatStatus.BOOKED);
    }

    @Test
    @DisplayName("Reservation ownership: Customer B cannot view or confirm Customer A's held reservation")
    void reservationOwnership_crossUserAttack_isBlocked() throws Exception {
        // Customer A holds a seat
        registrationService.registerForEvent(testEvent.getId(), customer.getId());
        Seat seatForHold = seatRepository.save(Seat.builder()
                .session(testSession)
                .section("B")
                .rowLabel("1")
                .seatNumber(99)
                .status(Seat.SeatStatus.AVAILABLE)
                .build());
        Reservation holdA = reservationService.holdSeat(seatForHold.getId(), customer.getId());

        // Create Customer B
        User customerB = userRepository.findByEmail("customerB@seatreserve.dev")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("customerB@seatreserve.dev")
                        .passwordHash(passwordEncoder.encode("password123"))
                        .role(User.Role.CUSTOMER)
                        .build()));
        AppUserDetails customerBDetails = AppUserDetails.from(customerB);

        // 1. Customer B attempts GET /reservations/{id}/confirm -> redirected with error
        mockMvc.perform(get("/reservations/" + holdA.getId() + "/confirm")
                        .with(user(customerBDetails)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reservations/my"))
                .andExpect(flash().attribute("errorMessage", org.hamcrest.Matchers.containsString("Access denied")));

        // 2. Customer B attempts POST /reservations/{id}/confirm -> blocked and redirected with error
        mockMvc.perform(post("/reservations/" + holdA.getId() + "/confirm")
                        .with(user(customerBDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reservations/my"))
                .andExpect(flash().attribute("errorMessage", org.hamcrest.Matchers.containsString("Access denied")));

        // 3. Customer B attempts REST API POST /api/v1/reservations/{id}/confirm -> 403 Forbidden RFC 9457
        mockMvc.perform(post("/api/v1/reservations/" + holdA.getId() + "/confirm")
                        .header("Idempotency-Key", "attack-key-" + UUID.randomUUID())
                        .with(user(customerBDetails))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Access denied"));

        // Verify Customer A's reservation is still PENDING and seat is still HELD
        Reservation currentRes = reservationRepository.findById(holdA.getId()).orElseThrow();
        assertThat(currentRes.getStatus()).isEqualTo(Reservation.Status.PENDING);
        assertThat(currentRes.getUser().getId()).isEqualTo(customer.getId());

        Seat currentSeat = seatRepository.findById(seatForHold.getId()).orElseThrow();
        assertThat(currentSeat.getStatus()).isEqualTo(Seat.SeatStatus.HELD);
    }
}
