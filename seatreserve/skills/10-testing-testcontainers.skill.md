# Skill: Testing (JUnit 5, Testcontainers)

## Why this project needs it
A claim like "this prevents double-booking" is worthless without proof.
Testing is what turns that claim into something you can demonstrate —
and defend — in an interview.

## Core concepts to know
- **Unit tests vs integration tests**: a unit test isolates one class
  (mocking its dependencies); an integration test runs real components
  together. `ConcurrentSeatHoldTest` is an integration test — it needs
  a real database because in-memory fakes (like H2) don't reliably
  reproduce Postgres's actual row-locking behavior.
- **Testcontainers**: spins up real Docker containers (a real Postgres,
  in this case) for the duration of a test run, then tears them down.
  This is *why* the concurrency test is trustworthy — it's not testing
  against a simplified stand-in database, it's testing against the
  real thing.
- **`@DynamicPropertySource`**: wires the Testcontainers-provided
  database URL/credentials into Spring's configuration at test runtime,
  overriding whatever's in `application.yml`.
- **Concurrency testing pattern**: `ExecutorService` + `CountDownLatch`
  — the latch makes all threads wait at a starting line and release
  simultaneously, maximizing the chance of actually triggering a race
  condition (versus threads starting at slightly different times, which
  might mask a bug).
- **Testing the failure path, not just the happy path**: the test
  doesn't just assert "one hold succeeded" — it asserts *the other 19
  failed with the right exception*. Both halves matter.

## Where it shows up in seat-reserve
- `ConcurrentSeatHoldTest.java` — the centerpiece proof
- `pom.xml` — `testcontainers-junit-jupiter`, `testcontainers-postgresql`

## What to practice next
Write a second, smaller integration test: confirm that calling
`confirm()` twice with the same idempotency key returns the same
reservation rather than erroring or double-confirming. This exercises
the idempotency guarantee the same way the concurrency test exercises
the locking guarantee.
