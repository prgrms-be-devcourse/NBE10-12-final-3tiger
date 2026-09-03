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
import com.back.map.kakao.dto.KakaoTransitDirectionsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.ArrayList;
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
                    true, List.of(), null
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
                    true, List.of(), null
            );
        }
        if (!result.isSuccess()) {
            throw routeNotFound(mode);
        }

        var properties = result.route().properties();
        List<CourseStartDirectionsResponse.RouteSegment> segments =
                routeSegments(result.route().legs(), mode);
        var route = new CourseStartDirectionsResponse.DirectionRoute(
                0,
                mode.name(),
                properties.totalDistance(),
                properties.totalTime(),
                0,
                null,
                segments
        );

        return createResponse(
                startPoint, destinationName, mode,
                DirectionsStatus.ROUTE_AVAILABLE,
                false,
                List.of(route),
                validateLandingUrl(properties.landingUrl())
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
                    true, List.of(), null
            );
        }
        if (!result.isSuccess()) {
            throw new BusinessException(ErrorCode.KAKAO_TRANSIT_ROUTE_NOT_FOUND);
        }

        List<CourseStartDirectionsResponse.DirectionRoute> routes = new ArrayList<>();
        for (int routeIndex = 0; routeIndex < result.routes().size(); routeIndex++) {
            var kakaoRoute = result.routes().get(routeIndex);
            if (kakaoRoute == null || kakaoRoute.properties() == null) {
                throw directionsFailed();
            }

            var properties = kakaoRoute.properties();
            if (properties.type() == null
                    || properties.totalDistance() == null
                    || properties.totalTime() == null
                    || properties.transfers() == null) {
                throw directionsFailed();
            }

            routes.add(new CourseStartDirectionsResponse.DirectionRoute(
                    routeIndex,
                    properties.type(),
                    properties.totalDistance(),
                    properties.totalTime(),
                    properties.transfers(),
                    properties.fare() == null ? null : properties.fare().value(),
                    transitSegments(kakaoRoute.steps())
            ));
        }

        if (routes.isEmpty()) {
            throw new BusinessException(ErrorCode.KAKAO_TRANSIT_ROUTE_NOT_FOUND);
        }

        return createResponse(
                startPoint, destinationName, DirectionsMode.PUBLIC_TRANSIT,
                DirectionsStatus.ROUTE_AVAILABLE,
                false,
                routes,
                validateLandingUrl(result.properties().landingUrl())
        );
    }

    private List<CourseStartDirectionsResponse.RouteSegment> routeSegments(
            List<KakaoRouteDirectionsResponse.Leg> legs,
            DirectionsMode mode
    ) {
        if (legs == null || legs.isEmpty()) {
            throw directionsFailed();
        }

        List<CourseStartDirectionsResponse.RouteSegment> segments = new ArrayList<>();
        for (var leg : legs) {
            if (leg == null || leg.steps() == null) {
                throw directionsFailed();
            }
            for (var step : leg.steps()) {
                var properties = requireRouteStep(step);
                segments.add(new CourseStartDirectionsResponse.RouteSegment(
                        segments.size(),
                        mode.name(),
                        properties.guidance(),
                        properties.distance(),
                        properties.time(),
                        List.of(),
                        List.of(),
                        lineString(step.path().points())
                ));
            }
        }

        if (segments.isEmpty()) {
            throw directionsFailed();
        }
        return List.copyOf(segments);
    }

    private KakaoRouteDirectionsResponse.StepProperties requireRouteStep(
            KakaoRouteDirectionsResponse.Step step
    ) {
        if (step == null
                || step.properties() == null
                || step.properties().distance() == null
                || step.properties().time() == null
                || step.properties().guidance() == null
                || step.path() == null) {
            throw directionsFailed();
        }
        return step.properties();
    }

    private List<CourseStartDirectionsResponse.RouteSegment> transitSegments(
            List<KakaoTransitDirectionsResponse.Step> steps
    ) {
        if (steps == null || steps.isEmpty()) {
            throw directionsFailed();
        }

        List<CourseStartDirectionsResponse.RouteSegment> segments = new ArrayList<>();
        for (var step : steps) {
            if (step == null
                    || step.properties() == null
                    || step.properties().distance() == null
                    || step.properties().time() == null
                    || step.properties().guidance() == null
                    || step.path() == null) {
                throw directionsFailed();
            }

            var properties = step.properties();
            String segmentMode = transitMode(properties);
            segments.add(new CourseStartDirectionsResponse.RouteSegment(
                    segments.size(),
                    segmentMode,
                    properties.guidance(),
                    properties.distance(),
                    properties.time(),
                    vehicleNames(properties.vehicles()),
                    stops(properties.stops()),
                    lineString(step.path().points())
            ));
        }
        return List.copyOf(segments);
    }

    private String transitMode(
            KakaoTransitDirectionsResponse.StepProperties properties
    ) {
        if ("WALK".equals(properties.type())
                || "BUS".equals(properties.type())
                || "SUBWAY".equals(properties.type())) {
            return properties.type();
        }

        if (properties.vehicles() != null) {
            for (var vehicle : properties.vehicles()) {
                if (vehicle != null
                        && ("BUS".equals(vehicle.type()) || "SUBWAY".equals(vehicle.type()))) {
                    return vehicle.type();
                }
            }
            if (!properties.vehicles().isEmpty()) {
                throw directionsFailed();
            }
        }
        return "WALK";
    }

    private List<String> vehicleNames(
            List<KakaoTransitDirectionsResponse.Vehicle> vehicles
    ) {
        if (vehicles == null || vehicles.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (var vehicle : vehicles) {
            if (vehicle == null || vehicle.name() == null || vehicle.name().isBlank()) {
                throw directionsFailed();
            }
            names.add(vehicle.name());
        }
        return List.copyOf(names);
    }

    private List<CourseStartDirectionsResponse.Stop> stops(
            List<KakaoTransitDirectionsResponse.Stop> kakaoStops
    ) {
        if (kakaoStops == null || kakaoStops.isEmpty()) {
            return List.of();
        }

        List<CourseStartDirectionsResponse.Stop> stops = new ArrayList<>();
        for (int index = 0; index < kakaoStops.size(); index++) {
            var stop = kakaoStops.get(index);
            if (stop == null || stop.name() == null || stop.name().isBlank()) {
                throw directionsFailed();
            }
            String role = index == 0
                    ? "BOARDING"
                    : index == kakaoStops.size() - 1 ? "ALIGHTING" : "PASSING";
            stops.add(new CourseStartDirectionsResponse.Stop(stop.name(), role));
        }
        return List.copyOf(stops);
    }

    private CourseStartDirectionsResponse.LineString lineString(
            List<List<Double>> points
    ) {
        if (points == null || points.isEmpty()) {
            throw directionsFailed();
        }

        List<List<Double>> coordinates = new ArrayList<>();
        for (List<Double> point : points) {
            if (point == null || point.size() < 2) {
                throw directionsFailed();
            }
            Double longitude = point.get(0);
            Double latitude = point.get(1);
            if (longitude == null || latitude == null
                    || !Double.isFinite(longitude) || !Double.isFinite(latitude)
                    || longitude < -180 || longitude > 180
                    || latitude < -90 || latitude > 90) {
                throw directionsFailed();
            }
            coordinates.add(List.of(longitude, latitude));
        }
        return new CourseStartDirectionsResponse.LineString(
                "LineString",
                List.copyOf(coordinates)
        );
    }

    private BusinessException directionsFailed() {
        return new BusinessException(ErrorCode.KAKAO_DIRECTIONS_FAILED);
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
            List<CourseStartDirectionsResponse.DirectionRoute> routes,
            String landingUrl
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
                routes,
                landingUrl
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
