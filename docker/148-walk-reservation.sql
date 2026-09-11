CREATE TABLE IF NOT EXISTS walk_reservation (
    reservation_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(user_id) ON DELETE CASCADE,
    course_id BIGINT NOT NULL REFERENCES course(course_id) ON DELETE CASCADE,
    scheduled_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT walk_reservation_status_check
        CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELED'))
);

CREATE INDEX IF NOT EXISTS idx_walk_reservation_user_status_scheduled
    ON walk_reservation (user_id, status, scheduled_at);

CREATE UNIQUE INDEX IF NOT EXISTS uq_walk_reservation_scheduled
    ON walk_reservation (user_id, course_id, scheduled_at)
    WHERE status = 'SCHEDULED';
