# Skill: Spring Boot & Dependency Injection

## Why this project needs it
Spring Boot is the framework holding the whole app together — it wires
your services, repositories, and controllers into one running
application without you manually instantiating anything.

## Core concepts to know
- **Inversion of Control (IoC)**: you don't create objects with `new`;
  Spring creates and manages them (called "beans") and hands them to
  whoever needs them.
- **Constructor injection** (the pattern used throughout this project):
  ```java
  @Service
  @RequiredArgsConstructor   // Lombok generates the constructor
  public class ReservationService {
      private final SeatRepository seatRepository;
      private final ReservationRepository reservationRepository;
      ...
  }
  ```
  Spring sees the constructor, finds beans matching each parameter
  type, and injects them automatically.
- **Stereotype annotations**: `@Service` (business logic),
  `@RestController` (HTTP layer), `@Repository`/interface extending
  `JpaRepository` (data layer), `@Configuration` (bean definitions like
  `OpenApiConfig`, `SecurityConfig`).
- **Auto-configuration**: adding `spring-boot-starter-data-jpa` to
  `pom.xml` automatically configures a `DataSource`, an
  `EntityManagerFactory`, and transaction management — you only supply
  `application.yml` properties.
- **`application.yml`**: externalized configuration (DB URL, Redis
  host, JWT secret, hold TTL) — never hardcode these in Java code.

## Where it shows up in seat-reserve
- `SeatReserveApplication.java` — `@SpringBootApplication` is three
  annotations combined: `@Configuration`, `@EnableAutoConfiguration`,
  `@ComponentScan`
- Every `@RequiredArgsConstructor` service/controller in the project

## What to practice next
Trace one request end-to-end in your head: HTTP request →
`ReservationController` → `ReservationService` → `SeatRepository` →
database. Name which annotation makes each hop possible.
