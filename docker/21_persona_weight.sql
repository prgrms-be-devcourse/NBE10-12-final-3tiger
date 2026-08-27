-- Phase: 페르소나별 지표 가중치 테이블
-- 4 personas × 10 metrics = 40 rows. 각 페르소나별 가중치 합 = 1.0

BEGIN;

CREATE TABLE IF NOT EXISTS public.persona_weight (
    persona VARCHAR(16) NOT NULL,   -- dog | senior | stroller | walker
    metric  VARCHAR(32) NOT NULL,   -- flatness, shade_summer, ...
    weight  NUMERIC(4,3) NOT NULL,
    PRIMARY KEY (persona, metric),
    CONSTRAINT ck_persona_weight_range CHECK (weight >= 0 AND weight <= 1)
);

-- 재실행 안전: 기존 데이터 지우고 다시 로드
DELETE FROM public.persona_weight;

INSERT INTO public.persona_weight (persona, metric, weight) VALUES
    -- walker: 균등 배분 (default, 10 * 0.1 = 1.0)
    ('walker', 'flatness',           0.100),
    ('walker', 'pavement_quality',   0.100),
    ('walker', 'wheelchair',         0.100),
    ('walker', 'traffic_low',        0.100),
    ('walker', 'shade_summer',       0.100),
    ('walker', 'shade_winter_sun',   0.100),
    ('walker', 'surface_natural',    0.100),
    ('walker', 'bench_density',      0.100),
    ('walker', 'restroom_proximity', 0.100),
    ('walker', 'water_facility',     0.100),

    -- senior: 평지·포장·벤치·화장실·낮은교통 위주
    ('senior', 'flatness',           0.200),
    ('senior', 'pavement_quality',   0.200),
    ('senior', 'wheelchair',         0.100),
    ('senior', 'traffic_low',        0.150),
    ('senior', 'shade_summer',       0.100),
    ('senior', 'shade_winter_sun',   0.050),
    ('senior', 'surface_natural',    0.000),
    ('senior', 'bench_density',      0.150),
    ('senior', 'restroom_proximity', 0.050),
    ('senior', 'water_facility',     0.000),

    -- stroller: 평지·포장·휠체어 접근성 절대적
    ('stroller', 'flatness',           0.250),
    ('stroller', 'pavement_quality',   0.250),
    ('stroller', 'wheelchair',         0.200),
    ('stroller', 'traffic_low',        0.150),
    ('stroller', 'shade_summer',       0.050),
    ('stroller', 'shade_winter_sun',   0.000),
    ('stroller', 'surface_natural',    0.000),
    ('stroller', 'bench_density',      0.050),
    ('stroller', 'restroom_proximity', 0.050),
    ('stroller', 'water_facility',     0.000),

    -- dog: 자연 표면·그늘·물시설·낮은 교통
    ('dog', 'flatness',           0.050),
    ('dog', 'pavement_quality',   0.050),
    ('dog', 'wheelchair',         0.000),
    ('dog', 'traffic_low',        0.150),
    ('dog', 'shade_summer',       0.150),
    ('dog', 'shade_winter_sun',   0.050),
    ('dog', 'surface_natural',    0.250),
    ('dog', 'bench_density',      0.050),
    ('dog', 'restroom_proximity', 0.050),
    ('dog', 'water_facility',     0.200);

COMMIT;

-- 검증 1: 각 페르소나별 가중치 합 = 1.000, 지표 개수 = 10
SELECT persona, ROUND(SUM(weight)::numeric, 3) AS total_weight, COUNT(*) AS metric_count
FROM public.persona_weight
GROUP BY persona
ORDER BY persona;

-- 검증 2: 전체 row 수 = 40
SELECT COUNT(*) AS total_rows FROM public.persona_weight;
