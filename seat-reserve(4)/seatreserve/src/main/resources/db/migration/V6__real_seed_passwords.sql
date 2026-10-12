-- ============================================================
-- V6: Update seed accounts with real BCrypt password hashes
-- Plaintext password for both demo users is: password123
-- ============================================================

UPDATE app_user
SET password_hash = '$2a$10$ajCPSabyto23.iJR0UGNxeHZBkmpR7rRqDQJ1ksxQl7S2G9f4kdKm'
WHERE email IN ('organizer@seatreserve.dev', 'customer@seatreserve.dev');
