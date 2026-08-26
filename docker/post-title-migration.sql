DO $$
BEGIN
    IF to_regclass('public.share_post') IS NOT NULL THEN
        ALTER TABLE share_post ADD COLUMN IF NOT EXISTS title VARCHAR(200);
        UPDATE share_post SET title = COALESCE(NULLIF(title, ''), LEFT(caption, 200));
        ALTER TABLE share_post ALTER COLUMN title SET NOT NULL;
    END IF;
END $$;
