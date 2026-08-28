-- Comment upvote schema migration
-- comment_upvote 테이블에 (comment_id, user_id) UNIQUE 제약 추가
-- IF NOT EXISTS / DO 블록으로 멱등 처리 (재실행 안전)

DO $$
BEGIN
    IF to_regclass('public.comment_upvote') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_comment_upvote_comment_user') THEN
        ALTER TABLE comment_upvote ADD CONSTRAINT uk_comment_upvote_comment_user UNIQUE (comment_id, user_id);
    END IF;
END $$;
