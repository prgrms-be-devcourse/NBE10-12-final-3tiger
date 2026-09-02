package com.back.course.navigation.dto;

import java.util.List;

public record CourseStartDirectionsResponse(
        Long courseId,
        DirectionsMode mode,
        DirectionsStatus status,
        boolean startable,
        int startableRadiusMeters,
        Destination destination,
        Integer distanceMeters,
        Integer estimatedSeconds,
        String landingUrl,
        List<TransitRoute> transitRoutes
) {
    public record Destination(
            String name,
            double latitude,
            double longitude
    ) {
    }

    public record TransitRoute(
            String type,
            int distanceMeters,
            int estimatedSeconds,
            int transfers,
            Integer fareWon
    ) {
    }
}
