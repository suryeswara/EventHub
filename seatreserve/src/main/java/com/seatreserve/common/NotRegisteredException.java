package com.seatreserve.common;

/**
 * Thrown when a customer attempts to hold a seat in a session without first
 * having an active AttendeeRegistration for the parent event.
 */
public class NotRegisteredException extends RuntimeException {
    public NotRegisteredException(String message) {
        super(message);
    }
}
