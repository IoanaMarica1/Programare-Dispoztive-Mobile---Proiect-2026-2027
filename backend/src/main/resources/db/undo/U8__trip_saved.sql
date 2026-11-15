-- Reverts db/migration/V8__trip_saved.sql
-- Flyway Community does not run undo scripts, so run these by hand, newest first:
--   docker exec -i wanderly-db psql -U wanderly -d wanderly_db < src/main/resources/db/undo/U8__trip_saved.sql

BEGIN;

DROP INDEX IF EXISTS idx_trips_user_saved;

ALTER TABLE trips DROP COLUMN IF EXISTS saved;

DELETE FROM flyway_schema_history WHERE version = '8';

COMMIT;
