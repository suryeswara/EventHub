package com.seatreserve.common.event;

import java.time.Instant;

/**
 * Domain event published when an expired hold is swept and released.
 * Plain Java record with no framework dependencies.
 */
public record HoldExpiredEvent(
        Long reservationId,
        Long userId,
        Long seatId,
        String seatLabel,
        String sessionTitle,
        Instant expiredAt
) {}
