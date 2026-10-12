package com.seatreserve.notification;

import com.seatreserve.common.event.*;
import com.seatreserve.user.User;
import com.seatreserve.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

/**
 * Event-driven notification listener.
 * Listens to domain events published AFTER_COMMIT and writes durable Notification records.
 * Uses REQUIRES_NEW propagation because AFTER_COMMIT runs outside the originating transaction.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationListener {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSeatHeld(SeatHeldEvent event) {
        log.info("NotificationListener handling SeatHeldEvent for user={}", event.userId());
        createNotification(
                event.userId(),
                "SEAT_HELD",
                String.format("Seat %s for '%s' is held for you until %s. Complete checkout to secure your ticket.",
                        event.seatLabel(), event.sessionTitle(), event.expiresAt())
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRegistrationCreated(RegistrationCreatedEvent event) {
        log.info("NotificationListener handling RegistrationCreatedEvent for user={}", event.userId());
        createNotification(
                event.userId(),
                "REGISTRATION_CREATED",
                String.format("You are now registered for event '%s'. You may now select and reserve session seats.",
                        event.eventName())
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        log.info("NotificationListener handling PaymentSucceededEvent for user={}", event.userId());
        createNotification(
                event.userId(),
                "PAYMENT_SUCCEEDED",
                String.format("Payment of %s %s succeeded! Your ticket for '%s' (Seat %s) is confirmed.",
                        event.amount(), event.currency(), event.sessionTitle(), event.seatLabel())
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.info("NotificationListener handling PaymentFailedEvent for user={}", event.userId());
        createNotification(
                event.userId(),
                "PAYMENT_FAILED",
                String.format("Payment of %s %s failed (%s). Your seat is still held temporarily. Please retry checkout.",
                        event.amount(), event.currency(), event.reason())
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReservationConfirmed(ReservationConfirmedEvent event) {
        log.info("NotificationListener handling ReservationConfirmedEvent for user={}", event.userId());
        createNotification(
                event.userId(),
                "RESERVATION_CONFIRMED",
                String.format("Reservation #%d confirmed: Seat %s for '%s'.",
                        event.reservationId(), event.seatLabel(), event.sessionTitle())
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onHoldExpired(HoldExpiredEvent event) {
        log.info("NotificationListener handling HoldExpiredEvent for user={}", event.userId());
        createNotification(
                event.userId(),
                "HOLD_EXPIRED",
                String.format("Your hold on seat %s for '%s' has expired and the seat was released.",
                        event.seatLabel(), event.sessionTitle())
        );
    }

    private void createNotification(Long userId, String type, String message) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("Cannot create notification for non-existent userId={}", userId);
            return;
        }

        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .message(message)
                .createdAt(Instant.now())
                .read(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("Notification saved: id={}, userId={}, type={}", saved.getId(), userId, type);
    }
}
