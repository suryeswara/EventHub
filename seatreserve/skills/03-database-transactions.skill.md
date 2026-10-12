# Skill: Relational DB Design & Transactions

## Why this project needs it
This is the DBMS knowledge you already had going in — this project puts
it to direct use in schema design and transaction boundaries, which is
exactly where the concurrency guarantee lives or dies.

## Core concepts to know
- **Schema design with constraints doing real work**: in
  `V1__baseline_schema.sql`, `UNIQUE (event_id, section, row_label, seat_number)`
  on `seat` and `UNIQUE` on `reservation.idempotency_key` aren't just
  data hygiene — they're a last line of defense. Even if application
  logic somehow raced past a check, the database itself refuses a
  duplicate.
- **Foreign keys with `ON DELETE CASCADE`**: `seat.event_id REFERENCES
  event(id) ON DELETE CASCADE` — deleting an event cleans up its seats
  automatically, preventing orphaned rows.
- **Transactions (`@Transactional`)**: a transaction is a unit of work
  that either fully commits or fully rolls back. `holdSeat()` is one
  transaction — if any step fails partway, nothing is left half-applied.
- **Isolation levels**: control what one transaction can see of another
  transaction's uncommitted changes. `READ COMMITTED` (Postgres's
  default) is what this project relies on, combined with explicit row
  locking rather than relying on isolation level alone.
- **Indexes**: `idx_seat_event_status`, `idx_reservation_status_expiry`
  — added on columns the app actually queries by, not everywhere,
  because indexes speed up reads but slow down writes.

## Where it shows up in seat-reserve
- `V1__baseline_schema.sql` — the schema of record (Hibernate never
  auto-generates it — `ddl-auto: validate` in `application.yml`)
- `@Transactional` on every method in `ReservationService`

## What to practice next
Write out, in plain English, what would go wrong in `holdSeat()` if the
`@Transactional` annotation were removed — walk through the exact
sequence of partial failures possible.
