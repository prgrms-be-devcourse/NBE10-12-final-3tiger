ALTER TABLE public.course
    ADD COLUMN IF NOT EXISTS map_image_status VARCHAR(20);

UPDATE public.course
   SET map_image_status = 'COMPLETED'
 WHERE map_image_url IS NOT NULL
   AND map_image_status IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conname = 'course_map_image_status_check'
           AND conrelid = 'public.course'::regclass
    ) THEN
        ALTER TABLE public.course
            ADD CONSTRAINT course_map_image_status_check
            CHECK (map_image_status IN ('PENDING', 'COMPLETED', 'FAILED'));
    END IF;
END
$$;
