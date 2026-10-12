package com.seatreserve.common.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain event published when a payment is processed successfully.
 * Plain Java record with no framework dependencies.
 */
public record PaymentSucceededEvent(
        Long paymentId,
        Long reservationId,
        Long userId,
        String userEmail,
        BigDecimal amount,
        String currency,
        String eventName,
        String sessionTitle,
        String seatLabel,
        Instant completedAt
) {}
