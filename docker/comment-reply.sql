-- post_comment 테이블에 대댓글(답글) 지원을 위한 컬럼 추가
ALTER TABLE post_comment ADD COLUMN IF NOT EXISTS parent_id BIGINT NULL REFERENCES post_comment(id) ON DELETE CASCADE;
ALTER TABLE post_comment ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_post_comment_parent_created ON post_comment (parent_id, created_at);
