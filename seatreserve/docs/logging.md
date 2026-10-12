# EventHub — Logging & Observability Guide (Phase 5)

## 1. Overview
EventHub utilizes **SLF4J** backed by **Logback** with **Mapped Diagnostic Context (MDC)** for distributed request correlation and structured business audit logging. This document defines the logging standards, level discipline, security boundaries, and observability configuration.

---

## 2. Log Level Discipline

| Log Level | Purpose & Usage in EventHub | Examples |
| :--- | :--- | :--- |
| **`DEBUG`** | Fine-grained diagnostic information and routine client-input anomalies during development. | Validation errors (400), resource 404 lookups, Hibernate SQL queries in `dev` profile. |
| **`INFO`** | Normal operational events and anticipated business-rule outcomes under high concurrency. | **Seat hold succeeded**, **seat conflict (409)** (expected under contention), **reservation confirmed**, **registration created / idempotent repeat**, user login/logout. |
| **`WARN`** | Actionable security events, authorization rejections, or unexpected business degradations that do not crash the service. | **Unauthorized organizer access attempts** (`UnauthorizedOrganizerException`), **RBAC access denials** (403), **failed login attempts** (`AuthenticationFailureBadCredentialsEvent`), hold expiration during confirmation. |
| **`ERROR`** | Unhandled system exceptions, infrastructure faults, database connectivity failures. | Uncaught runtime exceptions (500) caught in `GlobalExceptionHandler.handleUnexpected()`. |

> **Key Design Decision: Why Seat Conflict (409) is `INFO`, Not `WARN`:**  
> In a high-concurrency seat reservation system, multiple users racing to hold the same seat is expected and intended normal traffic. The pessimistic DB lock (`SELECT ... FOR UPDATE`) guarantees serial execution where 1 wins and 19 receive a 409 conflict. Classifying 409 as `WARN` would create alert fatigue for operations teams. Therefore, seat conflicts are logged at `INFO`.

---

## 3. Correlation & Request Tracing

### Request Lifecycle
Every incoming HTTP request passes through `CorrelationIdFilter` (`Ordered.HIGHEST_PRECEDENCE`):
1. **Header Inspection**: Checks for existing `X-Correlation-Id` or `X-Request-Id`.
2. **Fallback Generation**: If absent or blank, generates a fresh `UUID.randomUUID()`.
3. **MDC Injection**: Stores the correlation ID in SLF4J's `MDC` under the key `"correlationId"`.
4. **Header Echo**: Writes `X-Correlation-Id: <id>` to the HTTP response headers.
5. **MDC Cleanup**: In a `finally` block, `MDC.remove("correlationId")` guarantees no thread-pool pollution.

### Output Formatting
Configured in `application.yml`:
```yaml
logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%X{correlationId:-SYSTEM}] %5p [%15.15t] %-40.40logger{39} : %m%n"
```
Sample trace output:
```text
2026-09-29 12:30:15.123 [7b36f1c4-2f22-4809-9069-fcf2e2124571]  INFO [nio-8080-exec-1] c.s.r.ReservationService : Seat hold succeeded: reservationId=101, seatId=5, userId=12
2026-09-29 12:30:15.125 [7b36f1c4-2f22-4809-9069-fcf2e2124571]  INFO [nio-8080-exec-1] c.s.r.ReservationService : Reservation confirmed: reservationId=101, seatId=5, userId=12
```
When log events occur outside of an active HTTP request (e.g., application startup or background workers), `correlationId` defaults to `[SYSTEM]`.

---

## 4. Business Audit Event Catalog

