-- Course hazard report schema migration
-- hazardType / severity values remain strings until their allowed values are defined.

CREATE TABLE IF NOT EXISTS hazard (
    hazard_id    BIGSERIAL     PRIMARY KEY,
    course_id    BIGINT        NOT NULL REFERENCES course(course_id) ON DELETE CASCADE,
    reporter_user_id BIGINT     NOT NULL,
    hazard_type  VARCHAR(50)   NOT NULL,
    severity     VARCHAR(20)   NOT NULL,
    content      VARCHAR(1000) NOT NULL,
    upvote_count INTEGER       NOT NULL DEFAULT 0 CHECK (upvote_count >= 0),
    expires_at   TIMESTAMP     NOT NULL,
    created_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_hazard_reporter
        FOREIGN KEY (reporter_user_id) REFERENCES "user"(user_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_hazard_course_expires_at
    ON hazard (course_id, expires_at ASC);

CREATE INDEX IF NOT EXISTS idx_hazard_reporter
    ON hazard (reporter_user_id);
