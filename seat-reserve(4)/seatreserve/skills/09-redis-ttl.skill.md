# Skill: Redis (TTL-Based State)

## Why this project needs it
Redis solves a *different* problem than the pessimistic locking does.
Locking prevents double-booking at the instant of the hold. Redis (or,
currently, the `expires_at` column) tracks *when a hold should
automatically expire* if nobody confirms it.

## Core concepts to know
- **Redis as a key-value store**: extremely fast in-memory reads and
  writes, commonly used for caching, session storage, or — as planned
  here — tracking short-lived state like "this hold expires at time X."
- **TTL (Time To Live)**: Redis keys can be set to auto-delete after N
  seconds (`EXPIRE key 300`). This is a natural fit for "release this
  seat automatically after 5 minutes" without needing a constantly
  running background poll.
- **Why Redis is kept separate from the locking mechanism**: mixing
  concerns — using Redis for both mutual exclusion *and* expiry — would
  overload one tool for two different jobs and add a second point of
  failure to the correctness-critical hold logic. The database lock
  handles correctness; Redis (or the scheduled sweep) handles cleanup.
- **Two implementation options for expiry** (this project currently
  uses the simpler one, with Redis as a planned upgrade):
  1. A scheduled sweep job querying `WHERE status = 'PENDING' AND
     expires_at < now()` (works today, already has
     `ReservationRepository.findByStatusAndExpiresAtBefore`)
  2. Redis key expiry firing a callback/event to release the seat the
     instant it expires, instead of waiting for the next sweep cycle

## Where it shows up in seat-reserve
- `application.yml` — `spring.data.redis` config, already wired
- `pom.xml` — `spring-boot-starter-data-redis` dependency
- `docker-compose.yml` — the Redis container
- `ReservationRepository.findByStatusAndExpiresAtBefore` — ready for
  the sweep job implementation (Days 8–9 on the roadmap)

## What to practice next
Compare the two expiry approaches above: what's the latency difference
between "seat becomes available again" in each? When would that
latency actually matter to a real user?
