-- Hazard resolution confirmation and reward support.
ALTER TABLE hazard DROP CONSTRAINT IF EXISTS hazard_status_check;
ALTER TABLE hazard ADD CONSTRAINT hazard_status_check CHECK (status IN ('PENDING', 'ACTIVE', 'RESOLVED'));

ALTER TABLE point_history DROP CONSTRAINT IF EXISTS ck_point_history_type;
ALTER TABLE point_history ADD CONSTRAINT ck_point_history_type CHECK (type IN (
    'HAZARD_REPORT', 'HAZARD_ACTIVATED', 'HAZARD_RESOLUTION', 'HAZARD_RESOLVED', 'SHOP_ITEM_PURCHASE'
));

CREATE TABLE IF NOT EXISTS hazard_resolution (
    hazard_resolution_id BIGSERIAL PRIMARY KEY,
    hazard_id BIGINT NOT NULL REFERENCES hazard(hazard_id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES "user"(user_id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_hazard_resolution_hazard_user UNIQUE (hazard_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_hazard_resolution_hazard ON hazard_resolution(hazard_id);
CREATE INDEX IF NOT EXISTS idx_hazard_resolution_user ON hazard_resolution(user_id);
