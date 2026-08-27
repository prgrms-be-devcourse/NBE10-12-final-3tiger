-- Phase: walk_edges에 페르소나별 cost 사전계산
-- grid_score_5179 캐시에 페르소나별 가중평균 컬럼 추가 → 엣지 중점 매칭으로 cost UPDATE.
-- 페르소나 가중치 합 = 1.0 이므로 avg_score_{persona} ∈ [0,1]. 기존 avg_score(단순 평균)도 보존.

BEGIN;

-- 1) grid_score_5179 재구성: 기존 avg_score + 페르소나별 4개
DROP TABLE IF EXISTS routing.grid_score_5179;
CREATE TABLE routing.grid_score_5179 AS
WITH pw AS (
    SELECT
        persona,
        MAX(CASE WHEN metric='flatness'           THEN weight END) AS w_flatness,
        MAX(CASE WHEN metric='shade_summer'       THEN weight END) AS w_shade_summer,
        MAX(CASE WHEN metric='shade_winter_sun'   THEN weight END) AS w_shade_winter_sun,
        MAX(CASE WHEN metric='traffic_low'        THEN weight END) AS w_traffic_low,
        MAX(CASE WHEN metric='wheelchair'         THEN weight END) AS w_wheelchair,
        MAX(CASE WHEN metric='surface_natural'    THEN weight END) AS w_surface_natural,
        MAX(CASE WHEN metric='bench_density'      THEN weight END) AS w_bench_density,
        MAX(CASE WHEN metric='restroom_proximity' THEN weight END) AS w_restroom_proximity,
        MAX(CASE WHEN metric='water_facility'     THEN weight END) AS w_water_facility,
        MAX(CASE WHEN metric='pavement_quality'   THEN weight END) AS w_pavement_quality
    FROM public.persona_weight
    GROUP BY persona
),
pw_walker   AS (SELECT * FROM pw WHERE persona='walker'),
pw_senior   AS (SELECT * FROM pw WHERE persona='senior'),
pw_stroller AS (SELECT * FROM pw WHERE persona='stroller'),
pw_dog      AS (SELECT * FROM pw WHERE persona='dog')
SELECT
    gs.grid_id, gs.region_code,
    ST_Transform(gs.geom, 5179) AS geom5179,
    -- Backward-compatible: 단순 평균 (기존 11_route_score_cost.sql 동작 보존)
    (COALESCE(gs.flatness,0) + COALESCE(gs.shade_summer,0) + COALESCE(gs.shade_winter_sun,0)
     + COALESCE(gs.traffic_low,0) + COALESCE(gs.wheelchair,0) + COALESCE(gs.surface_natural,0)
     + COALESCE(gs.bench_density,0) + COALESCE(gs.restroom_proximity,0)
     + COALESCE(gs.water_facility,0) + COALESCE(gs.pavement_quality,0)) / 10.0 AS avg_score,
    -- 페르소나별 가중평균 (weight 합 = 1)
    (COALESCE(gs.flatness,0)*w.w_flatness + COALESCE(gs.shade_summer,0)*w.w_shade_summer
     + COALESCE(gs.shade_winter_sun,0)*w.w_shade_winter_sun + COALESCE(gs.traffic_low,0)*w.w_traffic_low
     + COALESCE(gs.wheelchair,0)*w.w_wheelchair + COALESCE(gs.surface_natural,0)*w.w_surface_natural
     + COALESCE(gs.bench_density,0)*w.w_bench_density + COALESCE(gs.restroom_proximity,0)*w.w_restroom_proximity
     + COALESCE(gs.water_facility,0)*w.w_water_facility + COALESCE(gs.pavement_quality,0)*w.w_pavement_quality
    ) AS avg_score_walker,
    (COALESCE(gs.flatness,0)*s.w_flatness + COALESCE(gs.shade_summer,0)*s.w_shade_summer
     + COALESCE(gs.shade_winter_sun,0)*s.w_shade_winter_sun + COALESCE(gs.traffic_low,0)*s.w_traffic_low
     + COALESCE(gs.wheelchair,0)*s.w_wheelchair + COALESCE(gs.surface_natural,0)*s.w_surface_natural
     + COALESCE(gs.bench_density,0)*s.w_bench_density + COALESCE(gs.restroom_proximity,0)*s.w_restroom_proximity
     + COALESCE(gs.water_facility,0)*s.w_water_facility + COALESCE(gs.pavement_quality,0)*s.w_pavement_quality
    ) AS avg_score_senior,
    (COALESCE(gs.flatness,0)*st.w_flatness + COALESCE(gs.shade_summer,0)*st.w_shade_summer
     + COALESCE(gs.shade_winter_sun,0)*st.w_shade_winter_sun + COALESCE(gs.traffic_low,0)*st.w_traffic_low
     + COALESCE(gs.wheelchair,0)*st.w_wheelchair + COALESCE(gs.surface_natural,0)*st.w_surface_natural
     + COALESCE(gs.bench_density,0)*st.w_bench_density + COALESCE(gs.restroom_proximity,0)*st.w_restroom_proximity
     + COALESCE(gs.water_facility,0)*st.w_water_facility + COALESCE(gs.pavement_quality,0)*st.w_pavement_quality
    ) AS avg_score_stroller,
    (COALESCE(gs.flatness,0)*d.w_flatness + COALESCE(gs.shade_summer,0)*d.w_shade_summer
     + COALESCE(gs.shade_winter_sun,0)*d.w_shade_winter_sun + COALESCE(gs.traffic_low,0)*d.w_traffic_low
     + COALESCE(gs.wheelchair,0)*d.w_wheelchair + COALESCE(gs.surface_natural,0)*d.w_surface_natural
     + COALESCE(gs.bench_density,0)*d.w_bench_density + COALESCE(gs.restroom_proximity,0)*d.w_restroom_proximity
     + COALESCE(gs.water_facility,0)*d.w_water_facility + COALESCE(gs.pavement_quality,0)*d.w_pavement_quality
    ) AS avg_score_dog
