package com.back.course.service;

import com.back.bookmark.repository.BookmarkRepository;
import com.back.course.repository.CourseDetailView;
import com.back.course.domain.Persona;
import com.back.course.repository.CourseListView;
import com.back.course.repository.CourseRepository;
import com.back.global.api.PageResponse;
import com.back.global.error.ApiException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courses;
    private final BookmarkRepository bookmarks;
    private final ObjectMapper objectMapper;

    public CourseService(CourseRepository courses, BookmarkRepository bookmarks, ObjectMapper objectMapper) {
        this.courses = courses;
        this.bookmarks = bookmarks;
        this.objectMapper = objectMapper;
    }

    public CourseDetail getDetail(Long courseId, Long userId, LocalDateTime at) {
        boolean useSummer = isSummer(at != null ? at : LocalDateTime.now());
        CourseDetailView view = courses.findDetailById(courseId, useSummer)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 코스입니다."));

        boolean isBookmarked = userId != null && bookmarks.existsByUserIdAndCourseId(userId, courseId);
        Double flatness = view.getFlatness();
        Double avgSlopeDegree = flatness == null ? null : (1.0 - flatness) * 30.0;

        ScoreBars bars = new ScoreBars(flatness, avgSlopeDegree, view.getShade(), view.getSurfaceTemp(), view.getAmenity(),
                view.getSurfaceNatural(), view.getBenchDensity(), view.getRestroomProximity(),
                view.getWaterFacility(), view.getPavementQuality());
        String summary = buildSummary(bars, useSummer);
        return new CourseDetail(
                view.getCourseId(), view.getName(), parsePath(view.getPathGeoJson()), view.getMapImageUrl(),
                view.getDistanceM(), view.getEstimatedMinutes(),
                view.getElevationGainM(), view.getElevationLossM(),
                Boolean.TRUE.equals(view.getIsLoop()), view.getSource(),
                bars,
                view.getScoreWalker(), view.getScoreSenior(), view.getScoreStroller(), view.getScoreDog(),
                view.getSurfaceType(), summary, List.of(), isBookmarked
        );
    }

    // 규칙 기반 요약: 지표 값 상위 특징 2~3개 뽑아 자연어 문장 조립.
    // 임계값은 실측 grid_score 평균(shade 0.14, flatness 0.87, surface_natural 0.29 등)을 기준으로 정함.
    static String buildSummary(ScoreBars b, boolean isSummer) {
        if (b == null) return null;
        List<String> traits = new ArrayList<>();
        if (b.flatness() != null && b.flatness() >= 0.85) traits.add("평탄한 노면");
        if (isSummer && b.shade() != null && b.shade() >= 0.25) traits.add("그늘 확보");
        if (b.surfaceNatural() != null && b.surfaceNatural() >= 0.40) traits.add("자연 노면 비율 높음");
        if (b.pavementQuality() != null && b.pavementQuality() >= 0.60) traits.add("보도 포장 양호");
        if (b.benchDensity() != null && b.benchDensity() >= 0.50) traits.add("벤치 다수");
        if (b.restroomProximity() != null && b.restroomProximity() >= 0.50) traits.add("화장실 접근 편리");
        if (b.waterFacility() != null && b.waterFacility() >= 0.50) traits.add("음수대 이용 가능");

        if (traits.isEmpty()) return "일반적인 산책 코스입니다.";
        List<String> top = traits.subList(0, Math.min(3, traits.size()));
        return String.join(", ", top) + " 특징의 산책 코스입니다.";
    }

    private GeoJsonLineString parsePath(String geoJson) {
        if (geoJson == null) {
            return null;
        }
        try {
            return objectMapper.readValue(geoJson, GeoJsonLineString.class);
        } catch (JacksonException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "코스 경로 데이터를 읽을 수 없습니다.");
        }
    }

    public PageResponse<CourseItem> search(CourseSearchQuery q) {
        boolean useSummer = isSummer(q.at() != null ? q.at() : LocalDateTime.now());
        int offset = q.page() * q.size();
        String persona = q.persona() != null ? q.persona().name() : null;

        List<CourseListView> rows;
        long total;
        if (q.regionCode() != null) {
            rows = courses.searchByRegion(q.regionCode(), q.isLoop(), q.distanceMinM(), q.distanceMaxM(),
                    useSummer, q.sort(), persona, q.size(), offset);
            total = courses.countByRegion(q.regionCode(), q.isLoop(), q.distanceMinM(), q.distanceMaxM());
        } else {
            rows = courses.searchByLocation(q.lat(), q.lng(), q.radiusM(), q.isLoop(),
                    q.distanceMinM(), q.distanceMaxM(), useSummer, q.sort(), persona, q.size(), offset);
            total = courses.countByLocation(q.lat(), q.lng(), q.radiusM(), q.isLoop(),
                    q.distanceMinM(), q.distanceMaxM());
        }

        List<CourseItem> items = rows.stream().map(CourseService::toItem).toList();
        return new PageResponse<>(items, q.page(), q.size(), total);
    }

    private static boolean isSummer(LocalDateTime at) {
        int month = at.getMonthValue();
        return month >= 6 && month <= 8;
    }

    private static CourseItem toItem(CourseListView v) {
        Double flatness = v.getFlatness();
        Double avgSlopeDegree = flatness == null ? null : (1.0 - flatness) * 30.0;
        return new CourseItem(
                v.getCourseId(),
                v.getName(),
                v.getDistanceM(),
                v.getEstimatedMinutes(),
                Boolean.TRUE.equals(v.getIsLoop()),
                new Point(v.getStartLat(), v.getStartLng()),
                new Scores(flatness, avgSlopeDegree, v.getShadeScore(), null, v.getWheelchair(), null),
                null,
                List.of()
        );
    }

    public record CourseSearchQuery(
            String regionCode,
            Double lat, Double lng, Integer radiusM,
            Persona persona,
            Integer distanceMinM, Integer distanceMaxM,
            Boolean isLoop,
            LocalDateTime at,
            String sort,
            int page, int size
    ) {}

    public record CourseItem(
            Long courseId,
            String name,
            int distanceM,
            int estimatedMinutes,
            boolean isLoop,
            Point startPoint,
            Scores scores,
            Double surfaceTempC,
            List<String> personaBadges
    ) {}

    public record Point(double lat, double lng) {}

    public record Scores(
            Double flatness,
            Double avgSlopeDegree,
            Double shadeSummer,
            Double windShelter,
            Double wheelchair,
            String surfaceType
    ) {}

    public record CourseDetail(
            Long courseId,
            String name,
            GeoJsonLineString path,
            String mapImageUrl,
            int distanceM,
            int estimatedMinutes,
            Integer elevationGainM,
            Integer elevationLossM,
            boolean isLoop,
            String source,
            ScoreBars scoreBars,
            Double scoreWalker,
            Double scoreSenior,
            Double scoreStroller,
            Double scoreDog,
            String surfaceType,
            String summary,
            List<String> personaBadges,
            boolean isBookmarked
    ) {}

    public record GeoJsonLineString(String type, List<List<Double>> coordinates) {}

    public record ScoreBars(
            Double flatness,
            Double avgSlopeDegree,
            Double shade,
            Double surfaceTemp,
            Double amenity,
            Double surfaceNatural,
            Double benchDensity,
            Double restroomProximity,
            Double waterFacility,
            Double pavementQuality
    ) {}
}
