package com.back.course.service;

import com.back.course.domain.Persona;
import com.back.course.repository.CourseListView;
import com.back.course.repository.CourseRepository;
import com.back.global.api.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courses;

    public CourseService(CourseRepository courses) {
        this.courses = courses;
    }

    public PageResponse<CourseItem> search(CourseSearchQuery q) {
        boolean useSummer = isSummer(q.at() != null ? q.at() : LocalDateTime.now());
        int offset = q.page() * q.size();

        List<CourseListView> rows;
        long total;
        if (q.regionCode() != null) {
            rows = courses.searchByRegion(q.regionCode(), q.isLoop(), q.distanceMinM(), q.distanceMaxM(),
                    useSummer, q.sort(), q.size(), offset);
            total = courses.countByRegion(q.regionCode(), q.isLoop(), q.distanceMinM(), q.distanceMaxM());
        } else {
            rows = courses.searchByLocation(q.lat(), q.lng(), q.radiusM(), q.isLoop(),
                    q.distanceMinM(), q.distanceMaxM(), useSummer, q.sort(), q.size(), offset);
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
}
