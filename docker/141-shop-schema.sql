-- Cosmetic shop MVP schema. Preserves existing users and point histories.

ALTER TABLE point_history
    DROP CONSTRAINT IF EXISTS point_history_amount_check;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_point_history_amount_nonzero'
          AND conrelid = 'point_history'::regclass
    ) THEN
        ALTER TABLE point_history
            ADD CONSTRAINT ck_point_history_amount_nonzero CHECK (amount <> 0);
    END IF;
END
$$;

ALTER TABLE point_history
    DROP CONSTRAINT IF EXISTS point_history_type_check;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_point_history_type'
          AND conrelid = 'point_history'::regclass
    ) THEN
        ALTER TABLE point_history
            ADD CONSTRAINT ck_point_history_type
            CHECK (type IN ('HAZARD_REPORT', 'HAZARD_ACTIVATED', 'SHOP_ITEM_PURCHASE'));
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS shop_item (
    shop_item_id BIGSERIAL PRIMARY KEY,
    type         VARCHAR(30)  NOT NULL,
    code         VARCHAR(50)  NOT NULL,
    name         VARCHAR(100) NOT NULL,
    description  VARCHAR(255),
    price        BIGINT       NOT NULL CHECK (price > 0),
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_shop_item_type_code UNIQUE (type, code),
    CONSTRAINT ck_shop_item_type CHECK (type IN ('PROFILE_BORDER', 'POST_BORDER', 'PROFILE_BADGE'))
);

CREATE INDEX IF NOT EXISTS idx_shop_item_active_type
    ON shop_item (active, type);

CREATE TABLE IF NOT EXISTS user_item (
    user_item_id BIGSERIAL PRIMARY KEY,
    user_id      BIGINT    NOT NULL REFERENCES "user"(user_id),
    shop_item_id BIGINT    NOT NULL REFERENCES shop_item(shop_item_id),
    equipped     BOOLEAN   NOT NULL DEFAULT FALSE,
    purchased_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_item_user_shop_item UNIQUE (user_id, shop_item_id)
);

CREATE INDEX IF NOT EXISTS idx_user_item_user_equipped
    ON user_item (user_id, equipped);

INSERT INTO shop_item (type, code, name, description, price, active)
VALUES
    ('PROFILE_BORDER', 'GOLD', '골드 프로필 테두리', '프로필 사진을 골드 테두리로 꾸며요.', 100, TRUE),
    ('PROFILE_BORDER', 'BLUE', '블루 프로필 테두리', '프로필 사진을 블루 테두리로 꾸며요.', 100, TRUE),
    ('PROFILE_BORDER', 'PINK', '핑크 프로필 테두리', '프로필 사진을 핑크 테두리로 꾸며요.', 100, TRUE),
    ('POST_BORDER', 'GOLD', '골드 게시글 테두리', '게시글 카드를 골드 테두리로 꾸며요.', 100, TRUE),
    ('POST_BORDER', 'BLUE', '블루 게시글 테두리', '게시글 카드를 블루 테두리로 꾸며요.', 100, TRUE),
    ('POST_BORDER', 'PINK', '핑크 게시글 테두리', '게시글 카드를 핑크 테두리로 꾸며요.', 100, TRUE),
    ('PROFILE_BADGE', 'STAR', '별 뱃지', '프로필에 반짝이는 별 뱃지를 표시해요.', 50, TRUE),
    ('PROFILE_BADGE', 'LEAF', '나뭇잎 뱃지', '프로필에 싱그러운 나뭇잎 뱃지를 표시해요.', 50, TRUE),
    ('PROFILE_BADGE', 'PAW', '발바닥 뱃지', '프로필에 귀여운 발바닥 뱃지를 표시해요.', 50, TRUE)
ON CONFLICT (type, code) DO NOTHING;
