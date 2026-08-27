-- Phase: 라우팅 함수에 persona 파라미터 추가
--   generate_loop:   페르소나별 가중치로 pgr_dijkstraVia 동적 cost SQL 구성 (시간대는 유지)
--   generate_only:   p_persona → generate_loop 전달
--   save_from_geom / save_generated_course: 저장 시 4개 페르소나 총점 자동 계산 (persona 파라미터 불필요)
--
-- p_persona = NULL → 균등 가중치 (walker) 폴백 == 기존 동작 보존

BEGIN;

-- 1) generate_loop 재정의: p_persona 추가
DROP FUNCTION IF EXISTS routing.generate_loop(double precision, double precision, integer, timestamp, integer) CASCADE;
DROP FUNCTION IF EXISTS routing.generate_loop(double precision, double precision, integer, timestamp, integer, varchar) CASCADE;

CREATE OR REPLACE FUNCTION routing.generate_loop(
    p_start_lng     double precision,
    p_start_lat     double precision,
    p_target_m      integer,
    p_at            timestamp DEFAULT now(),
    p_candidate_idx integer DEFAULT 0,
    p_persona       varchar DEFAULT NULL
)
RETURNS TABLE (
    loop_geom  public.geometry,
    total_m    numeric,
    avg_score  numeric,
    waypoints  bigint[]
) AS $$
DECLARE
    v_start_pt         public.geometry;
    v_start_node       bigint;
    v_radius_m         double precision;
    v_base_bearing     double precision;
    v_wp_nodes         bigint[];
    v_i                integer;
    v_wp_pt            public.geometry;
    v_wp_node          bigint;
    v_path_geom        public.geometry;
    v_total_len        numeric;
    v_avg_score        numeric;
    v_month            integer := extract(month FROM p_at)::int;
    v_hour_idx         integer;
    v_max_idx          integer;
    v_is_summer        boolean;
    v_shade_col        text;
    v_cost_sql         text;
    v_persona          varchar := COALESCE(p_persona, 'walker');  -- NULL → walker(균등)
    v_w                RECORD;
    v_w_shade          numeric;
