package com.seatreserve.common;

public class UnauthorizedOrganizerException extends RuntimeException {
    public UnauthorizedOrganizerException(String message) {
        super(message);
    }
}
