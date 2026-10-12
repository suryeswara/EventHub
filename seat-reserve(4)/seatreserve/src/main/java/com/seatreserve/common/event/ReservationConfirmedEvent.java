package com.seatreserve.common.event;

import java.time.Instant;

/**
 * Domain event published when a reservation is confirmed and seat is booked.
 * Plain Java record with no framework dependencies.
 */
public record ReservationConfirmedEvent(
        Long reservationId,
        Long userId,
        Long seatId,
        String seatLabel,
        String sessionTitle,
        Instant confirmedAt
) {}
