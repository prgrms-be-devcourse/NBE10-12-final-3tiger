package com.back.course.navigation.service;

import com.back.course.navigation.dto.CourseNavigationResponse;
import com.back.course.dto.GeoJsonLineString;
import com.back.course.navigation.dto.NavigationPoint;
import com.back.course.navigation.repository.CourseNavigationRepository;
import com.back.course.navigation.repository.CourseNavigationView;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
public class CourseNavigationService {

    private static final Logger log =
            LoggerFactory.getLogger(CourseNavigationService.class);

    private static final double DISTANCE_WARNING_RATE = 0.10;
    private static final double LOOP_MAX_GAP_M = 30.0;

    private final CourseNavigationRepository repository;
    private final ObjectMapper objectMapper;

    public CourseNavigationService(
            CourseNavigationRepository repository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public CourseNavigationResponse getNavigation(Long courseId) {
        CourseNavigationView view = repository
                .findNavigationByCourseId(courseId)
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.COURSE_NOT_FOUND
                        )
                );

        validateNavigable(view);
        logDataWarnings(view);

        GeoJsonLineString path =
                parsePath(view.getPathGeoJson());

        return new CourseNavigationResponse(
                view.getCourseId(),
                view.getName(),
                view.getDistanceM(),
                view.getEstimatedMinutes(),
                Boolean.TRUE.equals(view.getIsLoop()),
                new NavigationPoint(
                        view.getStartLat(),
                        view.getStartLng()
                ),
                new NavigationPoint(
                        view.getEndLat(),
                        view.getEndLng()
                ),
                path
        );
    }

    private void validateNavigable(
            CourseNavigationView view
    ) {
        if (view.getPathGeoJson() == null) {
            throw new BusinessException(
                    ErrorCode.COURSE_NOT_NAVIGABLE
            );
        }

        if (Boolean.TRUE.equals(view.getPathEmpty())) {
            throw new BusinessException(
                    ErrorCode.COURSE_NOT_NAVIGABLE
            );
        }

        if (!Boolean.TRUE.equals(view.getPathValid())) {
            throw new BusinessException(
                    ErrorCode.COURSE_NOT_NAVIGABLE
            );
        }

        if (!"LINESTRING".equalsIgnoreCase(
                view.getGeometryType()
        )) {
            throw new BusinessException(
                    ErrorCode.COURSE_NOT_NAVIGABLE
            );
        }

        if (view.getSrid() == null ||
                view.getSrid() != 4326) {
            throw new BusinessException(
                    ErrorCode.COURSE_NOT_NAVIGABLE
            );
        }

        if (view.getCoordinateCount() == null ||
                view.getCoordinateCount() < 2) {
            throw new BusinessException(
                    ErrorCode.COURSE_NOT_NAVIGABLE
            );
        }

        if (view.getStartLat() == null ||
                view.getStartLng() == null ||
                view.getEndLat() == null ||
                view.getEndLng() == null) {
            throw new BusinessException(
                    ErrorCode.COURSE_NOT_NAVIGABLE
            );
        }

        validateCoordinate(
                view.getStartLat(),
                view.getStartLng()
        );

        validateCoordinate(
                view.getEndLat(),
                view.getEndLng()
        );
    }

    private void validateCoordinate(
            double lat,
            double lng
    ) {
        if (!Double.isFinite(lat) ||
                !Double.isFinite(lng) ||
                lat < -90 ||
                lat > 90 ||
                lng < -180 ||
                lng > 180) {
            throw new BusinessException(
                    ErrorCode.COURSE_NOT_NAVIGABLE
            );
        }
    }

    private GeoJsonLineString parsePath(
            String pathGeoJson
    ) {
        try {
            GeoJsonLineString path = objectMapper.readValue(
                    pathGeoJson,
                    GeoJsonLineString.class
            );

            if (!"LineString".equals(path.type()) ||
                    path.coordinates() == null ||
                    path.coordinates().size() < 2) {
                throw new BusinessException(
                        ErrorCode.COURSE_NOT_NAVIGABLE
                );
            }

            validatePathCoordinates(path);
            return path;
        } catch (BusinessException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw new BusinessException(
                    ErrorCode.COURSE_PATH_DATA_INVALID
            );
        }
    }

    private void validatePathCoordinates(
            GeoJsonLineString path
    ) {
        for (var coordinate : path.coordinates()) {
            if (coordinate == null ||
                    coordinate.size() < 2) {
                throw new BusinessException(
                        ErrorCode.COURSE_NOT_NAVIGABLE
                );
            }

            Double lng = coordinate.get(0);
            Double lat = coordinate.get(1);

            if (lng == null || lat == null) {
                throw new BusinessException(
                        ErrorCode.COURSE_NOT_NAVIGABLE
                );
            }

            validateCoordinate(lat, lng);
        }
    }

    private void logDataWarnings(
            CourseNavigationView view
    ) {
        Double calculated = view.getCalculatedDistanceM();

        if (calculated != null && calculated > 0) {
            double errorRate = Math.abs(
                    view.getDistanceM() - calculated
            ) / calculated;

            if (errorRate > DISTANCE_WARNING_RATE) {
                log.warn(
                        "Course distance mismatch. " +
                                "courseId={}, stored={}, calculated={}",
                        view.getCourseId(),
                        view.getDistanceM(),
                        calculated
                );
            }
        }

        if (Boolean.TRUE.equals(view.getIsLoop()) &&
                view.getStartEndDistanceM() != null &&
                view.getStartEndDistanceM() > LOOP_MAX_GAP_M) {
            log.warn(
                    "Loop course start/end gap is too large. " +
                            "courseId={}, gapM={}",
                    view.getCourseId(),
                    view.getStartEndDistanceM()
            );
        }
    }
}
