-- Phase: 편도(oneway) 경로 생성 지원
--   1) course.end_point 컬럼 추가 (편도 코스의 도착점 저장)
--   2) routing.generate_oneway:  A→B 최적 경로 (pgr_dijkstra + persona 가중치 재사용)
--   3) routing.generate_oneway_only: Java에서 호출하는 얇은 wrapper (GeoJSON 반환)
--   4) save_from_geom 확장: is_loop / end_point 지원 (기본값으로 하위호환)

BEGIN;

-- ============================================================================
-- 1) course 테이블에 end_point 컬럼 추가
-- ============================================================================
ALTER TABLE public.course
    ADD COLUMN IF NOT EXISTS end_point geometry(Point, 4326);

-- ============================================================================
-- 2) routing.generate_oneway: A→B 최적 경로
--    - generate_loop의 cost SQL을 그대로 재사용 (페르소나 × 시간대 shade × 9개 지표)
--    - pgr_dijkstraVia 대신 pgr_dijkstra 로 A→B 단일 최적 경로 탐색
-- ============================================================================
DROP FUNCTION IF EXISTS routing.generate_oneway(
    double precision, double precision, double precision, double precision,
    timestamp, varchar
) CASCADE;

CREATE OR REPLACE FUNCTION routing.generate_oneway(
    p_start_lng     double precision,
    p_start_lat     double precision,
    p_end_lng       double precision,
    p_end_lat       double precision,
    p_at            timestamp DEFAULT now(),
    p_persona       varchar   DEFAULT NULL
)
RETURNS TABLE (
    path_geom  public.geometry,
    total_m    numeric,
    avg_score  numeric
) AS $$
DECLARE
    v_start_pt         public.geometry;
    v_end_pt           public.geometry;
    v_start_node       bigint;
    v_end_node         bigint;
    v_path_geom        public.geometry;
    v_total_len        numeric;
    v_avg_score        numeric;
    v_month            integer := extract(month FROM p_at)::int;
    v_hour_idx         integer;
    v_max_idx          integer;
    v_is_summer        boolean;
    v_shade_col        text;
    v_cost_sql         text;
    v_persona          varchar := COALESCE(p_persona, 'walker');
    v_w                RECORD;
    v_w_shade          numeric;
BEGIN
    v_is_summer := v_month BETWEEN 4 AND 9;
    v_max_idx   := CASE WHEN v_is_summer THEN 7 ELSE 6 END;
    v_hour_idx  := LEAST(GREATEST((extract(hour FROM p_at)::int - 8)/2 + 1, 1), v_max_idx);
    v_shade_col := CASE WHEN v_is_summer THEN 'shade_summer_hourly' ELSE 'shade_winter_hourly' END;

    -- 페르소나 가중치 로드 (generate_loop와 동일 로직)
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

    v_w_shade := CASE WHEN v_is_summer THEN v_w.w_shade_summer ELSE v_w.w_shade_winter_sun END;

    -- 시작/도착점 좌표 변환 + 최근접 노드 찾기
    v_start_pt := ST_Transform(ST_SetSRID(ST_MakePoint(p_start_lng, p_start_lat), 4326), 5179);
    v_end_pt   := ST_Transform(ST_SetSRID(ST_MakePoint(p_end_lng,   p_end_lat  ), 4326), 5179);

    SELECT id INTO v_start_node
      FROM routing.walk_edges_vertices_pgr
     ORDER BY the_geom <-> v_start_pt LIMIT 1;

    SELECT id INTO v_end_node
      FROM routing.walk_edges_vertices_pgr
     ORDER BY the_geom <-> v_end_pt LIMIT 1;

    IF v_start_node IS NULL OR v_end_node IS NULL THEN
        RETURN;
    END IF;

    -- 시작=도착 방지 (편도의 최소 요건)
    IF v_start_node = v_end_node THEN
        RETURN;
    END IF;

    -- 동적 cost SQL (generate_loop와 동일: 페르소나 가중치 × 시간대 반영)
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

    -- pgr_dijkstra: A→B 단일 최적 경로
    WITH route AS (
        SELECT * FROM pgr_dijkstra(
            v_cost_sql,
            v_start_node,
            v_end_node,
            directed := false
        )
    ),
    joined AS (
        SELECT ST_LineMerge(ST_Union(e.geom_5179 ORDER BY route.seq)) AS g,
               SUM(e.length_m) AS total_len,
               SUM(e.length_m * routing.grid_score_at(e.grid_id, p_at))
                 / NULLIF(SUM(e.length_m), 0) AS wavg_score
        FROM route
        JOIN routing.walk_edges e ON e.id = route.edge
        WHERE route.edge > 0
    )
    SELECT ST_Transform(g, 4326),
           ROUND(total_len::numeric, 1),
           ROUND(wavg_score::numeric, 3)
      INTO v_path_geom, v_total_len, v_avg_score
      FROM joined;

    -- 경로 자체가 안 만들어졌으면 (그래프 단절) NULL 반환
    IF v_path_geom IS NULL THEN
        RETURN;
    END IF;

    RETURN QUERY SELECT v_path_geom, v_total_len, v_avg_score;
END;
$$ LANGUAGE plpgsql
   SET search_path = public, routing, pg_catalog;

