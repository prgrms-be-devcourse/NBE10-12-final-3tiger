package com.back.course.navigation.dto;

import java.util.List;

public record CourseStartDirectionsResponse(
        Long courseId,
        DirectionsMode mode,
        DirectionsStatus status,
        boolean startable,
        int startableRadiusMeters,
        Destination destination,
        List<DirectionRoute> routes,
        String landingUrl
) {
    public record Destination(
            String name,
            double latitude,
            double longitude
    ) {
    }

    public record DirectionRoute(
            int routeIndex,
            String type,
            int distanceMeters,
            int estimatedSeconds,
            int transfers,
            Integer fareWon,
            List<RouteSegment> segments
    ) {
    }

    public record RouteSegment(
            int segmentIndex,
            String mode,
            String guidance,
            int distanceMeters,
            int estimatedSeconds,
            List<String> vehicleNames,
            List<Stop> stops,
            LineString path
    ) {
    }

    public record Stop(
            String name,
            String role
    ) {
    }

    public record LineString(
            String type,
            List<List<Double>> coordinates
    ) {
    }
}
