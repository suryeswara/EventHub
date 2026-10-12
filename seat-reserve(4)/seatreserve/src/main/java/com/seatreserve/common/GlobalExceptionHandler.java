package com.seatreserve.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(SeatConflictException.class)
    public ResponseEntity<ProblemDetail> handleSeatConflict(SeatConflictException ex, HttpServletRequest req) {
        log.info("Seat conflict (409) at [{} {}]: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, "seat-conflict", "Seat unavailable", ex.getMessage(), req);
    }

    @ExceptionHandler(HoldExpiredException.class)
    public ResponseEntity<ProblemDetail> handleHoldExpired(HoldExpiredException ex, HttpServletRequest req) {
        log.info("Hold expired (410) at [{} {}]: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.GONE, "hold-expired", "Hold expired", ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        log.debug("Validation error (400) at [{} {}]: {}", req.getMethod(), req.getRequestURI(), detail);
        return build(HttpStatus.BAD_REQUEST, "validation-error", "Invalid request", detail, req);
    }

    @ExceptionHandler(NotRegisteredException.class)
    public ResponseEntity<ProblemDetail> handleNotRegistered(NotRegisteredException ex, HttpServletRequest req) {
        log.info("Not registered (403) at [{} {}]: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "not-registered", "Registration required", ex.getMessage(), req);
    }

    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(java.util.NoSuchElementException ex, HttpServletRequest req) {
        log.debug("Resource not found (404) at [{} {}]: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "not-found", "Resource not found", ex.getMessage(), req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        log.debug("Invalid argument (400) at [{} {}]: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "invalid-argument", "Invalid argument", ex.getMessage(), req);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleIllegalState(IllegalStateException ex, HttpServletRequest req) {
        log.info("Illegal state conflict (409) at [{} {}]: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, "invalid-state", "Invalid state", ex.getMessage(), req);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex, HttpServletRequest req) {
        log.warn("Access denied (403) at [{} {}]: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "forbidden", "Access denied", ex.getMessage(), req);
    }

    @ExceptionHandler(UnauthorizedOrganizerException.class)
    public ResponseEntity<ProblemDetail> handleUnauthorizedOrganizer(UnauthorizedOrganizerException ex, HttpServletRequest req) {
        log.warn("Unauthorized organizer access (403) at [{} {}]: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "forbidden", "Access denied", ex.getMessage(), req);
    }

    @ExceptionHandler(UnauthorizedReservationException.class)
    public ResponseEntity<ProblemDetail> handleUnauthorizedReservation(UnauthorizedReservationException ex, HttpServletRequest req) {
        log.warn("Unauthorized reservation access (403) at [{} {}]: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "forbidden", "Access denied", ex.getMessage(), req);
    }

    @ExceptionHandler(PaymentDeclinedException.class)
    public ResponseEntity<ProblemDetail> handlePaymentDeclined(PaymentDeclinedException ex, HttpServletRequest req) {
        log.warn("Payment declined (402) at [{} {}]: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.PAYMENT_REQUIRED, "payment-declined", "Payment Declined", ex.getMessage(), req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled server exception (500) at [{} {}]", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Unexpected error",
                "An unexpected error occurred", req);
    }

    private ResponseEntity<ProblemDetail> build(HttpStatus status, String typeSlug, String title,
                                                 String detail, HttpServletRequest req) {
        ProblemDetail body = ProblemDetail.of(
                "https://api.seatreserve.com/errors/" + typeSlug,
                title,
                status.value(),
                detail,
                req.getRequestURI()
        );
        return ResponseEntity.status(status).body(body);
    }
}
