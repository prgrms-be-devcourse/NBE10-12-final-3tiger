-- Hazard report point reward schema. Additive and safe for existing data.

ALTER TABLE "user"
    ADD COLUMN IF NOT EXISTS point_balance BIGINT NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_user_point_balance_non_negative'
          AND conrelid = '"user"'::regclass
    ) THEN
        ALTER TABLE "user"
            ADD CONSTRAINT ck_user_point_balance_non_negative
            CHECK (point_balance >= 0);
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS point_history (
    point_history_id BIGSERIAL PRIMARY KEY,
    user_id          BIGINT      NOT NULL REFERENCES "user"(user_id),
    amount           BIGINT      NOT NULL CHECK (amount > 0),
    type             VARCHAR(30) NOT NULL
        CHECK (type IN ('HAZARD_REPORT', 'HAZARD_ACTIVATED')),
    reference_id     BIGINT      NOT NULL,
    created_at       TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_point_history_user_type_reference
        UNIQUE (user_id, type, reference_id)
);

CREATE INDEX IF NOT EXISTS idx_point_history_user_type_created_at
    ON point_history (user_id, type, created_at);

CREATE INDEX IF NOT EXISTS idx_point_history_type_reference
    ON point_history (type, reference_id);
