-- Entity: com.wanderly.backend.model.TripMember
-- Undo: db/undo/U10__trip_member_hidden.sql
--
-- Members with an account see a saved group trip in their own list (read-only). Leaving it only
-- hides it from them: the plan, its tags and its fairness stay as they were for everyone else.

ALTER TABLE trip_members ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;

-- "Saved trips I'm a member of": looked up by user, not by trip.
CREATE INDEX idx_trip_members_user ON trip_members (user_id) WHERE user_id IS NOT NULL;
