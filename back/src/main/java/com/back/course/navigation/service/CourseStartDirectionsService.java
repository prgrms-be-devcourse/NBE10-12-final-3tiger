package com.back.course.navigation.service;

import com.back.course.navigation.dto.CourseStartDirectionsResponse;
import com.back.course.navigation.dto.DirectionsMode;
import com.back.course.navigation.dto.DirectionsStatus;
import com.back.course.navigation.repository.CourseNavigationRepository;
import com.back.course.navigation.repository.CourseNavigationView;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.map.kakao.KakaoDirectionsClient;
import com.back.map.kakao.dto.KakaoRouteDirectionsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CourseStartDirectionsService {

    private static final int STARTABLE_RADIUS_METERS = 50;
    private static final double EARTH_RADIUS_METERS = 6_371_000;

    private final CourseNavigationRepository repository;
    private final KakaoDirectionsClient directionsClient;

    public CourseStartDirectionsService(
            CourseNavigationRepository repository,
            KakaoDirectionsClient directionsClient
    ) {
        this.repository = repository;
        this.directionsClient = directionsClient;
    }

    public CourseStartDirectionsResponse getDirectionsToStart(
            Long courseId,
            double currentLatitude,
            double currentLongitude,
            DirectionsMode mode
    ) {
        validateCoordinates(currentLatitude, currentLongitude);
        if (mode == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        CourseNavigationView startPoint =
                repository.findNavigationByCourseId(courseId)
                        .orElseThrow(() ->
                                new BusinessException(ErrorCode.COURSE_NOT_FOUND)
                        );

        validateStartPoint(startPoint);

        String destinationName = destinationName(startPoint.getName());

        if (distanceMeters(
                currentLatitude, currentLongitude,
                startPoint.getStartLat(), startPoint.getStartLng()
        ) <= STARTABLE_RADIUS_METERS) {
            return createResponse(
                    startPoint, destinationName, mode,
                    DirectionsStatus.ALREADY_NEAR_START,
                    true, 0, 0, null, List.of()
            );
        }

        return switch (mode) {
            case WALK -> routeResponse(
                    startPoint, destinationName, mode,
                    directionsClient.getWalk(
                            currentLatitude, currentLongitude,
                            startPoint.getStartLat(), startPoint.getStartLng(),
                            destinationName
                    )
            );
            case BICYCLE -> routeResponse(
                    startPoint, destinationName, mode,
                    directionsClient.getBicycle(
                            currentLatitude, currentLongitude,
                            startPoint.getStartLat(), startPoint.getStartLng(),
                            destinationName
                    )
            );
            case PUBLIC_TRANSIT -> transitResponse(
                    startPoint, destinationName,
                    currentLatitude, currentLongitude
            );
        };
    }

    private CourseStartDirectionsResponse routeResponse(
            CourseNavigationView startPoint,
            String destinationName,
            DirectionsMode mode,
            KakaoRouteDirectionsResponse result
    ) {
        if ("SAME_POINT".equals(result.status())) {
            return createResponse(
                    startPoint, destinationName, mode,
                    DirectionsStatus.ALREADY_NEAR_START,
                    true, 0, 0, null, List.of()
            );
        }
        if (!result.isSuccess()) {
            throw routeNotFound(mode);
        }

        var properties = result.route().properties();
        return createResponse(
                startPoint, destinationName, mode,
                DirectionsStatus.ROUTE_AVAILABLE,
                false,
                properties.totalDistance(),
                properties.totalTime(),
                validateLandingUrl(properties.landingUrl()),
                List.of()
        );
    }

    private CourseStartDirectionsResponse transitResponse(
            CourseNavigationView startPoint,
            String destinationName,
            double currentLatitude,
            double currentLongitude
    ) {
        var result = directionsClient.getPublicTransit(
                currentLatitude, currentLongitude,
                startPoint.getStartLat(), startPoint.getStartLng(),
                destinationName
        );

        if ("EQUAL_POINTS".equals(result.status())) {
            return createResponse(
                    startPoint, destinationName, DirectionsMode.PUBLIC_TRANSIT,
                    DirectionsStatus.ALREADY_NEAR_START,
                    true, 0, 0, null, List.of()
            );
        }
        if (!result.isSuccess()) {
            throw new BusinessException(ErrorCode.KAKAO_TRANSIT_ROUTE_NOT_FOUND);
        }

        List<CourseStartDirectionsResponse.TransitRoute> routes = result.routes().stream()
                .filter(route -> route != null && route.properties() != null)
                .map(route -> {
                    var properties = route.properties();
                    if (properties.totalDistance() == null
                            || properties.totalTime() == null
                            || properties.transfers() == null) {
                        throw new BusinessException(ErrorCode.KAKAO_DIRECTIONS_FAILED);
                    }
                    return new CourseStartDirectionsResponse.TransitRoute(
                            properties.type(),
                            properties.totalDistance(),
                            properties.totalTime(),
                            properties.transfers(),
                            properties.fare() == null ? null : properties.fare().value()
                    );
                })
                .toList();

        if (routes.isEmpty()) {
            throw new BusinessException(ErrorCode.KAKAO_TRANSIT_ROUTE_NOT_FOUND);
        }
        var representative = routes.getFirst();

        return createResponse(
                startPoint, destinationName, DirectionsMode.PUBLIC_TRANSIT,
                DirectionsStatus.ROUTE_AVAILABLE,
                false,
                representative.distanceMeters(),
                representative.estimatedSeconds(),
                validateLandingUrl(result.properties().landingUrl()),
                routes
        );
    }

    private void validateCoordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude)
                || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90
                || longitude < -180 || longitude > 180) {
            throw new BusinessException(ErrorCode.INVALID_COORDINATE);
        }
    }

    private void validateStartPoint(CourseNavigationView startPoint) {
        if (startPoint.getStartLat() == null || startPoint.getStartLng() == null) {
            throw new BusinessException(ErrorCode.COURSE_START_POINT_NOT_FOUND);
        }
        try {
            validateCoordinates(startPoint.getStartLat(), startPoint.getStartLng());
        } catch (BusinessException exception) {
            throw new BusinessException(ErrorCode.COURSE_START_POINT_NOT_FOUND);
        }
    }

    private String destinationName(String courseName) {
        if (courseName == null || courseName.isBlank()) {
            return "코스 출발점";
        }
        return courseName.trim() + " 출발점";
    }

    private String validateLandingUrl(String landingUrl) {
        if (landingUrl == null || landingUrl.isBlank()) {
            throw new BusinessException(ErrorCode.KAKAO_DIRECTIONS_FAILED);
        }
        try {
            URI uri = URI.create(landingUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"map.kakao.com".equalsIgnoreCase(uri.getHost())) {
                throw new BusinessException(ErrorCode.KAKAO_DIRECTIONS_FAILED);
            }
            return landingUrl;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.KAKAO_DIRECTIONS_FAILED);
        }
    }

    private BusinessException routeNotFound(DirectionsMode mode) {
        return new BusinessException(
                mode == DirectionsMode.BICYCLE
                        ? ErrorCode.KAKAO_BICYCLE_ROUTE_NOT_FOUND
                        : ErrorCode.KAKAO_WALK_ROUTE_NOT_FOUND
        );
    }

    private CourseStartDirectionsResponse createResponse(
            CourseNavigationView startPoint,
            String destinationName,
            DirectionsMode mode,
            DirectionsStatus status,
            boolean startable,
            Integer distanceMeters,
            Integer estimatedSeconds,
            String landingUrl,
            List<CourseStartDirectionsResponse.TransitRoute> transitRoutes
    ) {
        return new CourseStartDirectionsResponse(
                startPoint.getCourseId(),
                mode,
                status,
                startable,
                STARTABLE_RADIUS_METERS,
                new CourseStartDirectionsResponse.Destination(
                        destinationName,
                        startPoint.getStartLat(),
                        startPoint.getStartLng()
                ),
                distanceMeters,
                estimatedSeconds,
                landingUrl,
                transitRoutes
        );
    }

    private double distanceMeters(
            double startLat, double startLng,
            double endLat, double endLng
    ) {
        double latDistance = Math.toRadians(endLat - startLat);
        double lngDistance = Math.toRadians(endLng - startLng);
        double startLatRad = Math.toRadians(startLat);
        double endLatRad = Math.toRadians(endLat);
        double haversine = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(startLatRad) * Math.cos(endLatRad)
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(haversine));
    }
}
