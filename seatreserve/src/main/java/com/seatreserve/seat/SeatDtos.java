package com.seatreserve.seat;

public class SeatDtos {

    public record SeatResponse(
            Long id,
            Long sessionId,
            String section,
            String rowLabel,
            Integer seatNumber,
            Seat.SeatStatus status
    ) {
        public static SeatResponse from(Seat seat) {
            return new SeatResponse(
                    seat.getId(),
                    seat.getSession() != null ? seat.getSession().getId() : null,
                    seat.getSection(),
                    seat.getRowLabel(),
                    seat.getSeatNumber(),
                    seat.getStatus()
            );
        }
    }
}
