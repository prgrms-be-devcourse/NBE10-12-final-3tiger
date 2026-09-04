-- Social domain schema migration
-- bookmark / post_comment / comment_upvote / share_post_like / notification 테이블 추가
-- IF NOT EXISTS 로 멱등 처리 (재실행 안전)

------------------------------------------------------------
-- 1. Bookmark (User <-> Course 저장)
------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bookmark (
    user_id    BIGINT    NOT NULL REFERENCES "user"(user_id)   ON DELETE CASCADE,
    course_id  BIGINT    NOT NULL REFERENCES course(course_id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, course_id)
);
CREATE INDEX IF NOT EXISTS idx_bookmark_course ON bookmark(course_id);

------------------------------------------------------------
-- 2. Post comment (share_post 게시물에 대한 댓글)
------------------------------------------------------------
CREATE TABLE IF NOT EXISTS post_comment (
    id           BIGSERIAL PRIMARY KEY,
    post_id      BIGINT       NOT NULL REFERENCES share_post(post_id) ON DELETE CASCADE,
    user_id      BIGINT       NOT NULL REFERENCES "user"(user_id)     ON DELETE CASCADE,
    content      VARCHAR(1000) NOT NULL,
    upvote_count INTEGER      NOT NULL DEFAULT 0,
    hidden       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_post_comment_post_created
    ON post_comment (post_id, created_at DESC);
-- 신고 누적 자동 숨김 대상 컬럼 (report 도메인). 이미 만들어진 DB 재실행 대비 멱등 ALTER.
ALTER TABLE post_comment ADD COLUMN IF NOT EXISTS hidden BOOLEAN NOT NULL DEFAULT FALSE;

------------------------------------------------------------
-- 3. Comment upvote (댓글 공감)
------------------------------------------------------------
CREATE TABLE IF NOT EXISTS comment_upvote (
    comment_id BIGINT    NOT NULL REFERENCES post_comment(id)   ON DELETE CASCADE,
    user_id    BIGINT    NOT NULL REFERENCES "user"(user_id)    ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (comment_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_comment_upvote_user ON comment_upvote(user_id);

------------------------------------------------------------
-- 4. Share post like (좋아요)
------------------------------------------------------------
CREATE TABLE IF NOT EXISTS share_post_like (
    post_id    BIGINT    NOT NULL REFERENCES share_post(post_id) ON DELETE CASCADE,
    user_id    BIGINT    NOT NULL REFERENCES "user"(user_id)     ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (post_id, user_id)
);
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_post_like_post_user') THEN
        ALTER TABLE share_post_like
            ADD CONSTRAINT uk_post_like_post_user UNIQUE (post_id, user_id);
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_share_post_like_user ON share_post_like(user_id);

------------------------------------------------------------
-- 5. Notification (좋아요/댓글/댓글공감 실시간 알림)
------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notification (
    id                      BIGSERIAL    PRIMARY KEY,
    receiver_id             BIGINT       NOT NULL REFERENCES "user"(user_id)       ON DELETE CASCADE,
    actor_id                BIGINT       NOT NULL REFERENCES "user"(user_id)       ON DELETE CASCADE,
    actor_nickname          VARCHAR(50)  NOT NULL,
    actor_profile_image_url VARCHAR(2048),
    type                    VARCHAR(20)  NOT NULL,
    post_id                 BIGINT       NOT NULL REFERENCES share_post(post_id)   ON DELETE CASCADE,
    comment_id              BIGINT       REFERENCES post_comment(id)               ON DELETE CASCADE,
    is_read                 BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_notification_type CHECK (type IN ('LIKE', 'COMMENT', 'COMMENT_UPVOTE'))
);
CREATE INDEX IF NOT EXISTS idx_notification_receiver_created_at
    ON notification (receiver_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notification_receiver_unread
    ON notification (receiver_id) WHERE is_read = FALSE;
CREATE INDEX IF NOT EXISTS idx_notification_actor    ON notification (actor_id);
CREATE INDEX IF NOT EXISTS idx_notification_post     ON notification (post_id);
CREATE INDEX IF NOT EXISTS idx_notification_comment  ON notification (comment_id);
