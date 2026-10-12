-- ============================================================
-- V5: Drop redundant index on seat(session_id)
-- Design notes:
--   * idx_seat_session on seat(session_id) is redundant because
--     idx_seat_session_status on (session_id, status) already has
--     session_id as its leftmost leading column.
--   * Dropping this index removes duplicate index maintenance overhead
--     on bulk seat generation and seat status mutations.
--   * attendee_registration(event_id, user_id) is already uniquely indexed
--     via uq_attendee_registration, with idx_attendee_registration_user
--     properly covering user_id lookups.
-- ============================================================

DROP INDEX IF EXISTS idx_seat_session;
