-- Post like schema migration
-- share_post_like 테이블에 (post_id, user_id) UNIQUE 제약 추가
-- IF NOT EXISTS / DO 블록으로 멱등 처리 (재실행 안전)

DO $$
BEGIN
    IF to_regclass('public.share_post_like') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_post_like_post_user') THEN
        ALTER TABLE share_post_like ADD CONSTRAINT uk_post_like_post_user UNIQUE (post_id, user_id);
    END IF;
END $$;
