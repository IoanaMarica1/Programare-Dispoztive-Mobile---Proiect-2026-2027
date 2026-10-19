-- Reverts db/migration/V4__multi_select_preferences.sql
-- Flyway Community does not run undo scripts, so run these by hand, newest first:
--   docker exec -i wanderly-db psql -U wanderly -d wanderly_db < src/main/resources/db/undo/U4__multi_select_preferences.sql
--
-- The old columns hold a single answer, so only one of several saved answers survives
-- (the first one alphabetically). Travelers without a travel mode get SOLO, since the
-- column is NOT NULL.

BEGIN;

ALTER TABLE preferences
    ADD COLUMN travel_mode VARCHAR(20),
    ADD COLUMN priority    VARCHAR(20),
    ADD COLUMN pace        VARCHAR(20);

UPDATE preferences p SET
    travel_mode = COALESCE((SELECT MIN(t.travel_mode) FROM preference_travel_modes t WHERE t.preferences_id = p.id), 'SOLO'),
    priority    = (SELECT MIN(r.priority) FROM preference_priorities r WHERE r.preferences_id = p.id),
    pace        = (SELECT MIN(c.pace) FROM preference_paces c WHERE c.preferences_id = p.id);

ALTER TABLE preferences
    ALTER COLUMN travel_mode SET NOT NULL,
    ADD CONSTRAINT ck_preferences_travel_mode CHECK (travel_mode IN ('SOLO', 'COUPLE', 'FRIENDS', 'FAMILY')),
    ADD CONSTRAINT ck_preferences_priority    CHECK (priority IN ('SOCIAL', 'RELAXATION', 'CULTURE', 'BUDGET')),
    ADD CONSTRAINT ck_preferences_pace        CHECK (pace IN ('RELAXED', 'BALANCED', 'PACKED'));

DROP TABLE IF EXISTS preference_paces;
DROP TABLE IF EXISTS preference_priorities;
DROP TABLE IF EXISTS preference_travel_modes;

DELETE FROM flyway_schema_history WHERE version = '4';

COMMIT;
