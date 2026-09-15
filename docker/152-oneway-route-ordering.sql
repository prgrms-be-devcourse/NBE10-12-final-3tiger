-- Fix: 편도 경로 조립 시 ST_Union이 이동 방향을 보존하지 않아
-- 특정 페르소나의 경로가 도착점 -> 출발점 순서로 반환되는 문제를 수정한다.
-- pgRouting의 seq/node를 기준으로 간선 방향을 맞추고 이동 순서대로 연결한다.

BEGIN;

CREATE OR REPLACE FUNCTION routing.generate_oneway(
    p_start_lng     double precision,
    p_start_lat     double precision,
    p_end_lng       double precision,
    p_end_lat       double precision,
    p_at            timestamp DEFAULT now(),
    p_persona       varchar DEFAULT NULL
)
RETURNS TABLE (
    path_geom public.geometry,
    total_m   numeric,
    avg_score numeric
) AS $$
DECLARE
    v_start_pt    public.geometry;
    v_end_pt      public.geometry;
    v_start_node  bigint;
    v_end_node    bigint;
    v_path_geom   public.geometry;
    v_total_len   numeric;
    v_avg_score   numeric;
    v_max_gap_m   numeric;
    v_month       integer := extract(month FROM p_at)::int;
    v_hour_idx    integer;
    v_max_idx     integer;
    v_is_summer   boolean;
    v_shade_col   text;
    v_cost_sql    text;
    v_persona     varchar := COALESCE(p_persona, 'walker');
    v_w           RECORD;
    v_w_shade     numeric;
BEGIN
    v_is_summer := v_month BETWEEN 4 AND 9;
    v_max_idx := CASE WHEN v_is_summer THEN 7 ELSE 6 END;
    v_hour_idx := LEAST(GREATEST((extract(hour FROM p_at)::int - 8) / 2 + 1, 1), v_max_idx);
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
        ST_SetSRID(ST_MakePoint(p_start_lng, p_start_lat), 4326), 5179
    );
    v_end_pt := ST_Transform(
        ST_SetSRID(ST_MakePoint(p_end_lng, p_end_lat), 4326), 5179
    );

    SELECT id INTO v_start_node
      FROM routing.walk_edges_vertices_pgr
     ORDER BY the_geom <-> v_start_pt
     LIMIT 1;

    SELECT id INTO v_end_node
      FROM routing.walk_edges_vertices_pgr
     ORDER BY the_geom <-> v_end_pt
     LIMIT 1;

    IF v_start_node IS NULL OR v_end_node IS NULL OR v_start_node = v_end_node THEN
        RETURN;
    END IF;

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

    WITH route AS (
        SELECT *
          FROM pgr_dijkstra(v_cost_sql, v_start_node, v_end_node, directed := false)
    ),
    ordered_edges AS (
        SELECT
            route.seq,
            e.length_m,
            e.grid_id,
            CASE
                WHEN route.node = e.source THEN e.geom_5179
                ELSE ST_Reverse(e.geom_5179)
            END AS geom
          FROM route
          JOIN routing.walk_edges e ON e.id = route.edge
         WHERE route.edge > 0
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
            (dumped).geom
          FROM ordered_edges oe
          CROSS JOIN LATERAL ST_DumpPoints(oe.geom) AS dumped
    ),
    assembled AS (
        SELECT ST_RemoveRepeatedPoints(
                   ST_MakeLine(geom ORDER BY seq, point_seq), 0.001
               ) AS geom
          FROM ordered_points
    ),
    route_stats AS (
        SELECT
            SUM(length_m) AS total_len,
            SUM(length_m * routing.grid_score_at(grid_id, p_at))
                / NULLIF(SUM(length_m), 0) AS weighted_score
          FROM ordered_edges
    ),
    continuity AS (
        SELECT COALESCE(
                   MAX(ST_Distance(ST_EndPoint(previous_geom), ST_StartPoint(geom))), 0
               ) AS max_gap_m
          FROM edges_with_previous
         WHERE previous_geom IS NOT NULL
    )
    SELECT
        assembled.geom,
        ROUND(route_stats.total_len::numeric, 1),
        ROUND(route_stats.weighted_score::numeric, 3),
        continuity.max_gap_m
      INTO v_path_geom, v_total_len, v_avg_score, v_max_gap_m
      FROM assembled
      CROSS JOIN route_stats
      CROSS JOIN continuity;

    IF v_path_geom IS NULL
       OR ST_GeometryType(v_path_geom) <> 'ST_LineString'
       OR NOT ST_IsValid(v_path_geom)
       OR v_max_gap_m > 1.0 THEN
        RETURN;
    END IF;

    -- 결과 방향을 요청한 출발점 -> 도착점으로 한 번 더 보장한다.
    IF ST_Distance(ST_StartPoint(v_path_geom), v_start_pt)
       > ST_Distance(ST_EndPoint(v_path_geom), v_start_pt) THEN
        v_path_geom := ST_Reverse(v_path_geom);
    END IF;

    RETURN QUERY SELECT
        ST_Transform(v_path_geom, 4326),
        v_total_len,
        v_avg_score;
END;
$$ LANGUAGE plpgsql
   SET search_path = public, routing, pg_catalog;

COMMIT;
