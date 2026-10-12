# seat-reserve

A backend system that lets customers hold and book event seats without ever
double-booking the same seat — even under concurrent load.

## The problem this solves

When N users click "reserve" on the same seat within milliseconds of each
other, a naive read-then-write implementation lets more than one succeed.
This project proves, with a real concurrent test against Postgres, that
exactly one request wins and the rest are cleanly rejected with `409`.

See `src/test/java/com/seatreserve/reservation/ConcurrentSeatHoldTest.java`
for the proof: 20 threads race for one seat via Testcontainers-backed
Postgres; the test asserts exactly 1 success and 19 conflicts.

## Architecture decisions (and why)

**Pessimistic DB locking (`SELECT ... FOR UPDATE`), not Redis distributed
locks or optimistic locking, for the seat-hold critical section.**
The hold operation is short-lived and single-database-scoped. Pessimistic
locking lets the second concurrent request block, then see accurate
up-to-date state and fail cleanly — versus optimistic locking, which would
let it race ahead and fail only at commit time, or Redis locks, which add
an extra network hop and failure mode for a problem the database already
solves natively. If this system needed to scale across many app instances
hitting a sharded/distributed datastore, that's the specific point I'd
revisit this decision and reach for Redis or a dedicated lock service.

**Redis is used only for hold-expiry TTL bookkeeping, not for locking.**
Keeps the two concerns — mutual exclusion and time-based expiry — cleanly
separated rather than overloading one tool for both.

**Modular monolith, not microservices.** At this scale (a single write
path guarded by one table's row locks), splitting into services would add
network calls and distributed-transaction complexity without solving any
real bottleneck. Package boundaries (`event`, `seat`, `reservation`,
`user`, `security`) keep the codebase modular enough to extract a service
later if load ever genuinely demands it.

**DTOs are never entities.** Controllers only ever see `ReservationDtos`
records, never the JPA `Reservation`/`Seat` entities directly — prevents
lazy-loading exceptions and accidental persistence-layer leakage into the
API contract.

**Errors follow RFC 9457** (`ProblemDetail` + `GlobalExceptionHandler`) —
every error response is machine-parseable with a stable `type` URI.

## Stack

Spring Boot 3.3 / Java 21, Spring Data JPA, PostgreSQL, Flyway, Redis
(TTL only), Spring Security + JWT, Testcontainers, JUnit 5.

## Running locally

```bash
docker-compose up -d          # Postgres + Redis
./mvnw spring-boot:run        # Flyway migrates on startup
```

## Running tests

```bash
./mvnw test                   # includes Testcontainers-backed integration tests
```

## Project status / roadmap

- [x] Baseline schema (Flyway `V1`)
- [x] Domain entities + repositories
- [x] Seat-hold concurrency logic + proof test
- [x] RFC 9457 error handling
- [ ] Confirm/cancel idempotency hardening + scheduled expiry sweep (Days 8-9)
- [ ] JWT auth + role-based access (Days 10-11)
- [ ] Pagination/filtering on seat listing, Docker packaging (Days 12-13)

Full roadmap and day-by-day plan tracked separately during development.

mvn clean package -DskipTests
java -jar target/seat-reserve-0.1.0.jar

mvn spring-boot:run

mvn spring-boot:run -Dspring-boot.run.profiles=dev