BEGIN
    v_is_summer := v_month BETWEEN 4 AND 9;
    v_max_idx   := CASE WHEN v_is_summer THEN 7 ELSE 6 END;
    v_hour_idx  := LEAST(GREATEST((extract(hour FROM p_at)::int - 8)/2 + 1, 1), v_max_idx);
    v_shade_col := CASE WHEN v_is_summer THEN 'shade_summer_hourly' ELSE 'shade_winter_hourly' END;

    -- 페르소나 가중치 로드
    SELECT
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
      INTO v_w
      FROM public.persona_weight WHERE persona = v_persona;

    IF v_w.w_flatness IS NULL THEN
        RAISE EXCEPTION 'persona_weight not seeded for persona=%', v_persona;
    END IF;

    -- 시간대 반영 shade 슬롯의 weight = summer면 w_shade_summer, winter면 w_shade_winter_sun
    v_w_shade := CASE WHEN v_is_summer THEN v_w.w_shade_summer ELSE v_w.w_shade_winter_sun END;

    -- 시작점 5179 + 최근접 노드
    v_start_pt := ST_Transform(ST_SetSRID(ST_MakePoint(p_start_lng, p_start_lat), 4326), 5179);
    SELECT id INTO v_start_node
      FROM routing.walk_edges_vertices_pgr
     ORDER BY the_geom <-> v_start_pt LIMIT 1;

    -- 웨이포인트
    v_radius_m := p_target_m / (2 * pi());
    v_base_bearing := p_candidate_idx * 40.0;

    v_wp_nodes := ARRAY[]::bigint[];
    FOR v_i IN 0..2 LOOP
        v_wp_pt := ST_SetSRID(ST_MakePoint(
            ST_X(v_start_pt) + v_radius_m * sin(radians(v_base_bearing + v_i * 120)),
            ST_Y(v_start_pt) + v_radius_m * cos(radians(v_base_bearing + v_i * 120))
        ), 5179);

        SELECT v.id INTO v_wp_node
          FROM routing.walk_edges_vertices_pgr v
          JOIN routing.walk_edges e ON e.source = v.id OR e.target = v.id
         WHERE ST_DWithin(v.the_geom, v_wp_pt, 300)
         GROUP BY v.id
         ORDER BY MAX(e.grid_score) DESC NULLS LAST, v.the_geom <-> v_wp_pt
         LIMIT 1;

        IF v_wp_node IS NULL THEN
            SELECT id INTO v_wp_node FROM routing.walk_edges_vertices_pgr
             ORDER BY the_geom <-> v_wp_pt LIMIT 1;
        END IF;

        v_wp_nodes := array_append(v_wp_nodes, v_wp_node);
    END LOOP;

    -- 동적 cost SQL: 페르소나 가중치 × 시간대 반영 shade
    v_cost_sql := format($SQL$
        SELECT e.id, e.source, e.target,
               e.length_m / (0.1 + (
                   COALESCE(g.flatness,0)            * %3$L
                 + COALESCE(g.%1$I[%2$s],0)          * %4$L
                 + COALESCE(g.traffic_low,0)         * %5$L
                 + COALESCE(g.wheelchair,0)          * %6$L
                 + COALESCE(g.surface_natural,0)     * %7$L
                 + COALESCE(g.bench_density,0)       * %8$L
                 + COALESCE(g.restroom_proximity,0)  * %9$L
                 + COALESCE(g.water_facility,0)      * %10$L
                 + COALESCE(g.pavement_quality,0)    * %11$L
               )) AS cost,
               e.length_m / (0.1 + (
                   COALESCE(g.flatness,0)            * %3$L
                 + COALESCE(g.%1$I[%2$s],0)          * %4$L
                 + COALESCE(g.traffic_low,0)         * %5$L
                 + COALESCE(g.wheelchair,0)          * %6$L
                 + COALESCE(g.surface_natural,0)     * %7$L
                 + COALESCE(g.bench_density,0)       * %8$L
                 + COALESCE(g.restroom_proximity,0)  * %9$L
                 + COALESCE(g.water_facility,0)      * %10$L
                 + COALESCE(g.pavement_quality,0)    * %11$L
               )) AS reverse_cost
          FROM routing.walk_edges e
          LEFT JOIN public.grid_score g ON g.grid_id = e.grid_id
         WHERE e.length_m IS NOT NULL
    $SQL$, v_shade_col, v_hour_idx,
           v_w.w_flatness, v_w_shade, v_w.w_traffic_low, v_w.w_wheelchair,
           v_w.w_surface_natural, v_w.w_bench_density, v_w.w_restroom_proximity,
           v_w.w_water_facility, v_w.w_pavement_quality);

    -- pgr_dijkstraVia with dynamic cost
    WITH via AS (
        SELECT * FROM pgr_dijkstraVia(
            v_cost_sql,
            ARRAY[v_start_node, v_wp_nodes[1], v_wp_nodes[2], v_wp_nodes[3], v_start_node],
            directed := false
        )
    ),
    joined AS (
        SELECT ST_LineMerge(ST_Union(e.geom_5179 ORDER BY via.seq)) AS g,
               SUM(e.length_m) AS total_len,
               SUM(e.length_m * routing.grid_score_at(e.grid_id, p_at))
                 / NULLIF(SUM(e.length_m), 0) AS wavg_score
        FROM via
        JOIN routing.walk_edges e ON e.id = via.edge
        WHERE via.edge > 0
    )
    SELECT ST_Transform(g, 4326),
           ROUND(total_len::numeric, 1),
           ROUND(wavg_score::numeric, 3)
      INTO v_path_geom, v_total_len, v_avg_score
      FROM joined;

    RETURN QUERY SELECT v_path_geom, v_total_len, v_avg_score, v_wp_nodes;
END;
$$ LANGUAGE plpgsql
   SET search_path = public, routing, pg_catalog;

-- 2) generate_only 재정의: p_persona → generate_loop 전달
DROP FUNCTION IF EXISTS routing.generate_only(double precision, double precision, integer, timestamp, integer) CASCADE;
DROP FUNCTION IF EXISTS routing.generate_only(double precision, double precision, integer, timestamp, integer, varchar) CASCADE;

