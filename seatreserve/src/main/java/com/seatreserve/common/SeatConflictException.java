package com.seatreserve.common;

/** Thrown when a seat is not AVAILABLE at the moment a hold is attempted. */
public class SeatConflictException extends RuntimeException {
    public SeatConflictException(String message) {
        super(message);
    }
}
