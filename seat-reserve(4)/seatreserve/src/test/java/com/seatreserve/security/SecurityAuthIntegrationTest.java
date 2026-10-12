package com.seatreserve.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatreserve.user.User;
import com.seatreserve.user.UserDtos.RegisterUserRequest;
import com.seatreserve.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityAuthIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @org.junit.jupiter.api.BeforeEach
    void ensureSeededUsersExist() {
        userRepository.findByEmail("organizer@seatreserve.dev").ifPresentOrElse(
                u -> {
                    u.setPasswordHash(passwordEncoder.encode("password123"));
                    userRepository.save(u);
                },
                () -> userRepository.save(User.builder()
                        .email("organizer@seatreserve.dev")
                        .passwordHash(passwordEncoder.encode("password123"))
                        .role(User.Role.ORGANIZER)
                        .build())
        );
        userRepository.findByEmail("customer@seatreserve.dev").ifPresentOrElse(
                u -> {
                    u.setPasswordHash(passwordEncoder.encode("password123"));
                    userRepository.save(u);
                },
                () -> userRepository.save(User.builder()
                        .email("customer@seatreserve.dev")
                        .passwordHash(passwordEncoder.encode("password123"))
                        .role(User.Role.CUSTOMER)
                        .build())
        );
    }

    @Test
    @DisplayName("Seeded organizer can log in via form login with password123")
    void formLogin_succeeds_withSeededOrganizerCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .param("username", "organizer@seatreserve.dev")
                        .param("password", "password123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    @DisplayName("Form login with incorrect password returns 401 RFC 9457 ProblemDetail")
    void formLogin_fails_withInvalidPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .param("username", "organizer@seatreserve.dev")
                        .param("password", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://api.seatreserve.com/errors/unauthorized"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Invalid credentials"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register creates a new user with BCrypt hashed password")
    void registerUser_createsUserWithHashedPassword() throws Exception {
        String email = "newuser" + System.currentTimeMillis() + "@test.com";
        RegisterUserRequest request = new RegisterUserRequest(email, "secretPassword", User.Role.CUSTOMER);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        User savedUser = userRepository.findByEmail(email).orElseThrow();
        assertThat(savedUser.getPasswordHash()).isNotEqualTo("secretPassword");
        assertThat(passwordEncoder.matches("secretPassword", savedUser.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("Anonymous request to protected CUSTOMER endpoint returns 401 ProblemDetail")
    void anonymousRequest_toCustomerEndpoint_returns401ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/events/1/register")
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://api.seatreserve.com/errors/unauthorized"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("CUSTOMER role attempting ORGANIZER endpoint returns 403 ProblemDetail")
    void customerRole_attemptingOrganizerEndpoint_returns403ProblemDetail() throws Exception {
        AppUserDetails customer = new AppUserDetails(100L, "customer@seatreserve.dev", "hash", User.Role.CUSTOMER);

        mockMvc.perform(post("/api/v1/events")
                        .with(csrf())
                        .with(user(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Unauthorized Event",
                                    "venue": "Hall",
                                    "startTime": "2026-12-01T10:00:00Z"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://api.seatreserve.com/errors/forbidden"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("CSRF protection rejects state-changing POST without CSRF token with 403 Forbidden")
    void postWithoutCsrf_returns403Forbidden() throws Exception {
        AppUserDetails customer = new AppUserDetails(100L, "customer@seatreserve.dev", "hash", User.Role.CUSTOMER);

        mockMvc.perform(post("/api/v1/events/1/register")
                        .with(user(customer))
                        // explicitly NO csrf() token
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Public GET endpoints for browsing events and sessions are accessible anonymously")
    void publicEndpoints_accessibleAnonymously() throws Exception {
        mockMvc.perform(get("/api/v1/events"))
                .andExpect(status().isOk());
    }
}