CREATE OR REPLACE FUNCTION routing.generate_only(
    p_lng           double precision,
    p_lat           double precision,
    p_target_m      integer,
    p_at            timestamp,
    p_candidate_idx integer,
    p_persona       varchar DEFAULT NULL
)
RETURNS TABLE (
    path_geojson text,
    total_m      numeric,
    avg_score    numeric,
    error_pct    numeric,
    region_code  varchar
) AS $$
DECLARE
    v_geom        public.geometry;
    v_total_m     numeric;
    v_avg_score   numeric;
    v_region_code varchar;
    v_start_pt    public.geometry;
BEGIN
    SELECT r.loop_geom, r.total_m, r.avg_score
      INTO v_geom, v_total_m, v_avg_score
      FROM routing.generate_loop(p_lng, p_lat, p_target_m, p_at, p_candidate_idx, p_persona) r;

    IF v_geom IS NULL THEN RETURN; END IF;

    IF ST_GeometryType(v_geom) = 'ST_MultiLineString' THEN
        SELECT ST_MakeLine(pt.geom ORDER BY path_idx, pt_idx)
          INTO v_geom
          FROM (SELECT (dp).path[1] AS path_idx, (dp).path[2] AS pt_idx, (dp).geom
                  FROM (SELECT ST_DumpPoints(v_geom) AS dp) t) pt;
    END IF;

    v_start_pt := ST_SetSRID(ST_MakePoint(p_lng, p_lat), 4326);
    SELECT gs.region_code INTO v_region_code
      FROM public.grid_score gs
     WHERE ST_Contains(gs.geom, v_start_pt) LIMIT 1;

    IF v_region_code IS NULL THEN v_region_code := '11500'; END IF;

    RETURN QUERY SELECT
        ST_AsGeoJSON(v_geom)::text,
        v_total_m,
        v_avg_score,
        ROUND(ABS(v_total_m - p_target_m) / p_target_m * 100, 1),
        v_region_code;
END;
$$ LANGUAGE plpgsql
   SET search_path = public, routing, pg_catalog;

-- 3) save_from_geom 재정의: 4개 페르소나 총점 자동 계산
CREATE OR REPLACE FUNCTION routing.save_from_geom(
    p_path_geojson text,
    p_region_code  varchar,
    p_at           timestamp DEFAULT now(),
    p_data_version varchar DEFAULT '2026-08-gs-yc-v1.2'
)
RETURNS bigint AS $$
DECLARE
    v_geom      public.geometry;
    v_new_id    bigint;
    v_total_m   numeric;
    v_speed_mps constant numeric := 1.2;
BEGIN
    v_geom := ST_SetSRID(ST_GeomFromGeoJSON(p_path_geojson), 4326);

    IF ST_GeometryType(v_geom) <> 'ST_LineString' THEN
        RAISE EXCEPTION 'path must be LineString, got %', ST_GeometryType(v_geom);
    END IF;

    v_total_m := ST_Length(ST_Transform(v_geom, 5179));

    INSERT INTO public.course (
        region_code, name, path, start_point,
        distance_m, estimated_minutes,
        elevation_gain_m, elevation_loss_m,
        is_loop, source, like_count, data_version
    ) VALUES (
        p_region_code,
        format('즉석 %s · %sm', to_char(p_at, 'MM-DD HH24:MI'), ROUND(v_total_m)),
        v_geom,
        ST_StartPoint(v_geom),
        v_total_m::integer,
        CEIL(v_total_m / v_speed_mps / 60)::integer,
        0, 0, true, 'auto_discovered', 0, p_data_version
    ) RETURNING course.course_id INTO v_new_id;

    -- course_score 기본 지표 계산
    INSERT INTO public.course_score (
        course_id, flatness, shade_summer, shade_winter_sun,
        traffic_low, wheelchair, surface_natural,
        bench_density, restroom_proximity, water_facility,
        pavement_quality, loop_bonus
    )
    WITH path5179 AS (SELECT ST_Transform(v_geom, 5179) AS g),
    grid_ints AS (
        SELECT g.*,
               ST_Length(ST_Intersection(p.g, ST_Buffer(ST_Transform(g.geom, 5179), 5))) AS w
        FROM public.grid_score g, path5179 p
        WHERE ST_DWithin(p.g, ST_Transform(g.geom, 5179), 5)
    )
    SELECT
        v_new_id,
        ROUND((SUM(flatness           * w) / NULLIF(SUM(w),0))::numeric, 3),
        ROUND((SUM(shade_summer       * w) / NULLIF(SUM(w),0))::numeric, 3),
        ROUND((SUM(shade_winter_sun   * w) / NULLIF(SUM(w),0))::numeric, 3),
        ROUND((SUM(traffic_low        * w) / NULLIF(SUM(w),0))::numeric, 3),
        ROUND((SUM(wheelchair         * w) / NULLIF(SUM(w),0))::numeric, 3),
        ROUND((SUM(surface_natural    * w) / NULLIF(SUM(w),0))::numeric, 3),
        ROUND((SUM(bench_density      * w) / NULLIF(SUM(w),0))::numeric, 3),
        ROUND((SUM(restroom_proximity * w) / NULLIF(SUM(w),0))::numeric, 3),
        ROUND((SUM(water_facility     * w) / NULLIF(SUM(w),0))::numeric, 3),
        ROUND((SUM(pavement_quality * w) FILTER (WHERE pavement_quality IS NOT NULL)
              / NULLIF(SUM(w) FILTER (WHERE pavement_quality IS NOT NULL), 0))::numeric, 3),
        0.200
    FROM grid_ints;

    -- 4개 페르소나 총점 계산 (compute_persona_score 헬퍼 함수 사용)
    UPDATE public.course_score cs
       SET score_walker   = public.compute_persona_score('walker',   cs),
           score_senior   = public.compute_persona_score('senior',   cs),
           score_stroller = public.compute_persona_score('stroller', cs),
           score_dog      = public.compute_persona_score('dog',      cs)
     WHERE course_id = v_new_id;

    RETURN v_new_id;
