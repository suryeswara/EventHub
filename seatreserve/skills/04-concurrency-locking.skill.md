# Skill: Concurrency Control (Pessimistic Locking)

## Why this project needs it
This is the actual point of seat-reserve. Everything else supports this
one guarantee: two people can never book the same seat, no matter how
close together their requests arrive.

## Core concepts to know
- **The race condition**: without locking, two transactions can both
  read "seat is AVAILABLE" before either writes "HELD" — both then
  proceed, and you get a double booking. This is the classic
  read-then-write race.
- **Pessimistic vs optimistic locking**:
  - *Pessimistic* (used here): lock the row the moment you read it —
    `SELECT ... FOR UPDATE`. Anyone else wanting that row waits.
  - *Optimistic*: don't lock; instead, check a `@Version` number at
    commit time and fail if it changed. Cheaper when conflicts are
    rare, but the loser finds out only after doing the work.
  - This project chose pessimistic because the hold operation is short
    and the correctness guarantee needs to be airtight, not just
    "probably fine."
- **`SELECT ... FOR UPDATE`**: a SQL clause that locks the selected
  row(s) until the transaction commits or rolls back. Any other
  transaction trying to `SELECT ... FOR UPDATE` the same row blocks —
  it doesn't error, it waits in line.
- **Lock scope and duration**: the lock is held only for the length of
  the `@Transactional` method — acquired at the `SELECT`, released at
  commit. Keeping transactions short is what keeps this fast under load.

## Where it shows up in seat-reserve
- `SeatRepository.findByIdForUpdate()`:
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from Seat s where s.id = :seatId")
  Optional<Seat> findByIdForUpdate(@Param("seatId") Long seatId);
  ```
- `ReservationService.holdSeat()` — calls that method first, checks
  status, then mutates
- `ConcurrentSeatHoldTest` — the proof: 20 threads race for one seat,
  exactly 1 succeeds

## What to practice next
Explain out loud (or to a rubber duck) why `PESSIMISTIC_WRITE` on the
`SELECT` is what actually prevents the race — not the `if (status !=
AVAILABLE)` check by itself. The check alone, without the lock, is
exactly the race condition described above.
