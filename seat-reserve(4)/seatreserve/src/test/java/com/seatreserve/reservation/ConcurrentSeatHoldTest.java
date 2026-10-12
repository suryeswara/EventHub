package com.seatreserve.reservation;

import com.seatreserve.common.SeatConflictException;
import com.seatreserve.event.Event;
import com.seatreserve.event.EventRepository;
import com.seatreserve.session.Session;
import com.seatreserve.session.SessionRepository;
import com.seatreserve.seat.Seat;
import com.seatreserve.seat.SeatRepository;
import com.seatreserve.user.User;
import com.seatreserve.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;


import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * THE test this whole project is built to prove.
 *
 * Scenario: 20 threads simultaneously attempt to hold the SAME seat.
 * Assertion: exactly ONE succeeds; the other 19 receive SeatConflictException
 * (which the controller maps to HTTP 409), and the seat ends in a single
 * consistent HELD reservation with no duplicate rows.
 *
 * Run against a real Postgres via Testcontainers -- not H2 -- because
 * H2's locking semantics do not reliably reproduce Postgres row-lock
 * behavior under `SELECT ... FOR UPDATE`.
 */
@SpringBootTest
@DirtiesContext
class ConcurrentSeatHoldTest {

    @DynamicPropertySource
    static void configureProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5433/seatreserve_test");
        registry.add("spring.datasource.username", () -> "seatreserve");
        registry.add("spring.datasource.password", () -> "seatreserve");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "25");
    }

    @Autowired private ReservationService reservationService;
    @Autowired private SeatRepository seatRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private com.seatreserve.registration.AttendeeRegistrationRepository attendeeRegistrationRepository;

    private Long seatId;
    private List<Long> userIds;

    private static final int CONCURRENT_ATTEMPTS = 20;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        attendeeRegistrationRepository.deleteAll();
        seatRepository.deleteAll();
        sessionRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();

        User organizer = userRepository.save(User.builder()
                .email("organizer@test.com").passwordHash("x").role(User.Role.ORGANIZER)
                .build());

        Event event = eventRepository.save(Event.builder()
                .organizer(organizer).name("Test Concert").venue("Test Arena")
                .startTime(Instant.now().plusSeconds(86400))
                .build());

        Session session = sessionRepository.save(Session.builder()
                .event(event).title("Main Keynote").description("Opening keynote")
                .startTime(event.getStartTime())
                .endTime(event.getStartTime().plusSeconds(3600))
                .capacity(100)
                .build());

        Seat seat = seatRepository.save(Seat.builder()
                .session(session).section("A").rowLabel("1").seatNumber(14)
                .status(Seat.SeatStatus.AVAILABLE)
                .build());
        seatId = seat.getId();

        userIds = IntStreamRange(CONCURRENT_ATTEMPTS)
                .mapToObj(i -> userRepository.save(User.builder()
                        .email("customer" + i + "@test.com").passwordHash("x").role(User.Role.CUSTOMER)
                        .build()).getId())
                .toList();

        // Register each test attendee for the event so they satisfy the business-rule prerequisite
        for (Long userId : userIds) {
            attendeeRegistrationRepository.save(com.seatreserve.registration.AttendeeRegistration.builder()
                    .event(event)
                    .user(userRepository.getReferenceById(userId))
                    .build());
        }
    }

    @Test
    void exactlyOneHoldSucceedsUnderConcurrentAttempts() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_ATTEMPTS);
        CountDownLatch startLine = new CountDownLatch(1);
        CountDownLatch finishLine = new CountDownLatch(CONCURRENT_ATTEMPTS);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (Long userId : userIds) {
            executor.submit(() -> {
                try {
                    startLine.await(); // all threads fire as close to simultaneously as possible
                    reservationService.holdSeat(seatId, userId);
                    successCount.incrementAndGet();
                } catch (SeatConflictException e) {
                    conflictCount.incrementAndGet();
                } catch (Throwable e) {
                    System.err.println("THREAD ERROR: " + e.getClass().getName() + " -> " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    finishLine.countDown();
                }
            });
        }

        startLine.countDown(); // release all threads at once
        finishLine.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.printf("[CONCURRENCY_RESULT] ConcurrentSeatHoldTest: %d success, %d conflicts across %d threads%n",
                successCount.get(), conflictCount.get(), CONCURRENT_ATTEMPTS);

        // The core assertion: no matter how many threads raced, exactly
        // one hold succeeded and the rest were correctly rejected.
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(CONCURRENT_ATTEMPTS - 1);

        Seat finalSeat = seatRepository.findById(seatId).orElseThrow();
        assertThat(finalSeat.getStatus()).isEqualTo(Seat.SeatStatus.HELD);

        long pendingReservations = reservationRepository.findAll().stream()
                .filter(r -> r.getSeat().getId().equals(seatId))
                .filter(r -> r.getStatus() == Reservation.Status.PENDING)
                .count();
        assertThat(pendingReservations).isEqualTo(1);
    }

    private static java.util.stream.IntStream IntStreamRange(int n) {
        return java.util.stream.IntStream.range(0, n);
    }
}
