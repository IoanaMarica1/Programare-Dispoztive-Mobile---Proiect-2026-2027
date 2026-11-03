-- Reverts db/migration/V6__stop_details.sql
-- Flyway Community does not run undo scripts, so run these by hand, newest first:
--   docker exec -i wanderly-db psql -U wanderly -d wanderly_db < src/main/resources/db/undo/U6__stop_details.sql

BEGIN;

ALTER TABLE trip_stops
    DROP COLUMN IF EXISTS description,
    DROP COLUMN IF EXISTS opening_hours;

DELETE FROM flyway_schema_history WHERE version = '6';

COMMIT;
