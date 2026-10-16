-- Sample data for local development. Loaded only with the "dev" profile
-- (see application-dev.properties); never part of the real schema migrations.
-- Undo: db/undo/U__seed_sample_data.sql
--
-- Repeatable migration: Flyway re-runs it whenever this file changes. It first
-- deletes the sample users (cascading to their preferences and visits), so edits
-- here replace the old sample rows instead of duplicating them.
--
-- Every sample user logs in with the password "parola123".
-- Points equal 10 x the user's visits (VisitService.FIRST_VISIT_POINTS).
-- profile_picture stays NULL: no avatar files exist for these users.

DELETE FROM users WHERE email IN (
    'ana.pop@example.com',
    'mihai.ionescu@example.com',
    'elena.dumitru@example.com',
    'andrei.popescu@example.com',
    'ioana.marin@example.com',
    'radu.stan@example.com'
);

-- ---------- users ----------

INSERT INTO users (public_id, email, password_hash, name, profile_picture, bio, address, age, points, role)
VALUES
    ('a1000000-0000-4000-8000-000000000001', 'ana.pop@example.com',
     '$2a$10$K7IAyI1pCDszWhppqNNlAuvtPyZBnANnaCP//3Nm4Up7kBa8UY//m',
     'Ana Pop', NULL, 'Iubesc istoria și castelele vechi.', 'Cluj-Napoca', 26, 20, 'USER'),

    ('a1000000-0000-4000-8000-000000000002', 'mihai.ionescu@example.com',
     '$2a$10$K7IAyI1pCDszWhppqNNlAuvtPyZBnANnaCP//3Nm4Up7kBa8UY//m',
     'Mihai Ionescu', NULL, 'Administrator Wanderly și pasionat de arhitectură.', 'București', 31, 10, 'ADMIN'),

    ('a1000000-0000-4000-8000-000000000003', 'elena.dumitru@example.com',
     '$2a$10$K7IAyI1pCDszWhppqNNlAuvtPyZBnANnaCP//3Nm4Up7kBa8UY//m',
     'Elena Dumitru', NULL, 'Muzee de artă, cafenele și plimbări lungi.', 'Iași', 24, 10, 'USER'),

    ('a1000000-0000-4000-8000-000000000004', 'andrei.popescu@example.com',
     '$2a$10$K7IAyI1pCDszWhppqNNlAuvtPyZBnANnaCP//3Nm4Up7kBa8UY//m',
     'Andrei Popescu', NULL, 'Călătoresc cu prietenii, mereu în căutare de mâncare bună.', 'Timișoara', 29, 10, 'USER'),

    ('a1000000-0000-4000-8000-000000000005', 'ioana.marin@example.com',
     '$2a$10$K7IAyI1pCDszWhppqNNlAuvtPyZBnANnaCP//3Nm4Up7kBa8UY//m',
     'Ioana Marin', NULL, 'Drumeții la munte cu toată familia.', 'Brașov', 35, 10, 'USER'),

    ('a1000000-0000-4000-8000-000000000006', 'radu.stan@example.com',
     '$2a$10$K7IAyI1pCDszWhppqNNlAuvtPyZBnANnaCP//3Nm4Up7kBa8UY//m',
     'Radu Stan', NULL, 'Abia am început să explorez.', 'Constanța', 42, 0, 'USER');

-- ---------- preferences ----------
-- Linked by email and public_id instead of hard-coded ids, so the identity
-- sequences stay untouched. Radu skipped the optional priority and pace steps.

INSERT INTO preferences (public_id, user_id, travel_mode, priority, pace)
SELECT p.public_id::uuid, u.id, p.travel_mode, p.priority, p.pace
FROM (VALUES
    ('b2000000-0000-4000-8000-000000000001', 'ana.pop@example.com',        'SOLO',    'CULTURE',    'BALANCED'),
    ('b2000000-0000-4000-8000-000000000002', 'mihai.ionescu@example.com',  'COUPLE',  'RELAXATION', 'RELAXED'),
    ('b2000000-0000-4000-8000-000000000003', 'elena.dumitru@example.com',  'SOLO',    'BUDGET',     'PACKED'),
    ('b2000000-0000-4000-8000-000000000004', 'andrei.popescu@example.com', 'FRIENDS', 'SOCIAL',     'PACKED'),
    ('b2000000-0000-4000-8000-000000000005', 'ioana.marin@example.com',    'FAMILY',  'RELAXATION', 'BALANCED'),
    ('b2000000-0000-4000-8000-000000000006', 'radu.stan@example.com',      'COUPLE',  NULL,         NULL)
) AS p (public_id, email, travel_mode, priority, pace)
JOIN users u ON u.email = p.email;

INSERT INTO preference_interests (preferences_id, interest)
SELECT pr.id, i.interest
FROM (VALUES
    ('b2000000-0000-4000-8000-000000000001', 'HISTORY'),
    ('b2000000-0000-4000-8000-000000000001', 'ART'),
    ('b2000000-0000-4000-8000-000000000002', 'FOOD'),
    ('b2000000-0000-4000-8000-000000000002', 'HISTORY'),
    ('b2000000-0000-4000-8000-000000000003', 'ART'),
    ('b2000000-0000-4000-8000-000000000003', 'FOOD'),
    ('b2000000-0000-4000-8000-000000000004', 'NIGHTLIFE'),
    ('b2000000-0000-4000-8000-000000000004', 'FOOD'),
    ('b2000000-0000-4000-8000-000000000004', 'ADVENTURE'),
    ('b2000000-0000-4000-8000-000000000005', 'NATURE'),
    ('b2000000-0000-4000-8000-000000000005', 'ADVENTURE'),
    ('b2000000-0000-4000-8000-000000000006', 'NATURE')
) AS i (preferences_public_id, interest)
JOIN preferences pr ON pr.public_id = i.preferences_public_id::uuid;

-- ---------- visits ----------
-- Ana has visits in two countries, so the history shows more than one country.

INSERT INTO visits (public_id, user_id, objective_name, city, country, latitude, longitude, visited_at)
SELECT v.public_id::uuid, u.id, v.objective_name, v.city, v.country, v.latitude, v.longitude, v.visited_at::timestamptz
FROM (VALUES
    ('c3000000-0000-4000-8000-000000000001', 'ana.pop@example.com',
     'Castelul Bran',          'Bran',      'Romania', 45.5152, 25.3672, '2026-08-14 11:20:00+03'),
    ('c3000000-0000-4000-8000-000000000002', 'ana.pop@example.com',
     'Colosseum',              'Roma',      'Italy',   41.8902, 12.4922, '2026-09-03 10:05:00+02'),
    ('c3000000-0000-4000-8000-000000000003', 'mihai.ionescu@example.com',
     'Palatul Parlamentului',  'București', 'Romania', 44.4275, 26.0875, '2026-07-22 15:40:00+03'),
    ('c3000000-0000-4000-8000-000000000004', 'elena.dumitru@example.com',
     'Turnul Eiffel',          'Paris',     'France',  48.8584,  2.2945, '2026-09-18 19:30:00+02'),
    ('c3000000-0000-4000-8000-000000000005', 'andrei.popescu@example.com',
     'Sagrada Família',        'Barcelona', 'Spain',   41.4036,  2.1744, '2026-08-29 13:15:00+02'),
    ('c3000000-0000-4000-8000-000000000006', 'ioana.marin@example.com',
     'Biserica Neagră',        'Brașov',    'Romania', 45.6409, 25.5881, '2026-10-04 12:00:00+03')
) AS v (public_id, email, objective_name, city, country, latitude, longitude, visited_at)
JOIN users u ON u.email = v.email;
