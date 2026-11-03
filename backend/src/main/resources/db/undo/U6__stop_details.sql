ALTER TABLE trip_stops
    DROP COLUMN IF EXISTS description,
    DROP COLUMN IF EXISTS opening_hours;
