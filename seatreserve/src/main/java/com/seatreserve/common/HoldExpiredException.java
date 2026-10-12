package com.seatreserve.common;

/** Thrown when a confirm is attempted against a reservation whose hold TTL has passed. */
public class HoldExpiredException extends RuntimeException {
    public HoldExpiredException(String message) {
        super(message);
    }
}
