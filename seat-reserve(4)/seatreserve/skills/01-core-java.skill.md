# Skill: Core Java & OOP

## Why this project needs it
Every class in seat-reserve — entities, services, repositories, DTOs — is
built on fundamentals: classes, interfaces, generics, streams, exceptions,
and records. Spring hides a lot of wiring, but it can't hide bad Java.

## Core concepts to know
- **Classes vs records**: entities (`Seat`, `Reservation`) are mutable
  classes because JPA needs to manage their state. DTOs
  (`ReservationDtos.HoldRequest`) are Java `record`s — immutable,
  concise, perfect for data that just moves through a request/response.
- **Interfaces**: `SeatRepository extends JpaRepository<Seat, Long>` —
  Spring Data generates the implementation at runtime. You only declare
  the contract.
- **Generics**: `JpaRepository<Seat, Long>`, `Optional<Seat>` — generics
  give compile-time type safety instead of casting.
- **Streams**: used throughout, e.g.
  `seatRepository.findByEventId(eventId).stream().map(SeatResponse::from).toList()`
  in `SeatController` — transforms entities to DTOs without a manual loop.
- **Checked vs unchecked exceptions**: `SeatConflictException` and
  `HoldExpiredException` extend `RuntimeException` (unchecked) — a
  deliberate choice so service methods don't need `throws` clauses
  cluttering every signature.
- **Optional**: `seatRepository.findByIdForUpdate(seatId).orElseThrow(...)`
  — forces you to explicitly handle the "not found" case instead of
  risking a `NullPointerException`.

## Where it shows up in seat-reserve
- `ReservationService.holdSeat()` — method chaining, `Optional`,
  exception throwing
- `SeatDtos.SeatResponse.from(Seat seat)` — a static factory method
  pattern, converting between entity and DTO

## What to practice next
Write a few small exercises converting a list of objects with streams
(`filter`, `map`, `collect`) until `seat -> DTO` transformations feel
automatic rather than something you have to look up.
