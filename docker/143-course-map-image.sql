-- Generated course map image URL. Existing courses remain valid with a NULL image.
ALTER TABLE public.course
    ADD COLUMN IF NOT EXISTS map_image_url VARCHAR(2048);
