package com.back.course.repository;

import com.back.course.dto.GeoJsonLineString;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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

    /** 저장 없이 경로만 생성 */
    public Optional<GenerateRow> generateOnly(
            double lng, double lat, int targetM, LocalDateTime at, int candidateIdx
    ) {
        String sql = """
                SELECT path_geojson, total_m, avg_score, error_pct, region_code
                  FROM routing.generate_only(?, ?, ?, ?, ?)
                """;
        List<Map<String, Object>> rows = jdbc.queryForList(sql, lng, lat, targetM, at, candidateIdx);
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

    /** 사용자가 선택한 path를 저장 → 새 courseId */
    public Long saveFromPath(GeoJsonLineString path, String regionCode) {
        String pathJson;
        try {
            pathJson = mapper.writeValueAsString(path);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("failed to serialize path", e);
        }
        String sql = "SELECT routing.save_from_geom(?::text, ?, ?, ?)";
        return jdbc.queryForObject(sql, Long.class,
                pathJson, regionCode, LocalDateTime.now(), "2026-08-gs-yc-v1.2");
    }

    public record GenerateRow(GeoJsonLineString path, Integer totalM, BigDecimal avgScore,
                              BigDecimal errorPct, String regionCode) {}
}
