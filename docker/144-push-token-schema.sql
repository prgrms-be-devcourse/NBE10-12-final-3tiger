-- Push token schema migration
-- OS 레벨 푸시(FCM/Expo) 발송 대상 기기 토큰. token 을 유니크 키로 삼아
-- 한 기기가 항상 현재 로그인된 사용자 한 명에게만 매핑되도록 한다.
-- IF NOT EXISTS 로 멱등 처리 (재실행 안전)

CREATE TABLE IF NOT EXISTS push_token (
    push_token_id BIGSERIAL    PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES "user"(user_id) ON DELETE CASCADE,
    token         VARCHAR(512) NOT NULL,
    platform      VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_push_token_token UNIQUE (token)
);

CREATE INDEX IF NOT EXISTS idx_push_token_user ON push_token (user_id);
