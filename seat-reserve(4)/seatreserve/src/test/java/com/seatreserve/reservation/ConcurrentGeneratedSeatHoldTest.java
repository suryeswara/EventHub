package com.seatreserve.reservation;

import com.seatreserve.common.SeatConflictException;
import com.seatreserve.event.Event;
import com.seatreserve.event.EventRepository;
import com.seatreserve.registration.AttendeeRegistration;
import com.seatreserve.registration.AttendeeRegistrationRepository;
import com.seatreserve.seat.Seat;
import com.seatreserve.seat.SeatRepository;
import com.seatreserve.session.Session;
import com.seatreserve.session.SessionDtos.GenerateSeatsRequest;
import com.seatreserve.session.SessionDtos.GenerateSeatsResponse;
import com.seatreserve.session.SessionDtos.SectionSpec;
import com.seatreserve.session.SessionRepository;
import com.seatreserve.session.SessionService;
import com.seatreserve.user.User;
import com.seatreserve.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext
class ConcurrentGeneratedSeatHoldTest {

    @DynamicPropertySource
    static void configureProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5433/seatreserve_test");
        registry.add("spring.datasource.username", () -> "seatreserve");
        registry.add("spring.datasource.password", () -> "seatreserve");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "25");
    }

    @Autowired private ReservationService reservationService;
    @Autowired private SessionService sessionService;
    @Autowired private SeatRepository seatRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private AttendeeRegistrationRepository attendeeRegistrationRepository;

    private Long generatedSeatId;
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
                .email("organizer-gen@test.com").passwordHash("x").role(User.Role.ORGANIZER)
                .build());

        Event event = eventRepository.save(Event.builder()
                .organizer(organizer).name("Generated Seat Event").venue("Tech Pavilion")
                .startTime(Instant.now().plusSeconds(86400))
                .build());

        // 1. Create session with small capacity of 5
        Session session = sessionRepository.save(Session.builder()
                .event(event).title("Hands-On Micro-Lab").description("Intensive 5-person lab")
                .startTime(event.getStartTime())
                .endTime(event.getStartTime().plusSeconds(3600))
                .capacity(5)
                .build());

        // 2. Generate seats dynamically via SessionService.generateSeats (1 row x 5 seats = 5)
        GenerateSeatsResponse genResponse = sessionService.generateSeats(
                session.getId(),
                new GenerateSeatsRequest(List.of(new SectionSpec("VIP", 1, 5)))
        );
        assertThat(genResponse.totalSeatsGenerated()).isEqualTo(5);

        // Pick seat #1 of the newly generated seats for the race
        generatedSeatId = genResponse.seats().getFirst().id();

        // 3. Create and register 20 test attendees for the event
        userIds = IntStream.range(0, CONCURRENT_ATTEMPTS)
                .mapToObj(i -> userRepository.save(User.builder()
                        .email("gen-attendee" + i + "@test.com").passwordHash("x").role(User.Role.CUSTOMER)
                        .build()).getId())
                .toList();

        for (Long userId : userIds) {
            attendeeRegistrationRepository.save(AttendeeRegistration.builder()
                    .event(event)
                    .user(userRepository.getReferenceById(userId))
                    .build());
        }
    }

    @Test
    @DisplayName("Pessimistic locking guarantee holds for dynamically generated seats: exactly 1 wins, 19 conflict")
    void dynamicallyGeneratedSeatHoldsExclusivelyUnderConcurrency() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_ATTEMPTS);
        CountDownLatch startLine = new CountDownLatch(1);
        CountDownLatch finishLine = new CountDownLatch(CONCURRENT_ATTEMPTS);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (Long userId : userIds) {
            executor.submit(() -> {
                try {
                    startLine.await();
                    reservationService.holdSeat(generatedSeatId, userId);
                    successCount.incrementAndGet();
                } catch (SeatConflictException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("UNEXPECTED ERROR: " + e.getMessage());
                } finally {
                    finishLine.countDown();
                }
            });
        }

        startLine.countDown();
        boolean completed = finishLine.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.printf("[CONCURRENCY_RESULT] ConcurrentGeneratedSeatHoldTest: %d success, %d conflicts across %d threads%n",
                successCount.get(), conflictCount.get(), CONCURRENT_ATTEMPTS);

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(CONCURRENT_ATTEMPTS - 1);

        Seat seat = seatRepository.findById(generatedSeatId).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(Seat.SeatStatus.HELD);
    }
}
