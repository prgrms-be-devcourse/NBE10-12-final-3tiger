package com.back.course.navigation.service;

import com.back.course.navigation.dto.CourseNavigationResponse;
import com.back.course.dto.GeoJsonLineString;
import com.back.course.navigation.dto.NavigationPoint;
import com.back.course.navigation.repository.CourseNavigationRepository;
import com.back.course.navigation.repository.CourseNavigationView;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
public class CourseNavigationService {

    private final CourseNavigationRepository repository;
    private final ObjectMapper objectMapper;

    public CourseNavigationService(
            CourseNavigationRepository repository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Cacheable(cacheNames = "courseNavigation", key = "#courseId", sync = true)
    public CourseNavigationResponse getNavigation(Long courseId) {
        CourseNavigationView view = repository
                .findNavigationByCourseId(courseId)
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.COURSE_NOT_FOUND
                        )
                );

        validateNavigable(view);
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

            return path;
        } catch (BusinessException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw new BusinessException(
                    ErrorCode.COURSE_PATH_DATA_INVALID
            );
        }
    }

}
