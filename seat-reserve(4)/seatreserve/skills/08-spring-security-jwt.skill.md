# Skill: Spring Security & JWT

## Why this project needs it
Right now, anyone can call any endpoint — `SecurityConfig` permits all
requests. Days 10–11 replace that with real authentication: only logged
-in users can hold/confirm seats, and only organizers can manage their
own events.

## Core concepts to know
- **Authentication vs authorization**: authentication answers "who are
  you?" (login, verifying credentials); authorization answers "what are
  you allowed to do?" (roles, permissions). JWT handles the former;
  `@PreAuthorize`/role checks handle the latter.
- **JWT (JSON Web Token)**: a signed token containing claims (like user
  ID and role) that the server can verify without a database lookup on
  every request — the signature proves it wasn't tampered with.
- **The filter chain**: Spring Security intercepts every request before
  it reaches your controller. A `SecurityFilterChain` bean (like the
  current placeholder `SecurityConfig`) defines which paths need auth
  and which don't.
- **Stateless sessions**: unlike traditional session-cookie auth, JWT
  means the server holds no session state — every request carries its
  own proof of identity in the `Authorization: Bearer <token>` header.
  This matters for horizontal scaling: any server instance can validate
  any request without shared session storage.
- **Role-based access control (RBAC)**: `@PreAuthorize("hasRole('ORGANIZER')")`
  on an endpoint — declarative, readable authorization checks right on
  the method.

## Where it shows up in seat-reserve
- `SecurityConfig.java` — currently a **temporary permissive
  placeholder** (explicitly commented as such) so Swagger and manual
  API testing aren't blocked before real auth exists
- `pom.xml` — `jjwt-api`, `jjwt-impl`, `jjwt-jackson` already added,
  ready for implementation
- `User.Role` enum (`ORGANIZER`, `CUSTOMER`) — the roles JWT claims and
  `@PreAuthorize` checks will reference

## What to practice next
Before writing any JWT code, write out in plain English: what should
happen if a `CUSTOMER` tries to call an organizer-only endpoint? What
status code, what response body? Decide this before implementing it —
security design mistakes are expensive to fix after the fact.
