-- Testcontainer 픽스처: CourseRepository 페르소나 정렬 검증용
-- 최소 스키마 + persona_weight + 3개 코스 (page_persona별로 top이 달라져야 함)

CREATE EXTENSION IF NOT EXISTS postgis;

DROP TABLE IF EXISTS course_score;
DROP TABLE IF EXISTS course;
DROP TABLE IF EXISTS persona_weight;

-- persona_weight (매트릭스 4×10)
CREATE TABLE persona_weight (
    persona VARCHAR(16) NOT NULL,
    metric  VARCHAR(32) NOT NULL,
    weight  NUMERIC(4,3) NOT NULL,
    PRIMARY KEY (persona, metric)
);

INSERT INTO persona_weight VALUES
    ('walker',   'flatness',           0.100),
    ('walker',   'pavement_quality',   0.100),
    ('walker',   'wheelchair',         0.100),
    ('walker',   'traffic_low',        0.100),
    ('walker',   'shade_summer',       0.100),
    ('walker',   'shade_winter_sun',   0.100),
    ('walker',   'surface_natural',    0.100),
    ('walker',   'bench_density',      0.100),
    ('walker',   'restroom_proximity', 0.100),
    ('walker',   'water_facility',     0.100),
    ('senior',   'flatness',           0.200),
    ('senior',   'pavement_quality',   0.200),
    ('senior',   'wheelchair',         0.100),
    ('senior',   'traffic_low',        0.150),
    ('senior',   'shade_summer',       0.100),
    ('senior',   'shade_winter_sun',   0.050),
    ('senior',   'surface_natural',    0.000),
    ('senior',   'bench_density',      0.150),
    ('senior',   'restroom_proximity', 0.050),
    ('senior',   'water_facility',     0.000),
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
    ('dog',      'flatness',           0.050),
    ('dog',      'pavement_quality',   0.050),
    ('dog',      'wheelchair',         0.000),
    ('dog',      'traffic_low',        0.150),
    ('dog',      'shade_summer',       0.150),
    ('dog',      'shade_winter_sun',   0.050),
    ('dog',      'surface_natural',    0.250),
    ('dog',      'bench_density',      0.050),
    ('dog',      'restroom_proximity', 0.050),
    ('dog',      'water_facility',     0.200);

-- course (최소 컬럼)
CREATE TABLE course (
    course_id         BIGINT PRIMARY KEY,
    region_code       VARCHAR(10) NOT NULL,
    name              VARCHAR(200) NOT NULL,
    path              geometry(LineString, 4326),
    start_point       geometry(Point, 4326),
    distance_m        INTEGER NOT NULL,
    estimated_minutes INTEGER NOT NULL,
    elevation_gain_m  INTEGER DEFAULT 0,
    elevation_loss_m  INTEGER DEFAULT 0,
    is_loop           BOOLEAN NOT NULL,
    source            VARCHAR(50),
    like_count        INTEGER DEFAULT 0,
    data_version      VARCHAR(30)
);

-- course_score (지표 10개 + persona 총점 4개 + loop_bonus)
CREATE TABLE course_score (
    course_id          BIGINT PRIMARY KEY REFERENCES course(course_id) ON DELETE CASCADE,
    flatness           NUMERIC(4,3),
    shade_summer       NUMERIC(4,3),
    shade_winter_sun   NUMERIC(4,3),
    traffic_low        NUMERIC(4,3),
    wheelchair         NUMERIC(4,3),
    surface_natural    NUMERIC(4,3),
    bench_density      NUMERIC(4,3),
    restroom_proximity NUMERIC(4,3),
    water_facility     NUMERIC(4,3),
    pavement_quality   NUMERIC(4,3),
    loop_bonus         NUMERIC(4,3),
    score_walker       NUMERIC(4,3),
    score_senior       NUMERIC(4,3),
    score_stroller     NUMERIC(4,3),
    score_dog          NUMERIC(4,3)
);

-- 3개 코스: 페르소나별로 top이 명확히 달라지도록 값 조정
-- Course 101: stroller/senior 친화 (평지+포장+벤치)
-- Course 102: dog 친화 (자연 표면+그늘+물시설)
-- Course 103: 균등 (walker에서 유리)
INSERT INTO course (course_id, region_code, name, start_point, distance_m, estimated_minutes, is_loop, source, like_count) VALUES
    (101, '11500', 'stroller-friendly', ST_SetSRID(ST_MakePoint(126.850, 37.550), 4326), 2000, 30, true, 'test', 10),
    (102, '11500', 'dog-friendly',      ST_SetSRID(ST_MakePoint(126.851, 37.551), 4326), 2000, 30, true, 'test', 5),
    (103, '11500', 'balanced',          ST_SetSRID(ST_MakePoint(126.852, 37.552), 4326), 2000, 30, true, 'test', 20);

