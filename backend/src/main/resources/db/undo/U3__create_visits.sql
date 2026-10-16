-- Reverts db/migration/V3__create_visits.sql
-- Flyway Community does not run undo scripts, so run these by hand, newest first:
--   docker exec -i wanderly-db psql -U wanderly -d wanderly_db < src/main/resources/db/undo/U3__create_visits.sql

BEGIN;

DROP TABLE IF EXISTS visits;

DELETE FROM flyway_schema_history WHERE version = '3';

COMMIT;
