package com.seatreserve.payment;

import com.seatreserve.common.HoldExpiredException;
import com.seatreserve.common.SeatConflictException;
import com.seatreserve.common.UnauthorizedReservationException;
import com.seatreserve.common.event.PaymentFailedEvent;
import com.seatreserve.common.event.PaymentSucceededEvent;
import com.seatreserve.payment.PaymentDtos.PaymentResponse;
import com.seatreserve.reservation.Reservation;
import com.seatreserve.reservation.ReservationRepository;
import com.seatreserve.reservation.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates payment processing gating the hold -> confirm state transition.
 *
 * CRITICAL ARCHITECTURAL CONSTRAINTS:
 * 1. PaymentGatewayService.processPayment(...) MUST run OUTSIDE any pessimistic DB lock
 *    boundary. Do not hold findByIdForUpdate's lock while calling the gateway.
 * 2. ReservationService.confirm() is only called AFTER the payment gateway succeeds.
 * 3. On gateway failure, the reservation remains PENDING so the background hold-expiry
 *    sweep can clean it up if the user does not retry in time.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;
    private final PaymentGatewayService paymentGatewayService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Gated payment initiation:
     * 1. Validates reservation ownership and PENDING status
     * 2. Checks expiry
     * 3. Creates/resets PENDING Payment record
     * 4. Calls simulated PaymentGatewayService OUTSIDE of any database lock
     * 5. If gateway succeeds -> Marks SUCCEEDED, confirms reservation & seat, publishes PaymentSucceededEvent
     * 6. If gateway fails -> Marks FAILED, leaves reservation PENDING, publishes PaymentFailedEvent
     */
    public PaymentResponse initiatePayment(Long reservationId, Long userId, String idempotencyKey) {
        String finalKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey
                : "pay_" + reservationId + "_" + UUID.randomUUID();

        // Step 1 & 2: Validate reservation and persist pending payment in an isolated transaction
        Payment pendingPayment = createOrUpdatePendingPayment(reservationId, userId, finalKey);

        // If reservation/payment was already confirmed, return idempotently
        if (pendingPayment.getStatus() == Payment.Status.SUCCEEDED) {
            log.info("Idempotent payment return: paymentId={}, reservationId={}",
                    pendingPayment.getId(), reservationId);
            return PaymentResponse.from(pendingPayment);
        }

        // Step 3: Run external gateway call OUTSIDE any DB transaction or pessimistic lock
        log.info("Executing external payment gateway outside DB locks: paymentId={}, reservationId={}",
                pendingPayment.getId(), reservationId);
        boolean gatewaySuccess = paymentGatewayService.processPayment(pendingPayment);

        // Step 4: Handle outcome in isolated transactional boundaries
        if (gatewaySuccess) {
            Payment succeeded = handlePaymentSuccess(pendingPayment.getId(), reservationId, userId, finalKey);
            return PaymentResponse.from(succeeded);
        } else {
            Payment failed = handlePaymentFailure(pendingPayment.getId(), reservationId, userId,
                    "Simulated payment gateway declined transaction");
            return PaymentResponse.from(failed);
        }
    }

    public PaymentResponse initiatePayment(Long reservationId, Long userId) {
        return initiatePayment(reservationId, userId, null);
    }

    @Transactional
    public Payment createOrUpdatePendingPayment(Long reservationId, Long userId, String idempotencyKey) {
        Reservation reservation = reservationRepository.findByIdWithDetails(reservationId)
                .orElseThrow(() -> new NoSuchElementException("Reservation not found: " + reservationId));

        // Ownership verification
        if (userId != null && reservation.getUser() != null && !reservation.getUser().getId().equals(userId)) {
            log.warn("Payment initiation denied: user {} is not owner of reservation {} (owned by {})",
                    userId, reservationId, reservation.getUser().getId());
            throw new UnauthorizedReservationException("User " + userId + " is not the owner of reservation " + reservationId);
        }

        // Check if reservation is already confirmed
        if (reservation.getStatus() == Reservation.Status.CONFIRMED) {
            Optional<Payment> existingPayment = paymentRepository.findByReservationId(reservationId);
            if (existingPayment.isPresent()) {
                return existingPayment.get();
            }
        }

        if (reservation.getStatus() != Reservation.Status.PENDING) {
            throw new SeatConflictException("Reservation " + reservationId + " is not pending (status: " + reservation.getStatus() + ")");
        }

        // Check hold expiry
        if (Instant.now().isAfter(reservation.getExpiresAt())) {
            log.info("Hold expired during payment initiation: reservationId={}, seatId={}",
                    reservationId, reservation.getSeat().getId());
            reservationService.expireReservationAndReleaseSeat(reservationId);
            throw new HoldExpiredException("Hold for reservation " + reservationId + " has expired");
        }

        BigDecimal sessionPrice = BigDecimal.ZERO;
        if (reservation.getSeat() != null && reservation.getSeat().getSession() != null) {
            BigDecimal price = reservation.getSeat().getSession().getPrice();
            if (price != null) {
                sessionPrice = price;
            }
        }

        Optional<Payment> existingPaymentOpt = paymentRepository.findByReservationId(reservationId);
        Payment payment;
        if (existingPaymentOpt.isPresent()) {
            payment = existingPaymentOpt.get();
            if (payment.getStatus() == Payment.Status.SUCCEEDED) {
                return payment;
            }
            // Retrying payment: reset status to PENDING
            payment.setStatus(Payment.Status.PENDING);
            payment.setIdempotencyKey(idempotencyKey);
            payment.setCompletedAt(null);
            payment.setAmount(sessionPrice);
        } else {
            payment = Payment.builder()
                    .reservation(reservation)
                    .amount(sessionPrice)
                    .currency("USD")
                    .status(Payment.Status.PENDING)
                    .idempotencyKey(idempotencyKey)
                    .createdAt(Instant.now())
                    .build();
        }

        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment handlePaymentSuccess(Long paymentId, Long reservationId, Long userId, String idempotencyKey) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NoSuchElementException("Payment not found: " + paymentId));

        payment.setStatus(Payment.Status.SUCCEEDED);
        payment.setCompletedAt(Instant.now());
        Payment savedPayment = paymentRepository.save(payment);

        // Confirm reservation and lock seat status -> BOOKED
        Reservation confirmedReservation = reservationService.confirm(reservationId, idempotencyKey, userId);
        Reservation resWithDetails = reservationRepository.findByIdWithDetails(reservationId).orElse(confirmedReservation);

        String eventName = "Conference Event";
        String sessionTitle = "Session";
        String seatLabel = "Assigned Seat";
        String userEmail = (resWithDetails.getUser() != null && resWithDetails.getUser().getEmail() != null)
                ? resWithDetails.getUser().getEmail()
                : "customer@example.com";

        if (resWithDetails.getSeat() != null) {
            var seat = resWithDetails.getSeat();
            seatLabel = seat.getSection() + "-" + seat.getRowLabel() + "-" + seat.getSeatNumber();
            if (seat.getSession() != null) {
                sessionTitle = seat.getSession().getTitle();
                if (seat.getSession().getEvent() != null) {
                    eventName = seat.getSession().getEvent().getName();
                }
            }
        }

        // Publish PaymentSucceededEvent
        PaymentSucceededEvent succeededEvent = new PaymentSucceededEvent(
                savedPayment.getId(),
                reservationId,
                userId != null ? userId : confirmedReservation.getUser().getId(),
                userEmail,
                savedPayment.getAmount(),
                savedPayment.getCurrency(),
                eventName,
                sessionTitle,
                seatLabel,
                savedPayment.getCompletedAt()
        );
        eventPublisher.publishEvent(succeededEvent);
        log.info("Payment succeeded: paymentId={}, reservationId={}, amount={}",
                savedPayment.getId(), reservationId, savedPayment.getAmount());

        return savedPayment;
    }

    @Transactional
    public Payment handlePaymentFailure(Long paymentId, Long reservationId, Long userId, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NoSuchElementException("Payment not found: " + paymentId));

        payment.setStatus(Payment.Status.FAILED);
        payment.setCompletedAt(Instant.now());
        Payment savedPayment = paymentRepository.save(payment);

        PaymentFailedEvent failedEvent = new PaymentFailedEvent(
                savedPayment.getId(),
                reservationId,
                userId,
                savedPayment.getAmount(),
                savedPayment.getCurrency(),
                reason,
                savedPayment.getCompletedAt()
        );
        eventPublisher.publishEvent(failedEvent);
        log.warn("Payment failed: paymentId={}, reservationId={}, reason={}",
                savedPayment.getId(), reservationId, reason);

        return savedPayment;
    }

    @Transactional(readOnly = true)
    public Optional<PaymentResponse> getPaymentByReservationId(Long reservationId, Long userId) {
        return paymentRepository.findByReservationId(reservationId).map(payment -> {
            if (userId != null && payment.getReservation() != null
                    && payment.getReservation().getUser() != null
                    && !payment.getReservation().getUser().getId().equals(userId)) {
                throw new UnauthorizedReservationException("User " + userId + " is not the owner of reservation " + reservationId);
            }
            return PaymentResponse.from(payment);
        });
    }
}
