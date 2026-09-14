-- Fix #169: 임의 지점 코스 생성 시 폴리라인이 도로/건물을 가로지르는 문제 수정
--
-- 원인:
--   (A) 사용자가 도보 그래프에서 멀리 떨어진 지점(건물 내부, 강 위 등)을 찍어도
--       routing.generate_only/generate_oneway_only 이 거리 제한 없이 그냥 최근접
--       vertex 로 스냅함 → 엉뚱한 위치에서 코스가 시작되어 시각적으로 도로를
--       가로지르는 것처럼 보임.
--   (B) pgr_dijkstraVia 가 disconnected 그래프 조각을 반환하면 ST_LineMerge 결과가
--       MultiLineString 이 되고, 기존 코드는 이를 ST_DumpPoints + ST_MakeLine 으로
--       강제 스티칭하여 조각 사이를 직선으로 연결 → 건물을 관통하는 가짜 세그먼트
--       생성.
--
-- 해결:
--   1) 스냅 허용 거리 상수 함수(routing.max_snap_distance_m) 추가.
--   2) generate_only 재정의: 시작점 스냅 거리 검증 + MultiLineString 결과는
--      스티칭 대신 NULL 반환(다음 후보를 시도하도록).
--   3) generate_oneway_only 재정의: 시작/도착 양쪽 스냅 거리 검증 + 동일한
--      MultiLineString 스킵.
--
-- 스냅 초과 시 SQLSTATE 'P1001' 예외를 발생시키고, Java 레이어에서 이를 감지해
-- 사용자에게 명확한 메시지로 매핑한다.

BEGIN;

-- 도보 그래프 vertex 로 스냅을 허용할 최대 거리 (미터, EPSG:5179)
-- 서울시 walk_edges 밀도상 100m 정도가 실제 도로/공원은 통과, 건물 내부는 걸러냄
CREATE OR REPLACE FUNCTION routing.max_snap_distance_m()
RETURNS numeric
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT 100::numeric;
$$;

-- ============================================================================
-- generate_only: 시작점 스냅 거리 검증 + MultiLineString 스킵
-- ============================================================================
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
    v_geom          public.geometry;
    v_total_m       numeric;
    v_avg_score     numeric;
    v_region_code   varchar;
    v_start_pt_4326 public.geometry;
    v_start_pt_5179 public.geometry;
    v_snap_m        numeric;
BEGIN
    v_start_pt_4326 := ST_SetSRID(ST_MakePoint(p_lng, p_lat), 4326);
    v_start_pt_5179 := ST_Transform(v_start_pt_4326, 5179);

    -- 시작점이 도보 그래프에서 너무 멀면 즉시 실패
    SELECT ST_Distance(the_geom, v_start_pt_5179)
      INTO v_snap_m
      FROM routing.walk_edges_vertices_pgr
     ORDER BY the_geom <-> v_start_pt_5179 LIMIT 1;

    IF v_snap_m IS NULL OR v_snap_m > routing.max_snap_distance_m() THEN
        RAISE EXCEPTION
            'OFF_ROAD_START_POINT: nearest walk edge is % m away', ROUND(COALESCE(v_snap_m, -1), 1)
            USING ERRCODE = 'P1001';
    END IF;

    SELECT r.loop_geom, r.total_m, r.avg_score
      INTO v_geom, v_total_m, v_avg_score
      FROM routing.generate_loop(p_lng, p_lat, p_target_m, p_at, p_candidate_idx, p_persona) r;

    IF v_geom IS NULL THEN RETURN; END IF;

    -- MultiLineString 결과는 그래프 조각이 disconnected 라는 뜻.
    -- ST_MakeLine 스티칭은 조각 사이를 직선으로 이어 건물을 관통하는 가짜 세그먼트를
    -- 생성하므로 이 후보는 폐기하고 다음 candidate 를 시도하게 한다.
    IF ST_GeometryType(v_geom) <> 'ST_LineString' THEN
        RETURN;
    END IF;

    SELECT gs.region_code INTO v_region_code
      FROM public.grid_score gs
     WHERE ST_Contains(gs.geom, v_start_pt_4326) LIMIT 1;

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

-- ============================================================================
-- generate_oneway_only: 시작/도착 스냅 거리 모두 검증 + MultiLineString 스킵
-- ============================================================================
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
    v_geom          public.geometry;
    v_total_m       numeric;
    v_avg_score     numeric;
    v_region_code   varchar;
    v_start_pt_4326 public.geometry;
    v_start_pt_5179 public.geometry;
    v_end_pt_5179   public.geometry;
    v_start_snap_m  numeric;
    v_end_snap_m    numeric;
BEGIN
    v_start_pt_4326 := ST_SetSRID(ST_MakePoint(p_start_lng, p_start_lat), 4326);
    v_start_pt_5179 := ST_Transform(v_start_pt_4326, 5179);
    v_end_pt_5179   := ST_Transform(ST_SetSRID(ST_MakePoint(p_end_lng, p_end_lat), 4326), 5179);

    SELECT ST_Distance(the_geom, v_start_pt_5179)
      INTO v_start_snap_m
      FROM routing.walk_edges_vertices_pgr
     ORDER BY the_geom <-> v_start_pt_5179 LIMIT 1;

    IF v_start_snap_m IS NULL OR v_start_snap_m > routing.max_snap_distance_m() THEN
        RAISE EXCEPTION
            'OFF_ROAD_START_POINT: nearest walk edge is % m away', ROUND(COALESCE(v_start_snap_m, -1), 1)
            USING ERRCODE = 'P1001';
    END IF;

    SELECT ST_Distance(the_geom, v_end_pt_5179)
      INTO v_end_snap_m
      FROM routing.walk_edges_vertices_pgr
     ORDER BY the_geom <-> v_end_pt_5179 LIMIT 1;

    IF v_end_snap_m IS NULL OR v_end_snap_m > routing.max_snap_distance_m() THEN
        RAISE EXCEPTION
            'OFF_ROAD_END_POINT: nearest walk edge is % m away', ROUND(COALESCE(v_end_snap_m, -1), 1)
            USING ERRCODE = 'P1002';
    END IF;

    SELECT r.path_geom, r.total_m, r.avg_score
      INTO v_geom, v_total_m, v_avg_score
      FROM routing.generate_oneway(
              p_start_lng, p_start_lat, p_end_lng, p_end_lat, p_at, p_persona) r;

    IF v_geom IS NULL THEN RETURN; END IF;

    IF ST_GeometryType(v_geom) <> 'ST_LineString' THEN
        RETURN;
    END IF;

    SELECT gs.region_code INTO v_region_code
      FROM public.grid_score gs
     WHERE ST_Contains(gs.geom, v_start_pt_4326) LIMIT 1;

    IF v_region_code IS NULL THEN v_region_code := '11500'; END IF;

    RETURN QUERY SELECT
        ST_AsGeoJSON(v_geom)::text,
        v_total_m,
        v_avg_score,
        v_region_code;
END;
$$ LANGUAGE plpgsql
   SET search_path = public, routing, pg_catalog;

COMMIT;
