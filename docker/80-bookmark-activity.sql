-- Bookmark activity extension: personal rating and course usage history

ALTER TABLE bookmark
    ADD COLUMN IF NOT EXISTS rating SMALLINT,
    ADD COLUMN IF NOT EXISTS usage_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_used_at TIMESTAMP;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_bookmark_rating') THEN
        ALTER TABLE bookmark
            ADD CONSTRAINT chk_bookmark_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS course_usage_log (
    usage_log_id BIGSERIAL PRIMARY KEY,
    user_id      BIGINT    NOT NULL REFERENCES "user"(user_id)   ON DELETE CASCADE,
    course_id    BIGINT    NOT NULL REFERENCES course(course_id) ON DELETE CASCADE,
    used_at      TIMESTAMP NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_course_usage_log_user_course_used
    ON course_usage_log (user_id, course_id, used_at DESC);
