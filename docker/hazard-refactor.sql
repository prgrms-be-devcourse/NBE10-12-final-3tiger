-- Hazard feature schema replacement.
-- The feature has not been merged yet, so legacy hazard/upvote rows are intentionally not migrated.
-- Only Hazard-owned tables are replaced; Course/User and other domain tables are untouched.

DROP TABLE IF EXISTS hazard_confirmation;
DROP TABLE IF EXISTS hazard_report;
DROP TABLE IF EXISTS hazard CASCADE;

CREATE TABLE hazard (
    hazard_id   BIGSERIAL   PRIMARY KEY,
    course_id   BIGINT      NOT NULL REFERENCES course(course_id) ON DELETE CASCADE,
    hazard_type VARCHAR(50) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACTIVE')),
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    activated_at TIMESTAMP
);

CREATE TABLE hazard_report (
    hazard_report_id BIGSERIAL     PRIMARY KEY,
    hazard_id        BIGINT        NOT NULL REFERENCES hazard(hazard_id) ON DELETE CASCADE,
    reporter_user_id BIGINT        NOT NULL REFERENCES "user"(user_id) ON DELETE CASCADE,
    severity         VARCHAR(20)   NOT NULL,
    content          VARCHAR(1000) NOT NULL,
    latitude         DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude        DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_hazard_report_hazard_reporter
        UNIQUE (hazard_id, reporter_user_id)
);

CREATE TABLE hazard_confirmation (
    hazard_confirmation_id BIGSERIAL PRIMARY KEY,
    hazard_id BIGINT NOT NULL REFERENCES hazard(hazard_id) ON DELETE CASCADE,
    user_id   BIGINT NOT NULL REFERENCES "user"(user_id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_hazard_confirmation_hazard_user
        UNIQUE (hazard_id, user_id)
);

CREATE INDEX idx_hazard_course_status_created_at
    ON hazard (course_id, status, created_at DESC);

CREATE INDEX idx_hazard_report_reporter
    ON hazard_report (reporter_user_id);

CREATE INDEX idx_hazard_confirmation_user
    ON hazard_confirmation (user_id);
