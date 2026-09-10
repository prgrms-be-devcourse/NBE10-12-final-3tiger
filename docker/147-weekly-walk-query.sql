CREATE INDEX IF NOT EXISTS idx_course_usage_log_user_used_at
    ON course_usage_log (user_id, used_at DESC);
