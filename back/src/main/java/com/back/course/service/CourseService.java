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
import java.util.List;
import java.util.Set;

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

        return new CourseDetail(
                view.getCourseId(), view.getName(), parsePath(view.getPathGeoJson()),
                view.getDistanceM(), view.getEstimatedMinutes(),
                view.getElevationGainM(), view.getElevationLossM(),
                Boolean.TRUE.equals(view.getIsLoop()), view.getSource(),
                new ScoreBars(flatness, avgSlopeDegree, view.getShade(), view.getSurfaceTemp(), view.getAmenity()),
                view.getSurfaceType(), null, List.of(), isBookmarked
        );
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

    public PageResponse<CourseItem> search(CourseSearchQuery q, Long userId) {
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

        List<Long> courseIds = rows.stream().map(CourseListView::getCourseId).toList();
        Set<Long> bookmarkedCourseIds = userId == null || courseIds.isEmpty()
                ? Set.of()
                : bookmarks.findBookmarkedCourseIds(userId, courseIds);

        List<CourseItem> items = rows.stream()
                .map(row -> toItem(row, bookmarkedCourseIds.contains(row.getCourseId())))
                .toList();
        return new PageResponse<>(items, q.page(), q.size(), total);
    }

    private static boolean isSummer(LocalDateTime at) {
        int month = at.getMonthValue();
        return month >= 6 && month <= 8;
    }

    private static CourseItem toItem(CourseListView v, boolean isBookmarked) {
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
                List.of(),
                isBookmarked
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
            List<String> personaBadges,
            boolean isBookmarked
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
            int distanceM,
            int estimatedMinutes,
            Integer elevationGainM,
            Integer elevationLossM,
            boolean isLoop,
            String source,
            ScoreBars scoreBars,
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
            Double amenity
    ) {}
}
