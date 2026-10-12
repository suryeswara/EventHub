package com.seatreserve.common.event;

import java.time.Instant;

/**
 * Domain event published when an attendee registers for an event.
 * Plain Java record with no framework dependencies.
 */
public record RegistrationCreatedEvent(
        Long registrationId,
        Long userId,
        Long eventId,
        String eventName,
        Instant registeredAt
) {}
