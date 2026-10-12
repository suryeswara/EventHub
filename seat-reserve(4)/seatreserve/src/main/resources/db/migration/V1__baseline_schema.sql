-- ============================================================
-- V1: Baseline schema for seat-reserve
-- Design notes:
--   * seat.status is a plain VARCHAR + CHECK constraint rather than
--     a Postgres ENUM, so future states don't require a migration
--     that rewrites the type.
--   * reservation.idempotency_key has a UNIQUE constraint so a
--     retried confirm request can never create a duplicate reservation
--     even if the application-layer idempotency check races.
--   * seat has a UNIQUE(event_id, section, row, seat_number) so the
--     same physical seat can't be created twice for one event.
-- ============================================================

CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('ORGANIZER', 'CUSTOMER')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE event (
    id            BIGSERIAL PRIMARY KEY,
    organizer_id  BIGINT NOT NULL REFERENCES app_user(id),
    name          VARCHAR(255) NOT NULL,
    venue         VARCHAR(255) NOT NULL,
    start_time    TIMESTAMPTZ NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_event_organizer ON event(organizer_id);

CREATE TABLE seat (
    id            BIGSERIAL PRIMARY KEY,
    event_id      BIGINT NOT NULL REFERENCES event(id) ON DELETE CASCADE,
    section       VARCHAR(50) NOT NULL,
    row_label     VARCHAR(10) NOT NULL,
    seat_number   INT NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'
                      CHECK (status IN ('AVAILABLE', 'HELD', 'BOOKED')),
    version       BIGINT NOT NULL DEFAULT 0,   -- optimistic lock fallback / audit
    CONSTRAINT uq_seat_position UNIQUE (event_id, section, row_label, seat_number)
);

CREATE INDEX idx_seat_event_status ON seat(event_id, status);

CREATE TABLE reservation (
    id               BIGSERIAL PRIMARY KEY,
    seat_id          BIGINT NOT NULL REFERENCES seat(id),
    user_id          BIGINT NOT NULL REFERENCES app_user(id),
    status           VARCHAR(20) NOT NULL
                         CHECK (status IN ('PENDING', 'CONFIRMED', 'EXPIRED', 'CANCELLED')),
    idempotency_key  VARCHAR(100) NOT NULL UNIQUE,
    held_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ NOT NULL,
    confirmed_at     TIMESTAMPTZ,
    version          BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_reservation_seat ON reservation(seat_id);
CREATE INDEX idx_reservation_user ON reservation(user_id);
CREATE INDEX idx_reservation_status_expiry ON reservation(status, expires_at);