| Business Event | Trigger Location | Level | Log Content Format |
| :--- | :--- | :--- | :--- |
| **Seat Hold Succeeded** | `ReservationService.holdSeat()` | `INFO` | `Seat hold succeeded: reservationId={}, seatId={}, userId={}, expiresAt={}` |
| **Seat Hold Conflict** | `ReservationService.holdSeat()` | `INFO` | `Seat hold rejected (conflict): seatId={}, userId={}, currentStatus={}` |
| **Reservation Confirmed** | `ReservationService.confirm()` | `INFO` | `Reservation confirmed: reservationId={}, seatId={}, userId={}, confirmedAt={}` |
| **Reservation Idempotent Retry** | `ReservationService.confirm()` | `INFO` | `Reservation confirmation idempotent return: reservationId={}, key={}` |
| **Hold Expiration Check** | `ReservationService.confirm()` | `INFO` | `Reservation hold expiry check: reservationId={}, seatId={}, expiresAt={}, now={}` |
| **New Event Registration** | `RegistrationService.registerForEvent()` | `INFO` | `New registration created: registrationId={}, eventId={}, userId={}` |
| **Idempotent Registration** | `RegistrationService.registerForEvent()` | `INFO` | `Idempotent registration: user {} already registered for event {} (registrationId={})` |
| **Organizer Ownership Denial** | `SessionService.createSession()` & `generateSeats()` | `WARN` | `Organizer authorization denied: user {} attempted modifying event {} owned by {}` |
| **Failed Login Attempt** | `AuthenticationEventListener` & `SecurityConfig` | `WARN` | `Authentication failure - bad credentials for username: {}` |

---

## 5. Sensitive Data Exclusion Policy

**Strict Rule**: No passwords, password hashes, or session tokens may ever be written to logs at any log level.

### Safeguards Implemented:
1. **User Entity**: `@ToString(exclude = "passwordHash")` prevents accidental password leakage if a `User` entity is converted to a string.
2. **AppUserDetails**: Explicit `toString()` overrides default behavior and omits `password` / `passwordHash`.
3. **Authentication Logging**: Only the submitted `username` is logged during authentication successes or failures; the `password` parameter is never referenced in log parameters.
4. **Structured Parameters**: All logging uses SLF4J parameterized placeholders (`log.info("...", arg1, arg2)`) to prevent string injection and formatting errors.

---

## 6. Environment Profiles

### Development (`application-dev.yml`)
- Active by default (`spring.profiles.active: dev`).
- Verbose logging: `logging.level.com.seatreserve: DEBUG`.
- Formatted Hibernate SQL logging: `org.hibernate.SQL: DEBUG`, `org.hibernate.orm.jdbc.bind: TRACE`.
- Enables developers to inspect row-level locking queries and parameter bindings locally.

### Production (`application-prod.yml`)
- Active via `--spring.profiles.active=prod` or `SPRING_PROFILES_ACTIVE=prod`.
- Clean logging: `root: WARN`, `com.seatreserve: INFO`.
- SQL logging disabled (`org.hibernate.SQL: WARN`, `show-sql: false`) to minimize I/O overhead and prevent database query leakage.

---

## 7. Spring Boot Actuator Observability

EventHub includes `spring-boot-starter-actuator` for runtime observability:
- **Public Probes**: `/actuator/health` and `/actuator/info` are exposed anonymously (`permitAll()`) for orchestrators, container readiness, and liveness probes.
- **Protected Metrics**: `/actuator/metrics` and management endpoints require authentication with `ROLE_ORGANIZER` (`hasRole("ORGANIZER")` in `SecurityConfig`). Unauthenticated requests receive `401 Unauthorized` or `403 Forbidden`.

---

## 8. Explicit Non-Goals & Future Iterations

1. **Centralized Log Aggregation**: ELK stack, Splunk, or Grafana Loki are out of scope for the current local architecture and should be attached at production container deployment.
2. **Metrics Dashboards**: Prometheus scraping and Grafana dashboards are deferred to the Production Depth tier.
3. **Background Expiry Sweeper**: Hold expiration is currently enforced on-demand during `confirm()` and checked idempotently. A scheduled background sweeper (`@Scheduled` cron or Redis keyspace event sweep) is deferred to future work.
