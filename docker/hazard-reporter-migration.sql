-- Add the reporter relationship to an existing hazard table.
-- Existing rows cannot be assigned to an arbitrary user, so the column remains nullable
-- only while unowned legacy rows exist. Re-run this migration after backfilling them.

ALTER TABLE hazard
    ADD COLUMN IF NOT EXISTS reporter_user_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_hazard_reporter'
          AND conrelid = 'hazard'::regclass
    ) THEN
        ALTER TABLE hazard
            ADD CONSTRAINT fk_hazard_reporter
            FOREIGN KEY (reporter_user_id) REFERENCES "user"(user_id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_hazard_reporter
    ON hazard (reporter_user_id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM hazard WHERE reporter_user_id IS NULL) THEN
        ALTER TABLE hazard ALTER COLUMN reporter_user_id SET NOT NULL;
    END IF;
END $$;
