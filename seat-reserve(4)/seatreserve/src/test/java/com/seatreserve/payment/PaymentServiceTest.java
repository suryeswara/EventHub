package com.seatreserve.payment;

import com.seatreserve.common.HoldExpiredException;
import com.seatreserve.common.UnauthorizedReservationException;
import com.seatreserve.event.Event;
import com.seatreserve.event.EventRepository;
import com.seatreserve.notification.Notification;
import com.seatreserve.notification.NotificationRepository;
import com.seatreserve.registration.AttendeeRegistrationRepository;
import com.seatreserve.registration.RegistrationService;
import com.seatreserve.reservation.Reservation;
import com.seatreserve.reservation.ReservationRepository;
import com.seatreserve.reservation.ReservationService;
import com.seatreserve.seat.Seat;
import com.seatreserve.seat.SeatRepository;
import com.seatreserve.session.Session;
import com.seatreserve.session.SessionRepository;
import com.seatreserve.user.User;
import com.seatreserve.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DirtiesContext
class PaymentServiceTest {

    @DynamicPropertySource
    static void configureProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5433/seatreserve_test");
        registry.add("spring.datasource.username", () -> "seatreserve");
        registry.add("spring.datasource.password", () -> "seatreserve");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "5");
        registry.add("payment.gateway.delay-ms", () -> "0");
        registry.add("payment.gateway.failure-rate", () -> "0.0");
    }

    @Autowired private PaymentService paymentService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentGatewayService paymentGatewayService;
    @Autowired private ReservationService reservationService;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AttendeeRegistrationRepository attendeeRegistrationRepository;
    @Autowired private RegistrationService registrationService;

    private User customer;
    private User otherCustomer;
    private Event event;
    private Session session;
    private Seat seat;

    @BeforeEach
    void setUp() {
        // Clear dependent tables
        notificationRepository.deleteAll();
        paymentRepository.deleteAll();
        reservationRepository.deleteAll();
        attendeeRegistrationRepository.deleteAll();
        seatRepository.deleteAll();
        sessionRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();

        // Seed users
        User organizer = userRepository.save(User.builder()
                .email("org_" + System.currentTimeMillis() + "@test.com")
                .passwordHash("hashed")
                .role(User.Role.ORGANIZER)
                .build());

        customer = userRepository.save(User.builder()
                .email("cust_" + System.currentTimeMillis() + "@test.com")
                .passwordHash("hashed")
                .role(User.Role.CUSTOMER)
                .build());

        otherCustomer = userRepository.save(User.builder()
                .email("other_" + System.currentTimeMillis() + "@test.com")
                .passwordHash("hashed")
                .role(User.Role.CUSTOMER)
                .build());

        // Seed event, session with pricing, and seat
        event = eventRepository.save(Event.builder()
                .name("Cloud Summit 2026")
                .venue("Convention Hall A")
                .organizer(organizer)
                .startTime(Instant.now().plusSeconds(86400))
                .build());

        session = sessionRepository.save(Session.builder()
                .event(event)
                .title("Advanced Reactive Architecture")
                .startTime(Instant.now().plusSeconds(90000))
                .endTime(Instant.now().plusSeconds(93600))
                .capacity(10)
                .price(new BigDecimal("75.00"))
                .build());

        seat = seatRepository.save(Seat.builder()
                .session(session)
                .section("VIP")
                .rowLabel("1")
                .seatNumber(1)
                .status(Seat.SeatStatus.AVAILABLE)
                .build());

        // Register customer for event
        registrationService.registerForEvent(event.getId(), customer.getId());
    }

    @Test
    @DisplayName("Success path: Payment succeeds, confirms reservation, updates seat to BOOKED, and creates notifications")
    void testPaymentSuccessPath() {
        paymentGatewayService.setFailureRate(0.0);

        Reservation hold = reservationService.holdSeat(seat.getId(), customer.getId());
        assertThat(hold.getStatus()).isEqualTo(Reservation.Status.PENDING);

        PaymentDtos.PaymentResponse response = paymentService.initiatePayment(hold.getId(), customer.getId());

        assertThat(response.status()).isEqualTo(Payment.Status.SUCCEEDED);
        assertThat(response.amount()).isEqualByComparingTo("75.00");
        assertThat(response.currency()).isEqualTo("USD");

        Reservation confirmed = reservationRepository.findById(hold.getId()).orElseThrow();
        assertThat(confirmed.getStatus()).isEqualTo(Reservation.Status.CONFIRMED);
        assertThat(confirmed.getConfirmedAt()).isNotNull();

        Seat bookedSeat = seatRepository.findById(seat.getId()).orElseThrow();
        assertThat(bookedSeat.getStatus()).isEqualTo(Seat.SeatStatus.BOOKED);

        Payment savedPayment = paymentRepository.findByReservationId(hold.getId()).orElseThrow();
        assertThat(savedPayment.getStatus()).isEqualTo(Payment.Status.SUCCEEDED);
        assertThat(savedPayment.getCompletedAt()).isNotNull();

        // Verify notifications created
        List<Notification> userNotifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(customer.getId());
        assertThat(userNotifications).extracting(Notification::getType)
                .contains("REGISTRATION_CREATED", "SEAT_HELD", "PAYMENT_SUCCEEDED");
    }

    @Test
    @DisplayName("Failure path: Gateway decline marks payment FAILED, leaves reservation PENDING, seat HELD, and creates notification")
    void testPaymentFailurePath() {
        paymentGatewayService.setFailureRate(1.0); // force gateway decline

        Reservation hold = reservationService.holdSeat(seat.getId(), customer.getId());
        assertThat(hold.getStatus()).isEqualTo(Reservation.Status.PENDING);

        PaymentDtos.PaymentResponse response = paymentService.initiatePayment(hold.getId(), customer.getId());

        assertThat(response.status()).isEqualTo(Payment.Status.FAILED);

        // Reservation must remain PENDING
        Reservation pendingRes = reservationRepository.findById(hold.getId()).orElseThrow();
        assertThat(pendingRes.getStatus()).isEqualTo(Reservation.Status.PENDING);
        assertThat(pendingRes.getConfirmedAt()).isNull();

        // Seat must remain HELD
        Seat heldSeat = seatRepository.findById(seat.getId()).orElseThrow();
        assertThat(heldSeat.getStatus()).isEqualTo(Seat.SeatStatus.HELD);

        // Payment record is FAILED
        Payment failedPayment = paymentRepository.findByReservationId(hold.getId()).orElseThrow();
        assertThat(failedPayment.getStatus()).isEqualTo(Payment.Status.FAILED);

        // Verify notifications include PAYMENT_FAILED
        List<Notification> userNotifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(customer.getId());
        assertThat(userNotifications).extracting(Notification::getType)
                .contains("PAYMENT_FAILED");
    }

    @Test
    @DisplayName("Retry after failure: Customer retries payment before expiry and succeeds on second attempt")
    void testPaymentRetryAfterFailureSucceeds() {
        // Attempt 1: Fails
        paymentGatewayService.setFailureRate(1.0);
        Reservation hold = reservationService.holdSeat(seat.getId(), customer.getId());

        PaymentDtos.PaymentResponse firstAttempt = paymentService.initiatePayment(hold.getId(), customer.getId());
        assertThat(firstAttempt.status()).isEqualTo(Payment.Status.FAILED);

        // Verify reservation still PENDING
        Reservation stillPending = reservationRepository.findById(hold.getId()).orElseThrow();
        assertThat(stillPending.getStatus()).isEqualTo(Reservation.Status.PENDING);

        // Attempt 2: Succeeds
        paymentGatewayService.setFailureRate(0.0);
        PaymentDtos.PaymentResponse secondAttempt = paymentService.initiatePayment(hold.getId(), customer.getId());
        assertThat(secondAttempt.status()).isEqualTo(Payment.Status.SUCCEEDED);

        Reservation confirmed = reservationRepository.findById(hold.getId()).orElseThrow();
        assertThat(confirmed.getStatus()).isEqualTo(Reservation.Status.CONFIRMED);

        Seat bookedSeat = seatRepository.findById(seat.getId()).orElseThrow();
        assertThat(bookedSeat.getStatus()).isEqualTo(Seat.SeatStatus.BOOKED);

        // Exactly one payment row exists for this reservation (satisfying UNIQUE reservation_id)
        List<Payment> payments = paymentRepository.findAll().stream()
                .filter(p -> p.getReservation().getId().equals(hold.getId()))
                .toList();
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getStatus()).isEqualTo(Payment.Status.SUCCEEDED);
    }

    @Test
    @DisplayName("Ownership check: User cannot initiate payment for another user's reservation")
    void testPaymentOwnershipEnforced() {
        Reservation hold = reservationService.holdSeat(seat.getId(), customer.getId());

        assertThatThrownBy(() -> paymentService.initiatePayment(hold.getId(), otherCustomer.getId()))
                .isInstanceOf(UnauthorizedReservationException.class)
                .hasMessageContaining("not the owner of reservation");
    }

    @Test
    @DisplayName("Expired hold check: Payment initiation on expired reservation throws HoldExpiredException and releases seat")
    void testPaymentOnExpiredHoldThrowsAndReleasesSeat() {
        Reservation hold = reservationService.holdSeat(seat.getId(), customer.getId());

        // Fast-forward expiration in DB
        hold.setExpiresAt(Instant.now().minusSeconds(10));
        reservationRepository.save(hold);

        assertThatThrownBy(() -> paymentService.initiatePayment(hold.getId(), customer.getId()))
                .isInstanceOf(HoldExpiredException.class);

        // Seat must have been released back to AVAILABLE
        Seat releasedSeat = seatRepository.findById(seat.getId()).orElseThrow();
        assertThat(releasedSeat.getStatus()).isEqualTo(Seat.SeatStatus.AVAILABLE);
    }

    @Autowired private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("Lock boundary proof: Payment gateway processing does NOT hold seat row lock during processing delay")
    void testPaymentDoesNotHoldSeatLockDuringGatewayDelay() throws Exception {
        paymentGatewayService.setFailureRate(0.0);
        paymentGatewayService.setDelayMs(800); // 800ms gateway delay active

        try {
            Reservation hold = reservationService.holdSeat(seat.getId(), customer.getId());

            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch gatewayStarted = new CountDownLatch(1);

            Future<PaymentDtos.PaymentResponse> paymentFuture = executor.submit(() -> {
                gatewayStarted.countDown();
                return paymentService.initiatePayment(hold.getId(), customer.getId());
            });

            // Ensure background thread has entered initiatePayment and is inside the gateway delay
            gatewayStarted.await(2, TimeUnit.SECONDS);
            Thread.sleep(150); // Allow thread to reach the gateway sleep

            // Concurrent probe thread: Must attempt a PESSIMISTIC_WRITE lock (SELECT ... FOR UPDATE).
            // A plain read in Postgres MVCC never blocks on a writer, but findByIdForUpdate() WILL block
            // if an exclusive row lock is held. We verify it acquires immediately without blocking.
            long startTime = System.currentTimeMillis();
            Seat lockedSeat = transactionTemplate.execute(status ->
                    seatRepository.findByIdForUpdate(seat.getId()).orElseThrow()
            );
            long probeDuration = System.currentTimeMillis() - startTime;

            // Probe with findByIdForUpdate must complete fast (< 400ms), well before the 800ms gateway completes
            assertThat(lockedSeat).isNotNull();
            assertThat(probeDuration).isLessThan(400);

            PaymentDtos.PaymentResponse response = paymentFuture.get(5, TimeUnit.SECONDS);
            assertThat(response.status()).isEqualTo(Payment.Status.SUCCEEDED);

            executor.shutdown();
        } finally {
            paymentGatewayService.setDelayMs(0); // Reset delay
        }
    }
}
