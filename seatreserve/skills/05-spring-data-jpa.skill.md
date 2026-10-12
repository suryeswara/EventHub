# Skill: Spring Data JPA / Hibernate

## Why this project needs it
JPA/Hibernate is the ORM (object-relational mapper) translating between
Java objects (`Seat`, `Reservation`) and database rows, without you
writing raw SQL for every CRUD operation.

## Core concepts to know
- **`@Entity` mapping**: `@Table(name = "seat")`, `@Column`,
  `@ManyToOne`, `@Id`/`@GeneratedValue` — annotations tell Hibernate
  exactly how a Java class maps onto a table.
- **Repositories as interfaces**: `SeatRepository extends
  JpaRepository<Seat, Long>` gives you `save()`, `findById()`,
  `findAll()` for free — Spring Data generates the implementation from
  the interface signature at startup.
- **Derived query methods**: `findByEventIdAndStatus(Long eventId,
  SeatStatus status)` — Spring Data parses the method name and builds
  the SQL automatically, no `@Query` needed.
- **`@Query` for custom queries**: used in `findByIdForUpdate` because
  the locking behavior (`@Lock`) needs an explicit JPQL query to attach
  to.
- **`FetchType.LAZY` vs `EAGER`**: `@ManyToOne(fetch = FetchType.LAZY)`
  on `Seat.event` — the related `Event` isn't loaded from the database
  until you actually call `.getEvent()`. Prevents pulling in data you
  don't need.
- **`open-in-view: false`**: set in `application.yml` deliberately — it
  stops lazy-loaded fields from being silently fetched during view
  rendering, a common source of hidden N+1 query bugs in real
  production systems.

## Where it shows up in seat-reserve
- All four entity classes (`User`, `Event`, `Seat`, `Reservation`)
- `SeatRepository`, `ReservationRepository`, `UserRepository`,
  `EventRepository`

## What to practice next
Look up what an "N+1 query problem" is, then find where it could
happen in this codebase if you added a naive `event.getSeats()` list
mapping without being careful about fetch strategy.
