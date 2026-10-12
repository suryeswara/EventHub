package com.seatreserve.scheduling;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.seatreserve.event.Event;
import com.seatreserve.event.EventRepository;
import com.seatreserve.reservation.Reservation;
import com.seatreserve.reservation.ReservationRepository;
import com.seatreserve.seat.Seat;
import com.seatreserve.seat.SeatRepository;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class HoldExpirySweepIntegrationTest {

    @DynamicPropertySource
    static void configureProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5433/seatreserve_test");
        registry.add("spring.datasource.username", () -> "seatreserve");
        registry.add("spring.datasource.password", () -> "seatreserve");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "10");
        registry.add("reservation.sweep-interval-ms", () -> "60000");
    }

    @Autowired
    private HoldExpirySweepService sweepService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger rootLogger;

    private User testUser;
    private Event testEvent;
    private Session testSession;

    @BeforeEach
    void setUp() {
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        listAppender = new ListAppender<>();
        listAppender.start();
        rootLogger.addAppender(listAppender);

        testUser = userRepository.findByEmail("sweep_test_user@seatreserve.dev")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("sweep_test_user@seatreserve.dev")
                        .passwordHash(passwordEncoder.encode("Pass123!"))
                        .role(User.Role.CUSTOMER)
                        .build()));

        User organizer = userRepository.findByEmail("organizer@seatreserve.dev")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("organizer@seatreserve.dev")
                        .passwordHash(passwordEncoder.encode("Pass123!"))
                        .role(User.Role.ORGANIZER)
                        .build()));

        testEvent = eventRepository.findAll().stream().findFirst().orElseGet(() ->
                eventRepository.save(Event.builder()
                        .name("Sweep Test Conference")
                        .venue("Sweep Hall")
                        .startTime(Instant.now().plusSeconds(86400))
                        .organizer(organizer)
                        .build())
        );

        testSession = sessionRepository.save(Session.builder()
                .event(testEvent)
                .title("Sweep Session " + UUID.randomUUID())
                .startTime(Instant.now().plusSeconds(3600))
                .endTime(Instant.now().plusSeconds(7200))
                .capacity(10)
                .build());
    }

    @AfterEach
    void tearDown() {
        if (rootLogger != null && listAppender != null) {
            rootLogger.detachAppender(listAppender);
        }
    }

    @Test
    @DisplayName("HoldExpirySweepService expires past-due PENDING holds and releases seats back to AVAILABLE")
    void sweepExpiresBackdatedHoldAndReleasesSeat() {
        // 1. Create a seat marked HELD
        Seat seat = seatRepository.save(Seat.builder()
                .session(testSession)
                .section("B")
                .rowLabel("1")
                .seatNumber(10)
                .status(Seat.SeatStatus.HELD)
                .build());

        // 2. Create a reservation that is backdated (expiresAt in the past)
        Instant heldAt = Instant.now().minusSeconds(600); // 10 minutes ago
        Instant expiresAt = Instant.now().minusSeconds(300); // 5 minutes ago
        Reservation reservation = reservationRepository.save(Reservation.builder()
                .seat(seat)
                .user(testUser)
                .status(Reservation.Status.PENDING)
                .idempotencyKey("sweep-test-" + UUID.randomUUID())
                .heldAt(heldAt)
                .expiresAt(expiresAt)
                .build());

        assertThat(reservation.getStatus()).isEqualTo(Reservation.Status.PENDING);
        assertThat(seat.getStatus()).isEqualTo(Seat.SeatStatus.HELD);

        // 3. Manually trigger the sweep job
        sweepService.sweepExpiredHolds();

        // 4. Assert reservation status flipped to EXPIRED
        Reservation updatedReservation = reservationRepository.findById(reservation.getId())
                .orElseThrow();
        assertThat(updatedReservation.getStatus()).isEqualTo(Reservation.Status.EXPIRED);

        // 5. Assert seat status flipped back to AVAILABLE via releaseSeat()
        Seat updatedSeat = seatRepository.findById(seat.getId())
                .orElseThrow();
        assertThat(updatedSeat.getStatus()).isEqualTo(Seat.SeatStatus.AVAILABLE);

        // 6. Assert structured audit log was recorded
        boolean hasAuditLog = listAppender.list.stream().anyMatch(event ->
                event.getLevel() == Level.INFO &&
                        event.getFormattedMessage().contains("Hold expired by sweep job") &&
                        event.getFormattedMessage().contains("reservationId=" + reservation.getId()) &&
                        event.getFormattedMessage().contains("seatId=" + seat.getId()));

        assertThat(hasAuditLog).isTrue();
    }
}
