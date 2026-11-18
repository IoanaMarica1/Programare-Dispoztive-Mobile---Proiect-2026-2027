-- Reverts db/migration/V9__trip_members.sql
-- Flyway Community does not run undo scripts, so run these by hand, newest first:
--   docker exec -i wanderly-db psql -U wanderly -d wanderly_db < src/main/resources/db/undo/U9__trip_members.sql

BEGIN;

DROP TABLE IF EXISTS trip_member_paces;
DROP TABLE IF EXISTS trip_member_priorities;
DROP TABLE IF EXISTS trip_member_interests;
DROP TABLE IF EXISTS trip_members;

DELETE FROM flyway_schema_history WHERE version = '9';

COMMIT;