FROM public.grid_score gs
CROSS JOIN pw_walker   w
CROSS JOIN pw_senior   s
CROSS JOIN pw_stroller st
CROSS JOIN pw_dog      d
WHERE gs.region_code IN ('11500','11470');

CREATE INDEX idx_gs5179_geom ON routing.grid_score_5179 USING GIST (geom5179);

-- 2) walk_edges에 페르소나별 cost 컬럼 추가 (재실행 안전)
ALTER TABLE routing.walk_edges
    ADD COLUMN IF NOT EXISTS cost_walker           NUMERIC,
    ADD COLUMN IF NOT EXISTS cost_senior           NUMERIC,
    ADD COLUMN IF NOT EXISTS cost_stroller         NUMERIC,
    ADD COLUMN IF NOT EXISTS cost_dog              NUMERIC,
    ADD COLUMN IF NOT EXISTS reverse_cost_walker   NUMERIC,
    ADD COLUMN IF NOT EXISTS reverse_cost_senior   NUMERIC,
    ADD COLUMN IF NOT EXISTS reverse_cost_stroller NUMERIC,
    ADD COLUMN IF NOT EXISTS reverse_cost_dog      NUMERIC;

-- 3) 엣지 중점(5179) 캐시 보장 (기존 11 단계에서 채워졌을 수 있음)
ALTER TABLE routing.walk_edges ADD COLUMN IF NOT EXISTS mid_5179 geometry(Point,5179);
UPDATE routing.walk_edges
   SET mid_5179 = ST_LineInterpolatePoint(geom_5179, 0.5)
 WHERE mid_5179 IS NULL;
CREATE INDEX IF NOT EXISTS idx_walk_edges_mid ON routing.walk_edges USING GIST (mid_5179);

-- 4) 페르소나별 cost UPDATE (양방향 동일 가정: 보행 그래프)
UPDATE routing.walk_edges e
   SET cost_walker           = e.length_m / (0.1 + COALESCE(g.avg_score_walker,   0.3)),
       cost_senior           = e.length_m / (0.1 + COALESCE(g.avg_score_senior,   0.3)),
       cost_stroller         = e.length_m / (0.1 + COALESCE(g.avg_score_stroller, 0.3)),
       cost_dog              = e.length_m / (0.1 + COALESCE(g.avg_score_dog,      0.3)),
       reverse_cost_walker   = e.length_m / (0.1 + COALESCE(g.avg_score_walker,   0.3)),
       reverse_cost_senior   = e.length_m / (0.1 + COALESCE(g.avg_score_senior,   0.3)),
       reverse_cost_stroller = e.length_m / (0.1 + COALESCE(g.avg_score_stroller, 0.3)),
       reverse_cost_dog      = e.length_m / (0.1 + COALESCE(g.avg_score_dog,      0.3))
  FROM routing.grid_score_5179 g
 WHERE ST_Contains(g.geom5179, e.mid_5179);

COMMIT;

-- 검증 1: 엣지 커버리지 (기존 cost와 유사한 커버리지)
SELECT
    COUNT(*)                 AS edges,
    COUNT(cost_walker)       AS with_walker,
    COUNT(cost_senior)       AS with_senior,
    COUNT(cost_stroller)     AS with_stroller,
    COUNT(cost_dog)          AS with_dog
FROM routing.walk_edges;

-- 검증 2: 페르소나별 cost 분포 — 서로 다르게 나와야 정상
SELECT persona, ROUND(MIN(c)::numeric,1) AS min, ROUND(AVG(c)::numeric,1) AS avg, ROUND(MAX(c)::numeric,1) AS max
  FROM (
    SELECT 'walker'   AS persona, cost_walker   AS c FROM routing.walk_edges WHERE cost_walker IS NOT NULL
    UNION ALL
    SELECT 'senior',   cost_senior             FROM routing.walk_edges WHERE cost_senior IS NOT NULL
    UNION ALL
    SELECT 'stroller', cost_stroller           FROM routing.walk_edges WHERE cost_stroller IS NOT NULL
    UNION ALL
    SELECT 'dog',      cost_dog                FROM routing.walk_edges WHERE cost_dog IS NOT NULL
  ) t
 GROUP BY persona
 ORDER BY persona;
