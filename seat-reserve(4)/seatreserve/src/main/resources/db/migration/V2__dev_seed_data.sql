-- ============================================================
-- V2: Dev seed data — a demo event, a seat grid, and a demo
-- customer user so the frontend has real data to render without
-- any manual setup. NOT for production use.
-- ============================================================

INSERT INTO app_user (email, password_hash, role) VALUES
    ('organizer@seatreserve.dev', 'dev-placeholder', 'ORGANIZER'),
    ('customer@seatreserve.dev', 'dev-placeholder', 'CUSTOMER');

INSERT INTO event (organizer_id, name, venue, start_time)
SELECT id, 'Midnight Radio', 'Hall A', now() + interval '30 days'
FROM app_user WHERE email = 'organizer@seatreserve.dev';

-- 3 sections x 4 rows x 8 seats = 96 seats, all AVAILABLE
INSERT INTO seat (event_id, section, row_label, seat_number, status)
SELECT
    e.id,
    section_name,
    row_num::text,
    seat_num,
    'AVAILABLE'
FROM event e
CROSS JOIN (VALUES ('A'), ('B'), ('C')) AS sections(section_name)
CROSS JOIN generate_series(1, 4) AS row_num
CROSS JOIN generate_series(1, 8) AS seat_num
WHERE e.name = 'Midnight Radio';
