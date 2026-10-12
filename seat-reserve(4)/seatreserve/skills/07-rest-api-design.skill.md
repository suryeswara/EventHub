# Skill: REST API Design & RFC 9457

## Why this project needs it
A well-designed API is predictable — the same shape for success, the
same shape for errors, everywhere. This is what makes an API pleasant
(or painful) to consume, and it's a skill interviewers explicitly probe.

## Core concepts to know
- **Resource-oriented URLs**: `/api/v1/events/{eventId}/seats/{seatId}/hold`
  — nouns and hierarchy, not verbs. The HTTP method (`POST`) carries the
  action, the path carries the resource.
- **Correct status codes**: `201 Created` for a successful hold (a new
  resource — the reservation — was created), `409 Conflict` for a
  seat that's unavailable, `410 Gone` for an expired hold (the
  resource existed but is now permanently gone — different from `404`,
  which means it never existed).
- **DTOs, never entities, in controller signatures**: `ReservationDtos`
  records are the only thing `ReservationController` ever accepts or
  returns — never the JPA `Reservation` entity directly. This prevents
  lazy-loading exceptions and decouples your API contract from your
  database schema (you can refactor the schema without breaking API
  consumers).
- **RFC 9457 Problem Details**: a standardized JSON error shape —
  `type`, `title`, `status`, `detail`, `instance` — so every error
  response is machine-parseable the same way, instead of every
  endpoint inventing its own error format.
- **Idempotency**: the `Idempotency-Key` header on `/confirm` — a
  retried request with the same key returns the same result instead of
  double-processing. Critical for any payment-adjacent flow.

## Where it shows up in seat-reserve
- `ReservationController` — endpoint design
- `ProblemDetail` + `GlobalExceptionHandler` — the RFC 9457
  implementation
- `ReservationDtos.java` — the DTO/entity separation

## What to practice next
Design (on paper) the endpoint for cancelling a confirmed reservation —
what HTTP method, what status code on success, what error status if
it's already cancelled? Then compare your answer against how `confirm`
was designed.
