-- Course 공간 데이터 검증 및 파생값 계산을 쓰기 시점으로 이동한다.
-- 기존 행은 삭제하지 않으며 시작점/종료점만 경로 기준으로 보정한다.
-- 함수, 트리거, 제약조건은 재실행해도 안전하게 구성한다.

BEGIN;

ALTER TABLE public.course
    ADD COLUMN IF NOT EXISTS end_point geometry(Point, 4326);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.course
        WHERE path IS NULL
           OR ST_IsEmpty(path)
           OR NOT ST_IsValid(path)
           OR ST_NPoints(path) < 2
    ) THEN
        RAISE EXCEPTION
            'course.path contains invalid data; clean invalid rows before applying migration';
    END IF;
END
$$;

UPDATE public.course
SET start_point = ST_StartPoint(path),
    end_point = ST_EndPoint(path)
WHERE start_point IS DISTINCT FROM ST_StartPoint(path)
   OR end_point IS DISTINCT FROM ST_EndPoint(path);

CREATE OR REPLACE FUNCTION public.sync_course_path_derived_fields()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_distance_m integer;
BEGIN
    IF NEW.path IS NULL
       OR ST_IsEmpty(NEW.path)
       OR NOT ST_IsValid(NEW.path)
       OR ST_NPoints(NEW.path) < 2 THEN
        RAISE EXCEPTION 'course.path must be a valid, non-empty LineString with at least two points';
    END IF;

    v_distance_m := ROUND(ST_Length(ST_Transform(NEW.path, 5179)))::integer;

    NEW.start_point := ST_StartPoint(NEW.path);
    NEW.end_point := ST_EndPoint(NEW.path);
    NEW.distance_m := v_distance_m;
    NEW.estimated_minutes := CEIL(v_distance_m / 1.2 / 60)::integer;

    RETURN NEW;
END
$$;

DROP TRIGGER IF EXISTS trg_course_path_derived_fields ON public.course;

CREATE TRIGGER trg_course_path_derived_fields
BEFORE INSERT OR UPDATE OF path
ON public.course
FOR EACH ROW
EXECUTE FUNCTION public.sync_course_path_derived_fields();

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.course'::regclass
          AND conname = 'chk_course_path_not_empty'
    ) THEN
        ALTER TABLE public.course
            ADD CONSTRAINT chk_course_path_not_empty
            CHECK (NOT ST_IsEmpty(path)) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.course'::regclass
          AND conname = 'chk_course_path_valid'
    ) THEN
        ALTER TABLE public.course
            ADD CONSTRAINT chk_course_path_valid
            CHECK (ST_IsValid(path)) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.course'::regclass
          AND conname = 'chk_course_path_minimum_points'
    ) THEN
        ALTER TABLE public.course
            ADD CONSTRAINT chk_course_path_minimum_points
            CHECK (ST_NPoints(path) >= 2) NOT VALID;
    END IF;
END
$$;

ALTER TABLE public.course
    VALIDATE CONSTRAINT chk_course_path_not_empty;
ALTER TABLE public.course
    VALIDATE CONSTRAINT chk_course_path_valid;
ALTER TABLE public.course
    VALIDATE CONSTRAINT chk_course_path_minimum_points;

ALTER TABLE public.course
    ALTER COLUMN end_point SET NOT NULL;

COMMIT;
