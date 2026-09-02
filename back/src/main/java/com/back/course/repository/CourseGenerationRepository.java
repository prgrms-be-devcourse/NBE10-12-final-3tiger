package com.back.course.repository;

import com.back.course.dto.GeoJsonLineString;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class CourseGenerationRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public CourseGenerationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 저장 없이 경로만 생성. persona=null이면 균등 가중치(walker)로 폴백. */
    public Optional<GenerateRow> generateOnly(
            double lng, double lat, int targetM, LocalDateTime at, int candidateIdx, String persona
    ) {
        String sql = """
                SELECT path_geojson, total_m, avg_score, error_pct, region_code
                  FROM routing.generate_only(?, ?, ?, ?, ?, ?)
                """;
        List<Map<String, Object>> rows = jdbc.queryForList(sql, lng, lat, targetM, at, candidateIdx, persona);
        if (rows.isEmpty()) return Optional.empty();
        Map<String, Object> r = rows.get(0);
        if (r.get("path_geojson") == null) return Optional.empty();
        try {
            GeoJsonLineString path = mapper.readValue((String) r.get("path_geojson"), GeoJsonLineString.class);
            return Optional.of(new GenerateRow(
                    path,
                    ((BigDecimal) r.get("total_m")).intValue(),
                    (BigDecimal) r.get("avg_score"),
                    (BigDecimal) r.get("error_pct"),
                    (String) r.get("region_code")
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to parse path_geojson", e);
        }
    }

    /**
     * 편도(oneway) 경로 생성. 시작→도착 최적 경로 하나만 반환.
     * 도보 그래프에서 두 지점이 연결 안 되어있으면 Optional.empty().
     */
    public Optional<OnewayRow> generateOnewayOnly(
            double startLng, double startLat,
            double endLng,   double endLat,
            LocalDateTime at, String persona
    ) {
        String sql = """
                SELECT path_geojson, total_m, avg_score, region_code
                  FROM routing.generate_oneway_only(?, ?, ?, ?, ?, ?)
                """;
        List<Map<String, Object>> rows = jdbc.queryForList(sql,
                startLng, startLat, endLng, endLat, at, persona);
        if (rows.isEmpty()) return Optional.empty();
        Map<String, Object> r = rows.get(0);
        if (r.get("path_geojson") == null) return Optional.empty();
        try {
            GeoJsonLineString path = mapper.readValue((String) r.get("path_geojson"), GeoJsonLineString.class);
            return Optional.of(new OnewayRow(
                    path,
                    ((BigDecimal) r.get("total_m")).intValue(),
                    (BigDecimal) r.get("avg_score"),
                    (String) r.get("region_code")
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to parse path_geojson", e);
        }
    }

    /** 사용자가 선택한 순환 코스 저장 → 새 courseId (하위호환용 오버로드) */
    public Long saveFromPath(GeoJsonLineString path, String regionCode) {
        return saveFromPath(path, regionCode, true, null, null, null);
    }

    /**
     * 사용자가 선택한 코스 저장 → 새 courseId.
     * isLoop=false 일 때 endLng/endLat 을 넘기면 end_point 로 저장, null이면 path의 마지막 점을 사용.
     */
    public Long saveFromPath(
            GeoJsonLineString path, String regionCode,
            boolean isLoop, Double endLng, Double endLat
    ) {
        return saveFromPath(path, regionCode, isLoop, endLng, endLat, null);
    }

    public Long saveFromPath(
            GeoJsonLineString path, String regionCode,
            boolean isLoop, Double endLng, Double endLat, String name
    ) {
        String pathJson;
        try {
            pathJson = mapper.writeValueAsString(path);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("failed to serialize path", e);
        }
        boolean hasEndPoint = !isLoop && endLng != null && endLat != null;
        String sql = hasEndPoint
                ? "SELECT routing.save_from_geom(?::text, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326))"
                : "SELECT routing.save_from_geom(?::text, ?, ?, ?, ?, NULL)";

        Long courseId;
        if (hasEndPoint) {
            courseId = jdbc.queryForObject(sql, Long.class,
                    pathJson, regionCode, LocalDateTime.now(), "2026-08-gs-yc-v1.2",
                    isLoop, endLng, endLat);
        } else {
            courseId = jdbc.queryForObject(sql, Long.class,
                    pathJson, regionCode, LocalDateTime.now(), "2026-08-gs-yc-v1.2",
                    isLoop);
        }
        if (StringUtils.hasText(name)) {
            jdbc.update("UPDATE public.course SET name = ? WHERE course_id = ?", name.trim(), courseId);
        }
        return courseId;
    }

    public record GenerateRow(GeoJsonLineString path, Integer totalM, BigDecimal avgScore,
                              BigDecimal errorPct, String regionCode) {}

    public record OnewayRow(GeoJsonLineString path, Integer totalM, BigDecimal avgScore,
                            String regionCode) {}
}
