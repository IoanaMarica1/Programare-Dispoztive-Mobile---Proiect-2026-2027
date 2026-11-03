-- Entity: com.wanderly.backend.model.TripStop
-- Undo: db/undo/U7__trip_stop_ids.sql
--
-- Stops get a public id so the app can swap or remove one, and the per-day order
-- constraint is checked at commit: re-planning a day renumbers its stops, and checking
-- each row on its own would clash mid-update (e.g. two stops trading places).

ALTER TABLE trip_stops ADD COLUMN public_id UUID;
UPDATE trip_stops SET public_id = gen_random_uuid();
ALTER TABLE trip_stops
    ALTER COLUMN public_id SET NOT NULL,
    ADD CONSTRAINT uk_trip_stops_public_id UNIQUE (public_id);

ALTER TABLE trip_stops DROP CONSTRAINT uk_trip_stops_order;
ALTER TABLE trip_stops
    ADD CONSTRAINT uk_trip_stops_order UNIQUE (trip_id, day_number, position) DEFERRABLE INITIALLY DEFERRED;
