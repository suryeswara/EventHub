# Skill: Flyway Migrations

## Why this project needs it
Flyway makes your database schema version-controlled and reproducible
— exactly like Git for your schema. Anyone can clone this repo, run one
command, and get an identical database structure.

## Core concepts to know
- **Schema-as-code**: instead of letting Hibernate auto-generate
  tables (`ddl-auto: update`), the schema is hand-written SQL in
  `V1__baseline_schema.sql`, and `ddl-auto: validate` makes Hibernate
  merely *check* that entities match — never silently mutate — the
  real schema.
- **Naming convention**: `V<version>__<description>.sql` —
  `V1__baseline_schema.sql`, `V2__dev_seed_data.sql`. Flyway runs these
  in strict numeric order and tracks which have already run in a
  `flyway_schema_history` table.
- **Migrations are immutable once applied**: never edit a migration
  that's already run against a shared database — write a new `V3__...`
  file instead. Editing history breaks reproducibility for anyone who
  already ran the old version.
- **Baseline vs incremental migrations**: `V1` establishes the whole
  schema from nothing; every later file is a small, additive change —
  the same discipline real teams use so schema changes are reviewable
  in pull requests.

## Where it shows up in seat-reserve
- `V1__baseline_schema.sql` — tables, constraints, indexes
- `V2__dev_seed_data.sql` — demo event + seats so the frontend has data
  to render immediately
- `application.yml`: `flyway.enabled: true`,
  `locations: classpath:db/migration`

## What to practice next
Write a `V3__` migration yourself (don't run it yet) that would add an
`is_cancelled` boolean to `reservation` — this is the exact skill of
making a *small, safe, additive* schema change instead of rewriting
the whole table.
