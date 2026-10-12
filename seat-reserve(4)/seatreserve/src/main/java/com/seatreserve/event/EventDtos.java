package com.seatreserve.event;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public class EventDtos {

    public record CreateEventRequest(
            @NotBlank(message = "Event name is required")
            String name,

            @NotBlank(message = "Venue is required")
            String venue,

            @NotNull(message = "Start time is required")
            @Future(message = "Start time must be in the future")
            Instant startTime
    ) {}

    public record EventResponse(
            Long id,
            Long organizerId,
            String name,
            String venue,
            Instant startTime,
            Instant createdAt
    ) {
        public static EventResponse from(Event event) {
            return new EventResponse(
                    event.getId(),
                    event.getOrganizer().getId(),
                    event.getName(),
                    event.getVenue(),
                    event.getStartTime(),
                    event.getCreatedAt()
            );
        }
    }
}
