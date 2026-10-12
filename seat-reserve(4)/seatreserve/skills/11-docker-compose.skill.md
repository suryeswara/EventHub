# Skill: Docker & Docker Compose

## Why this project needs it
Docker gives everyone who runs this project — you, a reviewer, a future
teammate — the exact same Postgres and Redis setup with one command,
instead of "install Postgres 16 locally and hope the version matches."

## Core concepts to know
- **Containers vs VMs**: a container packages an application with its
  dependencies but shares the host OS kernel — much lighter and faster
  to start than a full virtual machine.
- **Images vs containers**: an image (`postgres:16-alpine`) is a
  read-only template; a container is a running instance of that image.
  You can run many containers from one image.
- **`docker-compose.yml`**: defines multiple containers (Postgres,
  Redis) as one unit, with their ports, environment variables, and
  health checks declared together — one `docker-compose up -d` starts
  everything in the right configuration.
- **Health checks**: `pg_isready` and `redis-cli ping` checks in
  `docker-compose.yml` let you know a container isn't just *running*
  but actually *ready to accept connections* — an app that starts
  before the DB is ready will fail to connect.
- **Volumes**: `pgdata:/var/lib/postgresql/data` — without this, your
  database data would vanish every time the container restarts. The
  volume persists data on the host machine, outside the container's
  lifecycle.

## Where it shows up in seat-reserve
- `docker-compose.yml` — Postgres + Redis, with health checks and a
  named volume
- `dockersetup.md` — the full install/verification walkthrough
- Testcontainers (a related but separate use of Docker — spinning up
  *throwaway* containers specifically for test runs)

## What to practice next
Read `docker-compose.yml` line by line and explain what would break if
you removed the `healthcheck` blocks — specifically, what race
condition could occur between the app starting and Postgres being
ready.
