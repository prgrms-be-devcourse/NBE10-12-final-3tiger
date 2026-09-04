-- User block schema migration
-- user_block 테이블 추가 (상호 차단). user_memo 구조를 그대로 복제한다.
-- IF NOT EXISTS 로 멱등 처리 (재실행 안전)

CREATE TABLE IF NOT EXISTS user_block (
    user_block_id BIGSERIAL PRIMARY KEY,
    blocker_id    BIGINT    NOT NULL REFERENCES "user"(user_id) ON DELETE CASCADE,
    blocked_id    BIGINT    NOT NULL REFERENCES "user"(user_id) ON DELETE CASCADE,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_block_blocker_blocked UNIQUE (blocker_id, blocked_id),
    CONSTRAINT chk_user_block_not_self CHECK (blocker_id <> blocked_id)
);

CREATE INDEX IF NOT EXISTS idx_user_block_blocker ON user_block (blocker_id);
CREATE INDEX IF NOT EXISTS idx_user_block_blocked ON user_block (blocked_id);
