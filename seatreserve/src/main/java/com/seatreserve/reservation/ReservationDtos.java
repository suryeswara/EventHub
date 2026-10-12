package com.seatreserve.reservation;

import java.time.Instant;

public class ReservationDtos {

    public record HoldRequest() {}

    public record ConfirmRequest(
            String idempotencyKey
    ) {}

    public record ReservationResponse(
            Long reservationId,
            Long seatId,
            Reservation.Status status,
            Instant expiresAt
    ) {
        static ReservationResponse from(Reservation r) {
            return new ReservationResponse(r.getId(), r.getSeat().getId(), r.getStatus(), r.getExpiresAt());
        }
    }
}
