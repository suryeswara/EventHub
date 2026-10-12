package com.seatreserve.session;

import com.seatreserve.seat.SeatDtos.SeatResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class SessionDtos {

    public record CreateSessionRequest(
            @NotBlank(message = "Title is required")
            String title,

            String description,

            @NotNull(message = "Start time is required")
            Instant startTime,

            @NotNull(message = "End time is required")
            Instant endTime,

            @NotNull(message = "Capacity is required")
            @Min(value = 1, message = "Capacity must be at least 1")
            Integer capacity,

            @DecimalMin(value = "0.00", message = "Price cannot be negative")
            BigDecimal price
    ) {
        public CreateSessionRequest {
            if (price == null) {
                price = BigDecimal.ZERO;
            }
        }

        public CreateSessionRequest(String title, String description, Instant startTime, Instant endTime, Integer capacity) {
            this(title, description, startTime, endTime, capacity, BigDecimal.ZERO);
        }
    }

    public record SectionSpec(
            @NotBlank(message = "Section name is required")
            String name,

            @Min(value = 1, message = "Rows must be at least 1")
            int rows,

            @Min(value = 1, message = "Seats per row must be at least 1")
            int seatsPerRow
    ) {
        public int seatCount() {
            return rows * seatsPerRow;
        }
    }

    public record GenerateSeatsRequest(
            @NotEmpty(message = "At least one section specification is required")
            @Valid
            List<SectionSpec> sections
    ) {
        public int totalSeatCount() {
            return sections == null ? 0 : sections.stream().mapToInt(SectionSpec::seatCount).sum();
        }
    }

    public record GenerateSeatsResponse(
            Long sessionId,
            int totalSeatsGenerated,
            List<SeatResponse> seats
    ) {}

    public record SessionResponse(
            Long id,
            Long eventId,
            String title,
            String description,
            Instant startTime,
            Instant endTime,
            Integer capacity,
            BigDecimal price,
            long totalSeats,
            long availableSeats,
            boolean seatsGenerated
    ) {
        public static SessionResponse from(Session session, long totalSeats, long availableSeats) {
            return new SessionResponse(
                    session.getId(),
                    session.getEvent() != null ? session.getEvent().getId() : null,
                    session.getTitle(),
                    session.getDescription(),
                    session.getStartTime(),
                    session.getEndTime(),
                    session.getCapacity(),
                    session.getPrice() != null ? session.getPrice() : BigDecimal.ZERO,
                    totalSeats,
                    availableSeats,
                    totalSeats > 0
            );
        }
    }
}
