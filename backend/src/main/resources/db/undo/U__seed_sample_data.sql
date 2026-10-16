-- Reverts db/seed/R__seed_sample_data.sql (removes only the sample rows).
-- Run it before U3/U2/U1 when undoing everything:
--   docker exec -i wanderly-db psql -U wanderly -d wanderly_db < src/main/resources/db/undo/U__seed_sample_data.sql
-- Clearing its history row makes Flyway re-seed on the next dev start.

BEGIN;

-- Cascades to the sample users' preferences, interests and visits.
DELETE FROM users WHERE email IN (
    'ana.pop@example.com',
    'mihai.ionescu@example.com',
    'elena.dumitru@example.com',
    'andrei.popescu@example.com',
    'ioana.marin@example.com',
    'radu.stan@example.com'
);

DELETE FROM flyway_schema_history WHERE script = 'R__seed_sample_data.sql';

COMMIT;
