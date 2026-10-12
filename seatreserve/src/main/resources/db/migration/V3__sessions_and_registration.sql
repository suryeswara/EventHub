-- ============================================================
-- V3: Add Session and AttendeeRegistration domain layer
-- Design notes:
--   * Introduces the Session layer between Event and Seat (Event -> Session -> Seat).
--     This allows an event to host multiple parallel or sequential sessions,
--     each with its own capacity and seat grid.
--   * Introduces AttendeeRegistration representing event-level attendance,
--     distinct from session-level Seat reservations (Reservation).
--     Enforces UNIQUE(event_id, user_id) so an attendee registers at most once per event.
--   * Alters Seat table to reference Session instead of Event, preserving
--     the unique position constraint per session: UNIQUE(session_id, section, row_label, seat_number).
--   * NOTE ON DATA PURGE: The DELETE statements below purge pre-existing seats and
--     reservations so session_id NOT NULL can be added cleanly. This is acceptable
--     only because existing data is purely dev seed data (V2). In a live production
--     environment with customer records, this would instead be executed as a non-destructive
--     backfill: create a default Session per existing Event, backfill session_id on all
--     seats, add the NOT NULL constraint, and only then drop event_id.
-- ============================================================

-- Clean pre-existing dev seed data so session_id NOT NULL constraint can be enforced
DELETE FROM reservation;
DELETE FROM seat;

-- 1. Create session table
CREATE TABLE session (
    id          BIGSERIAL PRIMARY KEY,
    event_id    BIGINT NOT NULL REFERENCES event(id) ON DELETE CASCADE,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    start_time  TIMESTAMPTZ NOT NULL,
    end_time    TIMESTAMPTZ NOT NULL,
    capacity    INT NOT NULL CHECK (capacity > 0),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_session_times CHECK (end_time >= start_time)
);

CREATE INDEX idx_session_event ON session(event_id);

-- 2. Create attendee_registration table
CREATE TABLE attendee_registration (
    id            BIGSERIAL PRIMARY KEY,
    event_id      BIGINT NOT NULL REFERENCES event(id) ON DELETE CASCADE,
    user_id       BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_attendee_registration UNIQUE (event_id, user_id)
);

-- uq_attendee_registration(event_id, user_id) automatically creates an implicit index 
-- where event_id is the leftmost prefix. Only user_id needs an explicit standalone index.
CREATE INDEX idx_attendee_registration_user ON attendee_registration(user_id);

-- 3. Alter seat table: drop event_id, add session_id, and recreate position constraint
DROP INDEX IF EXISTS idx_seat_event_status;
ALTER TABLE seat DROP CONSTRAINT IF EXISTS uq_seat_position;
ALTER TABLE seat DROP COLUMN event_id;

ALTER TABLE seat ADD COLUMN session_id BIGINT NOT NULL REFERENCES session(id) ON DELETE CASCADE;
ALTER TABLE seat ADD CONSTRAINT uq_seat_position UNIQUE (session_id, section, row_label, seat_number);

CREATE INDEX idx_seat_session ON seat(session_id);
CREATE INDEX idx_seat_session_status ON seat(session_id, status);
