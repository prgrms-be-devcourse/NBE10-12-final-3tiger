CREATE TABLE IF NOT EXISTS user_memo (
    user_memo_id   BIGSERIAL PRIMARY KEY,
    owner_user_id  BIGINT NOT NULL REFERENCES "user"(user_id) ON DELETE CASCADE,
    target_user_id BIGINT NOT NULL REFERENCES "user"(user_id) ON DELETE CASCADE,
    tags           JSONB NOT NULL DEFAULT '[]'::jsonb,
    memo           VARCHAR(1000),
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_memo_owner_target UNIQUE (owner_user_id, target_user_id),
    CONSTRAINT chk_user_memo_not_self CHECK (owner_user_id <> target_user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_memo_owner_target
    ON user_memo (owner_user_id, target_user_id);
