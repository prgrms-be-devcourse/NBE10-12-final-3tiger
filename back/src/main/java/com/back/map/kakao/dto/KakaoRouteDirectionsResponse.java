package com.back.map.kakao.dto;

import java.util.List;

public record KakaoRouteDirectionsResponse(
        String status,
        Route route
) {
    public boolean isSuccess() {
        return "OK".equals(status)
                && route != null
                && route.properties() != null
                && route.legs() != null
                && !route.legs().isEmpty()
                && route.properties().totalDistance() != null
                && route.properties().totalTime() != null;
    }

    public record Route(
            RouteProperties properties,
            List<Leg> legs
    ) {
    }

    public record RouteProperties(
            Integer totalDistance,
            Integer totalTime,
            String landingUrl
    ) {
    }

    public record Leg(
            LegProperties properties,
            List<Step> steps
    ) {
    }

    public record LegProperties(
            Integer distance,
            Integer time
    ) {
    }

    public record Step(
            StepProperties properties,
            Path path
    ) {
    }

    public record StepProperties(
            Integer distance,
            String guidance,
            Integer time,
            Double x,
            Double y
    ) {
    }

    public record Path(List<List<Double>> points) {
    }
}