INSERT INTO course_score (
    course_id, flatness, shade_summer, shade_winter_sun, traffic_low, wheelchair,
    surface_natural, bench_density, restroom_proximity, water_facility, pavement_quality,
    loop_bonus,
    score_walker, score_senior, score_stroller, score_dog
) VALUES
    -- Course 101: flatness/pavement/wheelchair/bench 매우 높음, 자연/물시설 낮음
    (101, 0.95, 0.30, 0.30, 0.60, 0.95, 0.10, 0.80, 0.60, 0.10, 0.95,
     0.200,
     -- walker (균등 0.1씩): 0.95+0.30+0.30+0.60+0.95+0.10+0.80+0.60+0.10+0.95 = 5.65 * 0.1 = 0.565
     0.565,
     -- senior: 0.95*0.2 + 0.95*0.2 + 0.95*0.1 + 0.60*0.15 + 0.30*0.1 + 0.30*0.05 + 0.10*0 + 0.80*0.15 + 0.60*0.05 + 0.10*0
     --       = 0.190 + 0.190 + 0.095 + 0.090 + 0.030 + 0.015 + 0 + 0.120 + 0.030 + 0 = 0.760
     0.760,
     -- stroller: 0.95*0.25 + 0.95*0.25 + 0.95*0.2 + 0.60*0.15 + 0.30*0.05 + 0.30*0 + 0.10*0 + 0.80*0.05 + 0.60*0.05 + 0.10*0
     --         = 0.2375 + 0.2375 + 0.190 + 0.090 + 0.015 + 0 + 0 + 0.040 + 0.030 + 0 = 0.840
     0.840,
     -- dog: 0.95*0.05 + 0.95*0.05 + 0.95*0 + 0.60*0.15 + 0.30*0.15 + 0.30*0.05 + 0.10*0.25 + 0.80*0.05 + 0.60*0.05 + 0.10*0.2
     --    = 0.0475 + 0.0475 + 0 + 0.090 + 0.045 + 0.015 + 0.025 + 0.040 + 0.030 + 0.020 = 0.360
     0.360),
    -- Course 102: 자연 표면/그늘/물시설 매우 높음, 포장/휠체어 낮음
    (102, 0.40, 0.95, 0.60, 0.70, 0.10, 0.95, 0.30, 0.20, 0.95, 0.20,
     0.200,
     -- walker: 0.40+0.95+0.60+0.70+0.10+0.95+0.30+0.20+0.95+0.20 = 5.35 * 0.1 = 0.535
     0.535,
     -- senior: 0.40*0.2 + 0.20*0.2 + 0.10*0.1 + 0.70*0.15 + 0.95*0.1 + 0.60*0.05 + 0.95*0 + 0.30*0.15 + 0.20*0.05 + 0.95*0
     --       = 0.080 + 0.040 + 0.010 + 0.105 + 0.095 + 0.030 + 0 + 0.045 + 0.010 + 0 = 0.415
     0.415,
     -- stroller: 0.40*0.25 + 0.20*0.25 + 0.10*0.2 + 0.70*0.15 + 0.95*0.05 + 0.60*0 + 0.95*0 + 0.30*0.05 + 0.20*0.05 + 0.95*0
     --         = 0.100 + 0.050 + 0.020 + 0.105 + 0.0475 + 0 + 0 + 0.015 + 0.010 + 0 = 0.3475 → 0.348
     0.348,
     -- dog: 0.40*0.05 + 0.20*0.05 + 0.10*0 + 0.70*0.15 + 0.95*0.15 + 0.60*0.05 + 0.95*0.25 + 0.30*0.05 + 0.20*0.05 + 0.95*0.2
     --    = 0.020 + 0.010 + 0 + 0.105 + 0.1425 + 0.030 + 0.2375 + 0.015 + 0.010 + 0.190 = 0.760
     0.760),
    -- Course 103: 모든 지표 0.5 근처 균등
    (103, 0.50, 0.50, 0.50, 0.50, 0.50, 0.50, 0.50, 0.50, 0.50, 0.50,
     0.200,
     -- walker: 5.0 * 0.1 = 0.5
     0.500,
     -- senior: 0.50 * (0.2+0.2+0.1+0.15+0.1+0.05+0+0.15+0.05+0) = 0.50 * 1.0 = 0.500
     0.500,
     -- stroller: 0.50 * (0.25+0.25+0.2+0.15+0.05+0+0+0.05+0.05+0) = 0.50 * 1.0 = 0.500
     0.500,
     -- dog: 0.50 * (0.05+0.05+0+0.15+0.15+0.05+0.25+0.05+0.05+0.2) = 0.50 * 1.0 = 0.500
     0.500);
