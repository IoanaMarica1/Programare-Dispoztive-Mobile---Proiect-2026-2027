-- Reverts db/migration/V5__create_trips.sql
-- Flyway Community does not run undo scripts, so run these by hand, newest first:
--   docker exec -i wanderly-db psql -U wanderly -d wanderly_db < src/main/resources/db/undo/U5__create_trips.sql
--
-- Dropping trip_stops also removes what V6 and V7 added, so their history rows go too
-- (in case U7/U6 were not run first); otherwise Flyway would skip them when V5 is re-applied.

BEGIN;

DROP TABLE IF EXISTS trip_stops;
DROP TABLE IF EXISTS trips;

DELETE FROM flyway_schema_history WHERE version IN ('5', '6', '7');

COMMIT;
