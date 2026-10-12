package com.seatreserve.common.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain event published when a payment fails or is declined by the gateway.
 * Plain Java record with no framework dependencies.
 */
public record PaymentFailedEvent(
        Long paymentId,
        Long reservationId,
        Long userId,
        BigDecimal amount,
        String currency,
        String reason,
        Instant failedAt
) {}
