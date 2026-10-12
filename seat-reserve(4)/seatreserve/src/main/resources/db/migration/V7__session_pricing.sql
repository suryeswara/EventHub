-- ============================================================
-- V7: Add pricing column to session table and update seed data
-- ============================================================

ALTER TABLE session 
ADD COLUMN price NUMERIC(10, 2) NOT NULL DEFAULT 0.00;

-- Set realistic demo prices for existing DevCon 2026 sessions
UPDATE session SET price = 49.00 WHERE title LIKE '%Keynote%';
UPDATE session SET price = 99.00 WHERE title LIKE '%Kubernetes%' OR title LIKE '%Workshop%';
UPDATE session SET price = 29.00 WHERE title LIKE '%Panel%' OR price = 0.00;