END;
$$ LANGUAGE plpgsql
   SET search_path = public, routing, pg_catalog;

-- 4) save_generated_course 재정의: p_persona 추가 (generate_loop에 전달) + 4개 페르소나 총점 계산
DROP FUNCTION IF EXISTS routing.save_generated_course(double precision, double precision, integer, timestamp, integer, varchar);

CREATE OR REPLACE FUNCTION routing.save_generated_course(
    p_lng           double precision,
    p_lat           double precision,
    p_target_m      integer,
    p_at            timestamp,
    p_candidate_idx integer,
    p_persona       varchar DEFAULT NULL,
    p_data_version  varchar DEFAULT '2026-08-gs-yc-v1.2'
)
RETURNS TABLE (
    course_id   bigint,
    total_m     numeric,
    avg_score   numeric,
    error_pct   numeric,
    region_code varchar
) AS $$
DECLARE
    v_geom        public.geometry;
    v_total_m     numeric;
    v_avg_score   numeric;
    v_region_code varchar;
    v_new_id      bigint;
    v_error_pct   numeric;
    v_start_pt    public.geometry;
    v_speed_mps   constant numeric := 1.2;
BEGIN
    SELECT r.loop_geom, r.total_m, r.avg_score
      INTO v_geom, v_total_m, v_avg_score
      FROM routing.generate_loop(p_lng, p_lat, p_target_m, p_at, p_candidate_idx, p_persona) r;

    IF v_geom IS NULL THEN RETURN; END IF;

    IF ST_GeometryType(v_geom) = 'ST_MultiLineString' THEN
        SELECT ST_MakeLine(pt.geom ORDER BY path_idx, pt_idx)
          INTO v_geom
          FROM (
            SELECT (dp).path[1] AS path_idx,
                   (dp).path[2] AS pt_idx,
                   (dp).geom
              FROM (SELECT ST_DumpPoints(v_geom) AS dp) t
          ) pt;
    END IF;

    v_error_pct := ROUND(ABS(v_total_m - p_target_m) / p_target_m * 100, 1);

    v_start_pt := ST_SetSRID(ST_MakePoint(p_lng, p_lat), 4326);
    SELECT gs.region_code INTO v_region_code
      FROM public.grid_score gs
     WHERE ST_Contains(gs.geom, v_start_pt)
     LIMIT 1;

    IF v_region_code IS NULL THEN
        v_region_code := '11500';
    END IF;

    INSERT INTO public.course (
        region_code, name, path, start_point,
        distance_m, estimated_minutes,
        elevation_gain_m, elevation_loss_m,
        is_loop, source, like_count, data_version
    ) VALUES (
        v_region_code,
        format('즉석 %s · %sm', to_char(p_at, 'MM-DD HH24:MI'), ROUND(v_total_m)),
        v_geom,
        ST_StartPoint(v_geom),
        v_total_m::integer,
        CEIL(v_total_m / v_speed_mps / 60)::integer,
        0, 0,
        true,
        'auto_discovered',
        0,
        p_data_version
    ) RETURNING course.course_id INTO v_new_id;

    INSERT INTO public.course_score (
        course_id, flatness, shade_summer, shade_winter_sun,
        traffic_low, wheelchair, surface_natural,
        bench_density, restroom_proximity, water_facility,
        pavement_quality, loop_bonus,
        shade_summer_hourly, shade_winter_hourly, slope_deg_max
    )
    WITH path5179 AS (SELECT ST_Transform(v_geom, 5179) AS g),
    grid_ints AS (
        SELECT g.*,
               ST_Length(ST_Intersection(p.g, ST_Buffer(ST_Transform(g.geom, 5179), 5))) AS w
        FROM public.grid_score g, path5179 p
        WHERE ST_DWithin(p.g, ST_Transform(g.geom, 5179), 5)
    )
    SELECT
        v_new_id,
        ROUND((SUM(flatness           * w) / NULLIF(SUM(w),0))::numeric, 3),
        ROUND((SUM(shade_summer       * w) / NULLIF(SUM(w),0))::numeric, 3),
        ROUND((SUM(shade_winter_sun   * w) / NULLIF(SUM(w),0))::numeric, 3),
        ROUND((SUM(traffic_low        * w) / NULLIF(SUM(w),0))::numeric, 3),
        ROUND((SUM(wheelchair         * w) / NULLIF(SUM(w),0))::numeric, 3),
        ROUND((SUM(surface_natural    * w) / NULLIF(SUM(w),0))::numeric, 3),
        ROUND((SUM(bench_density      * w) / NULLIF(SUM(w),0))::numeric, 3),
        ROUND((SUM(restroom_proximity * w) / NULLIF(SUM(w),0))::numeric, 3),
        ROUND((SUM(water_facility     * w) / NULLIF(SUM(w),0))::numeric, 3),
        ROUND((SUM(pavement_quality * w) FILTER (WHERE pavement_quality IS NOT NULL)
              / NULLIF(SUM(w) FILTER (WHERE pavement_quality IS NOT NULL), 0))::numeric, 3),
        0.200,
        NULL, NULL,
        NULL
    FROM grid_ints;

    -- 4개 페르소나 총점 계산
    UPDATE public.course_score cs
       SET score_walker   = public.compute_persona_score('walker',   cs),
           score_senior   = public.compute_persona_score('senior',   cs),
           score_stroller = public.compute_persona_score('stroller', cs),
           score_dog      = public.compute_persona_score('dog',      cs)
     WHERE course_id = v_new_id;

    RETURN QUERY SELECT v_new_id, v_total_m, v_avg_score, v_error_pct, v_region_code;
