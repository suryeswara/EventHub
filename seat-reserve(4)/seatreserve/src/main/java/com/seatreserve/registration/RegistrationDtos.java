package com.seatreserve.registration;

import java.time.Instant;

public class RegistrationDtos {

    public record RegisterRequest() {}

    public record RegistrationResponse(
            Long id,
            Long eventId,
            Long userId,
            Instant registeredAt
    ) {
        public static RegistrationResponse from(AttendeeRegistration r) {
            return new RegistrationResponse(
                    r.getId(),
                    r.getEvent().getId(),
                    r.getUser().getId(),
                    r.getRegisteredAt()
            );
        }
    }
}
