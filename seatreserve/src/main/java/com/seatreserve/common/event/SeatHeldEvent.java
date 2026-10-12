package com.seatreserve.common.event;

import java.time.Instant;

/**
 * Domain event published when a seat hold is successfully acquired.
 * Plain Java record with no framework dependencies.
 */
public record SeatHeldEvent(
        Long reservationId,
        Long userId,
        Long seatId,
        String seatLabel,
        String sessionTitle,
        Instant heldAt,
        Instant expiresAt
) {}