END;
$$ LANGUAGE plpgsql
   SET search_path = public, routing, pg_catalog;

COMMIT;

-- 검증: 같은 좌표+거리에서 페르소나만 바꿔 generate_only 호출 → 서로 다른 경로 나오는지 확인
--   (강서구 좌표 예시. 실제 dev DB에서 수동 실행)
--
--   SELECT 'walker' AS persona, total_m, avg_score, LEFT(path_geojson, 80) AS path_head
--     FROM routing.generate_only(126.844, 37.550, 3000, '2026-08-27 14:00'::timestamp, 0, 'walker')
--   UNION ALL
--   SELECT 'senior',   total_m, avg_score, LEFT(path_geojson, 80)
--     FROM routing.generate_only(126.844, 37.550, 3000, '2026-08-27 14:00'::timestamp, 0, 'senior')
--   UNION ALL
--   SELECT 'stroller', total_m, avg_score, LEFT(path_geojson, 80)
--     FROM routing.generate_only(126.844, 37.550, 3000, '2026-08-27 14:00'::timestamp, 0, 'stroller')
--   UNION ALL
--   SELECT 'dog',      total_m, avg_score, LEFT(path_geojson, 80)
--     FROM routing.generate_only(126.844, 37.550, 3000, '2026-08-27 14:00'::timestamp, 0, 'dog');
