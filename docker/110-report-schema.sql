-- Report schema migration
-- report 테이블 추가 + share_post 에 hidden 컬럼 추가
-- IF NOT EXISTS / DO 블록으로 멱등 처리 (재실행 안전)
--
-- 주의: /docker-entrypoint-initdb.d 스크립트는 파일명 사전순으로 실행된다.
-- 즉 "110-..." 은 "30-social-schema" 보다 먼저 돈다.
-- share_post 는 베이스 덤프(10-restore)에 이미 존재하므로 여기서 ALTER 가능하지만,
-- post_comment 는 30-social-schema 에서 생성되므로 post_comment.hidden 은 그쪽에 둔다.

------------------------------------------------------------
-- 1. Report (USER / COMMENT / POST 신고 로그)
------------------------------------------------------------
CREATE TABLE IF NOT EXISTS report (
    id          BIGSERIAL   PRIMARY KEY,
    reporter_id BIGINT      NOT NULL REFERENCES "user"(user_id) ON DELETE CASCADE,
    target_type VARCHAR(20) NOT NULL,
    target_id   BIGINT      NOT NULL,
    reason      VARCHAR(20) NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_report_reporter_target UNIQUE (reporter_id, target_type, target_id),
    CONSTRAINT chk_report_target_type CHECK (target_type IN ('USER', 'COMMENT', 'POST')),
    CONSTRAINT chk_report_reason CHECK (reason IN ('SPAM', 'ABUSE', 'SEXUAL', 'HARASSMENT', 'ILLEGAL', 'ETC'))
);
CREATE INDEX IF NOT EXISTS idx_report_target ON report (target_type, target_id);

------------------------------------------------------------
-- 2. 신고 누적 자동 숨김 대상 컬럼 (share_post)
--    post_comment.hidden 은 30-social-schema.sql 참고
------------------------------------------------------------
ALTER TABLE share_post ADD COLUMN IF NOT EXISTS hidden BOOLEAN NOT NULL DEFAULT FALSE;
