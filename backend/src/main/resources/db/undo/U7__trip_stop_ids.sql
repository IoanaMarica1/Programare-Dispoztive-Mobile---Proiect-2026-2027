-- Reverts db/migration/V7__trip_stop_ids.sql
-- Flyway Community does not run undo scripts, so run these by hand, newest first:
--   docker exec -i wanderly-db psql -U wanderly -d wanderly_db < src/main/resources/db/undo/U7__trip_stop_ids.sql

BEGIN;

ALTER TABLE trip_stops DROP CONSTRAINT IF EXISTS uk_trip_stops_order;
ALTER TABLE trip_stops ADD CONSTRAINT uk_trip_stops_order UNIQUE (trip_id, day_number, position);

ALTER TABLE trip_stops
    DROP CONSTRAINT IF EXISTS uk_trip_stops_public_id,
    DROP COLUMN IF EXISTS public_id;

DELETE FROM flyway_schema_history WHERE version = '7';

COMMIT;
