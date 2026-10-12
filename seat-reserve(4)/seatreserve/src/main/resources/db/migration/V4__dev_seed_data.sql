-- ============================================================
-- V4: Dev seed data for EventHub
-- Seed data:
--   * Event: "DevCon 2026"
--   * 3 Sessions under DevCon 2026 with realistic capacities:
--     1. "Opening Keynote" (capacity: 100)
--     2. "Hands-on: Kubernetes Workshop" (capacity: 30)
--     3. "Closing Panel" (capacity: 60)
--   * Seats generated per session matching each session's actual declared capacity.
-- ============================================================

INSERT INTO event (organizer_id, name, venue, start_time)
SELECT id, 'DevCon 2026', 'Convention Center - Hall 1', now() + interval '45 days'
FROM app_user WHERE email = 'organizer@seatreserve.dev';

-- Session 1: Opening Keynote (Capacity 100)
INSERT INTO session (event_id, title, description, start_time, end_time, capacity)
SELECT e.id, 'Opening Keynote', 'Welcome address and key industry announcements',
       e.start_time + interval '1 hour', e.start_time + interval '2 hours', 100
FROM event e WHERE e.name = 'DevCon 2026';

-- Session 2: Hands-on: Kubernetes Workshop (Capacity 30)
INSERT INTO session (event_id, title, description, start_time, end_time, capacity)
SELECT e.id, 'Hands-on: Kubernetes Workshop', 'Deep-dive interactive lab on cluster autoscaling and GitOps',
       e.start_time + interval '3 hours', e.start_time + interval '5 hours', 30
FROM event e WHERE e.name = 'DevCon 2026';

-- Session 3: Closing Panel (Capacity 60)
INSERT INTO session (event_id, title, description, start_time, end_time, capacity)
SELECT e.id, 'Closing Panel', 'Future of Cloud Native Architecture with special guests',
       e.start_time + interval '6 hours', e.start_time + interval '7 hours', 60
FROM event e WHERE e.name = 'DevCon 2026';

-- Seats for "Opening Keynote" (100 seats: 5 rows x 20 seats = 100)
INSERT INTO seat (session_id, section, row_label, seat_number, status)
SELECT
    s.id,
    'Main-Hall',
    row_num::text,
    seat_num,
    'AVAILABLE'
FROM session s
JOIN event e ON s.event_id = e.id
CROSS JOIN generate_series(1, 5) AS row_num
CROSS JOIN generate_series(1, 20) AS seat_num
WHERE e.name = 'DevCon 2026' AND s.title = 'Opening Keynote';

-- Seats for "Hands-on: Kubernetes Workshop" (30 seats: 3 rows x 10 seats = 30)
INSERT INTO seat (session_id, section, row_label, seat_number, status)
SELECT
    s.id,
    'Lab-B',
    row_num::text,
    seat_num,
    'AVAILABLE'
FROM session s
JOIN event e ON s.event_id = e.id
CROSS JOIN generate_series(1, 3) AS row_num
CROSS JOIN generate_series(1, 10) AS seat_num
WHERE e.name = 'DevCon 2026' AND s.title = 'Hands-on: Kubernetes Workshop';

-- Seats for "Closing Panel" (60 seats: 6 rows x 10 seats = 60)
INSERT INTO seat (session_id, section, row_label, seat_number, status)
SELECT
    s.id,
    'Auditorium',
    row_num::text,
    seat_num,
    'AVAILABLE'
FROM session s
JOIN event e ON s.event_id = e.id
CROSS JOIN generate_series(1, 6) AS row_num
CROSS JOIN generate_series(1, 10) AS seat_num
WHERE e.name = 'DevCon 2026' AND s.title = 'Closing Panel';
