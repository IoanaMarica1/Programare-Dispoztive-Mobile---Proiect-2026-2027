-- Reverts db/migration/V1__create_users.sql
-- Flyway Community does not run undo scripts, so run these by hand, newest first
-- (U3 and U2 must already be applied, since visits and preferences reference users):
--   docker exec -i wanderly-db psql -U wanderly -d wanderly_db < src/main/resources/db/undo/U1__create_users.sql

BEGIN;

DROP TABLE IF EXISTS users;

DELETE FROM flyway_schema_history WHERE version = '1';

COMMIT;