-- ============================================================================
-- 3) routing.generate_oneway_only: Java 호출용 (GeoJSON + region_code 반환)
-- ============================================================================
DROP FUNCTION IF EXISTS routing.generate_oneway_only(
    double precision, double precision, double precision, double precision,
    timestamp, varchar
) CASCADE;

CREATE OR REPLACE FUNCTION routing.generate_oneway_only(
    p_start_lng     double precision,
    p_start_lat     double precision,
    p_end_lng       double precision,
    p_end_lat       double precision,
    p_at            timestamp,
    p_persona       varchar DEFAULT NULL
)
RETURNS TABLE (
    path_geojson text,
    total_m      numeric,
    avg_score    numeric,
    region_code  varchar
) AS $$
DECLARE
    v_geom        public.geometry;
    v_total_m     numeric;
    v_avg_score   numeric;
    v_region_code varchar;
    v_start_pt    public.geometry;
BEGIN
    SELECT r.path_geom, r.total_m, r.avg_score
      INTO v_geom, v_total_m, v_avg_score
      FROM routing.generate_oneway(
              p_start_lng, p_start_lat, p_end_lng, p_end_lat, p_at, p_persona) r;

    IF v_geom IS NULL THEN RETURN; END IF;

    -- MultiLineString이면 LineString으로 병합 (generate_only와 동일 패턴)
    IF ST_GeometryType(v_geom) = 'ST_MultiLineString' THEN
        SELECT ST_MakeLine(pt.geom ORDER BY path_idx, pt_idx)
          INTO v_geom
          FROM (SELECT (dp).path[1] AS path_idx, (dp).path[2] AS pt_idx, (dp).geom
                  FROM (SELECT ST_DumpPoints(v_geom) AS dp) t) pt;
    END IF;

    -- 지역 코드는 시작점 기준
    v_start_pt := ST_SetSRID(ST_MakePoint(p_start_lng, p_start_lat), 4326);
    SELECT gs.region_code INTO v_region_code
      FROM public.grid_score gs
     WHERE ST_Contains(gs.geom, v_start_pt) LIMIT 1;

    IF v_region_code IS NULL THEN v_region_code := '11500'; END IF;

    RETURN QUERY SELECT
        ST_AsGeoJSON(v_geom)::text,
        v_total_m,
        v_avg_score,
        v_region_code;
END;
$$ LANGUAGE plpgsql
   SET search_path = public, routing, pg_catalog;

-- ============================================================================
-- 4) save_from_geom 확장: is_loop / end_point 파라미터 추가
--    - 기본값(is_loop=true, end_point=NULL)으로 기존 호출부는 무영향
--    - loop_bonus는 순환일 때만 0.2, 편도는 0
-- ============================================================================
CREATE OR REPLACE FUNCTION routing.save_from_geom(
    p_path_geojson text,
    p_region_code  varchar,
    p_at           timestamp DEFAULT now(),
    p_data_version varchar   DEFAULT '2026-08-gs-yc-v1.2',
    p_is_loop      boolean   DEFAULT true,
    p_end_point    public.geometry DEFAULT NULL
)
RETURNS bigint AS $$
DECLARE
    v_geom          public.geometry;
    v_new_id        bigint;
    v_total_m       numeric;
    v_speed_mps     constant numeric := 1.2;
    v_end_point     public.geometry;
    v_loop_bonus    numeric;
    v_name_prefix   text;
BEGIN
    v_geom := ST_SetSRID(ST_GeomFromGeoJSON(p_path_geojson), 4326);

    IF ST_GeometryType(v_geom) <> 'ST_LineString' THEN
        RAISE EXCEPTION 'path must be LineString, got %', ST_GeometryType(v_geom);
    END IF;

    v_total_m := ST_Length(ST_Transform(v_geom, 5179));

    -- 편도면 도착점 저장 (파라미터 우선, 없으면 path의 마지막 점)
    IF p_is_loop THEN
        v_end_point := NULL;
    ELSE
        v_end_point := COALESCE(p_end_point, ST_EndPoint(v_geom));
    END IF;

    v_loop_bonus  := CASE WHEN p_is_loop THEN 0.200 ELSE 0 END;
    v_name_prefix := CASE WHEN p_is_loop THEN '즉석' ELSE '즉석 편도' END;

    INSERT INTO public.course (
        region_code, name, path, start_point, end_point,
        distance_m, estimated_minutes,
        elevation_gain_m, elevation_loss_m,
        is_loop, source, like_count, data_version
    ) VALUES (
        p_region_code,
        format('%s %s · %sm', v_name_prefix, to_char(p_at, 'MM-DD HH24:MI'), ROUND(v_total_m)),
        v_geom,
        ST_StartPoint(v_geom),
        v_end_point,
        v_total_m::integer,
        CEIL(v_total_m / v_speed_mps / 60)::integer,
        0, 0, p_is_loop, 'auto_discovered', 0, p_data_version
    ) RETURNING course.course_id INTO v_new_id;

    -- course_score 기본 지표 계산 (기존 로직 그대로)
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
        v_loop_bonus
    FROM grid_ints;

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

COMMIT;

-- 검증 예시 (dev DB에서 수동 실행):
--   SELECT total_m, avg_score, LEFT(path_geojson, 80) AS head
--     FROM routing.generate_oneway_only(
--            126.844, 37.550,   -- 출발
--            126.850, 37.555,   -- 도착
--            '2026-08-27 14:00'::timestamp, 'walker');
