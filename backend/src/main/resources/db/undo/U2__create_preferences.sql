-- Reverts db/migration/V2__create_preferences.sql
-- Flyway Community does not run undo scripts, so run these by hand, newest first:
--   docker exec -i wanderly-db psql -U wanderly -d wanderly_db < src/main/resources/db/undo/U2__create_preferences.sql

BEGIN;

DROP TABLE IF EXISTS preference_interests;
DROP TABLE IF EXISTS preferences;

DELETE FROM flyway_schema_history WHERE version = '2';

COMMIT;
