package com.seatreserve.common;

public class UnauthorizedReservationException extends RuntimeException {
    public UnauthorizedReservationException(String message) {
        super(message);
    }
}
