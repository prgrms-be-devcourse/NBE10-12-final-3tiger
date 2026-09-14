package com.back.course.repository;

import com.back.course.dto.GeoJsonLineString;
import com.back.course.map.domain.CourseMapImageStatus;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class CourseGenerationRepository {

    /** routing.generate_only 계열에서 사용자 지점이 도보 그래프에서 너무 멀 때 발생시키는 SQLSTATE. */
    private static final String SQLSTATE_OFF_ROAD_START = "P1001";
    private static final String SQLSTATE_OFF_ROAD_END = "P1002";

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
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(sql, lng, lat, targetM, at, candidateIdx, persona);
        } catch (DataAccessException e) {
            throw translateRoutingException(e);
        }
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
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(sql,
                    startLng, startLat, endLng, endLat, at, persona);
        } catch (DataAccessException e) {
            throw translateRoutingException(e);
        }
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

    public void markMapImagePending(Long courseId) {
        updateMapImageStatus(courseId, CourseMapImageStatus.PENDING);
    }

    public void markMapImageCompleted(Long courseId, String mapImageUrl) {
        jdbc.update(
                """
                UPDATE public.course
                   SET map_image_url = ?, map_image_status = ?
                 WHERE course_id = ?
                """,
                mapImageUrl,
                CourseMapImageStatus.COMPLETED.name(),
                courseId
        );
    }

    public void markMapImageFailed(Long courseId) {
        jdbc.update(
                """
                UPDATE public.course
                   SET map_image_url = NULL, map_image_status = ?
                 WHERE course_id = ?
                """,
                CourseMapImageStatus.FAILED.name(),
                courseId
        );
    }

    private void updateMapImageStatus(Long courseId, CourseMapImageStatus status) {
        jdbc.update(
                "UPDATE public.course SET map_image_status = ? WHERE course_id = ?",
                status.name(),
                courseId
        );
    }

    /**
     * routing.generate_* 함수의 커스텀 SQLSTATE (P1001/P1002) 예외를 도메인 예외로 변환.
     * 이외의 예외는 그대로 재던져 GlobalExceptionHandler 가 처리하도록 둔다.
     */
    private RuntimeException translateRoutingException(DataAccessException e) {
        SQLException sqlException = findSqlException(e);
        if (sqlException != null) {
            String sqlState = sqlException.getSQLState();
            if (SQLSTATE_OFF_ROAD_START.equals(sqlState)) {
                return new BusinessException(ErrorCode.COURSE_START_POINT_OFF_ROAD);
            }
            if (SQLSTATE_OFF_ROAD_END.equals(sqlState)) {
                return new BusinessException(ErrorCode.COURSE_END_POINT_OFF_ROAD);
            }
        }
        return e;
    }

    private SQLException findSqlException(Throwable t) {
        Throwable cursor = t;
        while (cursor != null) {
            if (cursor instanceof SQLException sqlException) return sqlException;
            cursor = cursor.getCause();
        }
        return null;
    }

    public record GenerateRow(GeoJsonLineString path, Integer totalM, BigDecimal avgScore,
                              BigDecimal errorPct, String regionCode) {}

    public record OnewayRow(GeoJsonLineString path, Integer totalM, BigDecimal avgScore,
                            String regionCode) {}
}
