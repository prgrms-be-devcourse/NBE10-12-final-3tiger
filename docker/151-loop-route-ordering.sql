-- Fix: 순환 코스 조립 시 ST_Union이 이동 순서와 중복 간선을 제거해
-- 대부분의 결과가 MultiLineString으로 폐기되는 문제를 수정한다.
-- pgRouting 결과의 seq/node를 사용해 각 간선의 방향을 맞춘 뒤
-- 좌표를 이동 순서대로 ST_MakeLine 한다.

BEGIN;

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
    v_radius_mult      double precision;
    v_slot             integer;
    v_base_bearing     double precision;
    v_wp_nodes         bigint[];
    v_i                integer;
    v_wp_pt            public.geometry;
    v_wp_node          bigint;
    v_path_geom        public.geometry;
    v_total_len        numeric;
    v_avg_score        numeric;
    v_leg_count        integer;
    v_max_gap_m        numeric;
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
    v_hour_idx  := LEAST(GREATEST((extract(hour FROM p_at)::int - 8) / 2 + 1, 1), v_max_idx);
    v_shade_col := CASE WHEN v_is_summer THEN 'shade_summer_hourly' ELSE 'shade_winter_hourly' END;

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
      FROM public.persona_weight
     WHERE persona = v_persona;

    IF v_w.w_flatness IS NULL THEN
        RAISE EXCEPTION 'persona_weight not seeded for persona=%', v_persona;
    END IF;

    v_w_shade := CASE
        WHEN v_is_summer THEN v_w.w_shade_summer
        ELSE v_w.w_shade_winter_sun
    END;

    v_start_pt := ST_Transform(
        ST_SetSRID(ST_MakePoint(p_start_lng, p_start_lat), 4326),
        5179
    );
    SELECT id
      INTO v_start_node
      FROM routing.walk_edges_vertices_pgr
     ORDER BY the_geom <-> v_start_pt
     LIMIT 1;

    v_slot         := p_candidate_idx % 8;
    v_base_bearing := v_slot * 45.0;
    v_radius_mult  := CASE v_slot
        WHEN 2 THEN 0.85
        WHEN 5 THEN 0.85
        WHEN 3 THEN 1.15
        WHEN 6 THEN 1.15
        ELSE 1.00
    END;
    v_radius_m := (p_target_m / (2 * pi())) * v_radius_mult;

    v_wp_nodes := ARRAY[]::bigint[];
    FOR v_i IN 0..2 LOOP
        v_wp_pt := ST_SetSRID(ST_MakePoint(
            ST_X(v_start_pt) + v_radius_m * sin(radians(v_base_bearing + v_i * 120)),
            ST_Y(v_start_pt) + v_radius_m * cos(radians(v_base_bearing + v_i * 120))
        ), 5179);

        SELECT v.id
          INTO v_wp_node
          FROM routing.walk_edges_vertices_pgr v
          JOIN routing.walk_edges e ON e.source = v.id OR e.target = v.id
         WHERE ST_DWithin(v.the_geom, v_wp_pt, 300)
         GROUP BY v.id
         ORDER BY MAX(e.grid_score) DESC NULLS LAST, v.the_geom <-> v_wp_pt
         LIMIT 1;

        IF v_wp_node IS NULL THEN
            SELECT id
              INTO v_wp_node
              FROM routing.walk_edges_vertices_pgr
             ORDER BY the_geom <-> v_wp_pt
             LIMIT 1;
        END IF;

        v_wp_nodes := array_append(v_wp_nodes, v_wp_node);
    END LOOP;

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
                 + COALESCE(g.pavement_quality,0.5)  * %11$L
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
                 + COALESCE(g.pavement_quality,0.5)  * %11$L
               )) AS reverse_cost
          FROM routing.walk_edges e
          LEFT JOIN public.grid_score g ON g.grid_id = e.grid_id
         WHERE e.length_m IS NOT NULL
    $SQL$, v_shade_col, v_hour_idx,
           v_w.w_flatness, v_w_shade, v_w.w_traffic_low, v_w.w_wheelchair,
           v_w.w_surface_natural, v_w.w_bench_density, v_w.w_restroom_proximity,
           v_w.w_water_facility, v_w.w_pavement_quality);

    WITH via AS (
        SELECT *
          FROM pgr_dijkstraVia(
              v_cost_sql,
              ARRAY[v_start_node, v_wp_nodes[1], v_wp_nodes[2], v_wp_nodes[3], v_start_node],
              directed := false
          )
    ),
    ordered_edges AS (
        SELECT
            via.seq,
            via.path_id,
            e.length_m,
            e.grid_id,
            CASE
                WHEN via.node = e.source THEN e.geom_5179
                ELSE ST_Reverse(e.geom_5179)
            END AS geom
          FROM via
          JOIN routing.walk_edges e ON e.id = via.edge
         WHERE via.edge > 0
    ),
    edges_with_previous AS (
        SELECT
            ordered_edges.*,
            LAG(geom) OVER (ORDER BY seq) AS previous_geom
          FROM ordered_edges
    ),
    ordered_points AS (
        SELECT
            oe.seq,
            (dumped).path[1] AS point_seq,
            (dumped).geom AS geom
          FROM ordered_edges oe
          CROSS JOIN LATERAL ST_DumpPoints(oe.geom) AS dumped
    ),
    assembled AS (
        SELECT ST_RemoveRepeatedPoints(
                   ST_MakeLine(geom ORDER BY seq, point_seq),
                   0.001
               ) AS geom
          FROM ordered_points
    ),
    route_stats AS (
        SELECT
            SUM(length_m) AS total_len,
            SUM(length_m * routing.grid_score_at(grid_id, p_at))
                / NULLIF(SUM(length_m), 0) AS weighted_score,
            COUNT(DISTINCT path_id)::integer AS leg_count
          FROM ordered_edges
    ),
    continuity AS (
        SELECT COALESCE(
                   MAX(ST_Distance(ST_EndPoint(previous_geom), ST_StartPoint(geom))),
                   0
               ) AS max_gap_m
          FROM edges_with_previous
         WHERE previous_geom IS NOT NULL
    )
    SELECT
        assembled.geom,
        ROUND(route_stats.total_len::numeric, 1),
        ROUND(route_stats.weighted_score::numeric, 3),
        route_stats.leg_count,
        continuity.max_gap_m
      INTO v_path_geom, v_total_len, v_avg_score, v_leg_count, v_max_gap_m
      FROM assembled
      CROSS JOIN route_stats
      CROSS JOIN continuity;

    -- 시작→경유지 3개→시작의 네 구간이 모두 연결되어야 한다.
    IF v_leg_count <> 4 OR v_path_geom IS NULL THEN
        RETURN;
    END IF;

    -- 실제 인접 간선 사이에 틈이 있으면 직선으로 메우지 않고 후보를 폐기한다.
    IF v_max_gap_m > 1.0 THEN
        RETURN;
    END IF;

    IF ST_GeometryType(v_path_geom) <> 'ST_LineString'
       OR NOT ST_IsValid(v_path_geom) THEN
        RETURN;
    END IF;

    -- 순환 코스의 시작과 끝이 같은 노드에 닫혀 있는지 최종 확인한다.
    IF ST_Distance(ST_StartPoint(v_path_geom), ST_EndPoint(v_path_geom)) > 2.0 THEN
        RETURN;
    END IF;

    -- API GeoJSON은 WGS84(4326)로 반환해야 crs 확장 필드 없이
    -- type/coordinates 구조만 생성되고 프론트 지도 좌표로 바로 사용할 수 있다.
    RETURN QUERY SELECT
        ST_Transform(v_path_geom, 4326),
        v_total_len,
        v_avg_score,
        v_wp_nodes;
END;
$$ LANGUAGE plpgsql
   SET search_path = public, routing, pg_catalog;

COMMIT;
