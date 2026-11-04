-- Entity: com.wanderly.backend.model.Trip
-- Undo: db/undo/U8__trip_saved.sql
--
-- A generated plan is a draft until the traveler saves it; only saved plans are listed.

ALTER TABLE trips ADD COLUMN saved BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_trips_user_saved ON trips (user_id, saved, created_at DESC);
