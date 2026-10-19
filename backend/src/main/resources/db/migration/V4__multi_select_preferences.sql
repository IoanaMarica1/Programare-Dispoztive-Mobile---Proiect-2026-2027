-- Entity: com.wanderly.backend.model.Preferences (travelModes, priorities and paces collections)
-- Undo: db/undo/U4__multi_select_preferences.sql
--
-- Every wizard question now accepts several answers, so travel mode, priority and pace
-- move from single columns into collection tables, like preference_interests.

CREATE TABLE preference_travel_modes (
    preferences_id BIGINT      NOT NULL,
    travel_mode    VARCHAR(20) NOT NULL,

    CONSTRAINT pk_preference_travel_modes PRIMARY KEY (preferences_id, travel_mode),
    CONSTRAINT fk_preference_travel_modes_preferences
        FOREIGN KEY (preferences_id) REFERENCES preferences (id) ON DELETE CASCADE,
    CONSTRAINT ck_preference_travel_modes_travel_mode
        CHECK (travel_mode IN ('SOLO', 'COUPLE', 'FRIENDS', 'FAMILY'))
);

CREATE TABLE preference_priorities (
    preferences_id BIGINT      NOT NULL,
    priority       VARCHAR(20) NOT NULL,

    CONSTRAINT pk_preference_priorities PRIMARY KEY (preferences_id, priority),
    CONSTRAINT fk_preference_priorities_preferences
        FOREIGN KEY (preferences_id) REFERENCES preferences (id) ON DELETE CASCADE,
    CONSTRAINT ck_preference_priorities_priority
        CHECK (priority IN ('SOCIAL', 'RELAXATION', 'CULTURE', 'BUDGET'))
);

CREATE TABLE preference_paces (
    preferences_id BIGINT      NOT NULL,
    pace           VARCHAR(20) NOT NULL,

    CONSTRAINT pk_preference_paces PRIMARY KEY (preferences_id, pace),
    CONSTRAINT fk_preference_paces_preferences
        FOREIGN KEY (preferences_id) REFERENCES preferences (id) ON DELETE CASCADE,
    CONSTRAINT ck_preference_paces_pace
        CHECK (pace IN ('RELAXED', 'BALANCED', 'PACKED'))
);

-- Keep the answers saved so far.
INSERT INTO preference_travel_modes (preferences_id, travel_mode)
SELECT id, travel_mode FROM preferences;

INSERT INTO preference_priorities (preferences_id, priority)
SELECT id, priority FROM preferences WHERE priority IS NOT NULL;

INSERT INTO preference_paces (preferences_id, pace)
SELECT id, pace FROM preferences WHERE pace IS NOT NULL;

ALTER TABLE preferences
    DROP COLUMN travel_mode,
    DROP COLUMN priority,
    DROP COLUMN pace;
