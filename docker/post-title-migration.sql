ALTER TABLE share_post
    ADD COLUMN IF NOT EXISTS title VARCHAR(100);

UPDATE share_post post
SET title = course.name
FROM course
WHERE post.course_id = course.course_id
  AND post.title IS NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM share_post WHERE title IS NULL) THEN
        RAISE EXCEPTION 'share_post.title backfill failed: NULL title remains';
    END IF;

    ALTER TABLE share_post
        ALTER COLUMN title SET NOT NULL;
END $$;
