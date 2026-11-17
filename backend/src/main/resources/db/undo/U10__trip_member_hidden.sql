-- Reverts db/migration/V10__trip_member_hidden.sql
-- Flyway Community does not run undo scripts, so run these by hand, newest first:
--   docker exec -i wanderly-db psql -U wanderly -d wanderly_db < src/main/resources/db/undo/U10__trip_member_hidden.sql

BEGIN;

DROP INDEX IF EXISTS idx_trip_members_user;

ALTER TABLE trip_members DROP COLUMN IF EXISTS hidden;

DELETE FROM flyway_schema_history WHERE version = '10';

COMMIT;
