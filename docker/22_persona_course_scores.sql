-- Phase: course_score에 페르소나별 총점 컬럼 추가 + 기존 코스 백필
-- 총점 = Σ(metric_value * persona_weight). NULL metric은 0 취급.

BEGIN;

-- 1) 컬럼 추가 (재실행 안전)
ALTER TABLE public.course_score
    ADD COLUMN IF NOT EXISTS score_walker   NUMERIC(4,3),
    ADD COLUMN IF NOT EXISTS score_senior   NUMERIC(4,3),
    ADD COLUMN IF NOT EXISTS score_stroller NUMERIC(4,3),
    ADD COLUMN IF NOT EXISTS score_dog      NUMERIC(4,3);

COMMENT ON COLUMN public.course_score.score_walker   IS '페르소나 walker 총점 (Σ metric * weight)';
COMMENT ON COLUMN public.course_score.score_senior   IS '페르소나 senior 총점 (Σ metric * weight)';
COMMENT ON COLUMN public.course_score.score_stroller IS '페르소나 stroller 총점 (Σ metric * weight)';
COMMENT ON COLUMN public.course_score.score_dog      IS '페르소나 dog 총점 (Σ metric * weight)';

-- 2) 헬퍼 함수: (persona, course_score row) → 페르소나 총점
--    이후 저장 함수(save_from_geom 등)에서도 재사용
CREATE OR REPLACE FUNCTION public.compute_persona_score(
    p_persona VARCHAR,
    p_cs public.course_score
) RETURNS NUMERIC AS $$
    SELECT ROUND(SUM(COALESCE(v, 0) * pw.weight)::numeric, 3)
      FROM (VALUES
              ('flatness',           p_cs.flatness),
              ('pavement_quality',   p_cs.pavement_quality),
              ('wheelchair',         p_cs.wheelchair),
              ('traffic_low',        p_cs.traffic_low),
              ('shade_summer',       p_cs.shade_summer),
              ('shade_winter_sun',   p_cs.shade_winter_sun),
              ('surface_natural',    p_cs.surface_natural),
              ('bench_density',      p_cs.bench_density),
              ('restroom_proximity', p_cs.restroom_proximity),
              ('water_facility',     p_cs.water_facility)
           ) AS m(metric, v)
      JOIN public.persona_weight pw USING (metric)
     WHERE pw.persona = p_persona;
$$ LANGUAGE sql STABLE;

-- 3) 기존 코스 백필
UPDATE public.course_score cs
   SET score_walker   = public.compute_persona_score('walker',   cs),
       score_senior   = public.compute_persona_score('senior',   cs),
       score_stroller = public.compute_persona_score('stroller', cs),
       score_dog      = public.compute_persona_score('dog',      cs);

COMMIT;

-- 검증: 백필 완료 및 값이 0..1 범위 (모든 metric이 0..1이고 weight 합이 1이므로 총점도 0..1)
SELECT
    COUNT(*)                                      AS total_courses,
    COUNT(score_walker)                           AS with_walker,
    COUNT(score_senior)                           AS with_senior,
    COUNT(score_stroller)                         AS with_stroller,
    COUNT(score_dog)                              AS with_dog,
    ROUND(AVG(score_walker)::numeric, 3)          AS avg_walker,
    ROUND(AVG(score_senior)::numeric, 3)          AS avg_senior,
    ROUND(AVG(score_stroller)::numeric, 3)        AS avg_stroller,
    ROUND(AVG(score_dog)::numeric, 3)             AS avg_dog
FROM public.course_score;

-- 검증: 페르소나별 top 3 코스 (서로 다르게 나와야 정상)
(SELECT 'walker' AS persona, course_id, score_walker AS score
   FROM public.course_score ORDER BY score_walker DESC NULLS LAST LIMIT 3)
UNION ALL
(SELECT 'senior', course_id, score_senior
   FROM public.course_score ORDER BY score_senior DESC NULLS LAST LIMIT 3)
UNION ALL
(SELECT 'stroller', course_id, score_stroller
   FROM public.course_score ORDER BY score_stroller DESC NULLS LAST LIMIT 3)
UNION ALL
(SELECT 'dog', course_id, score_dog
   FROM public.course_score ORDER BY score_dog DESC NULLS LAST LIMIT 3);
