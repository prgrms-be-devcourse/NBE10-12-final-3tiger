package com.back.map.kakao.dto;

public record KakaoRouteDirectionsResponse(
        String status,
        Route route
) {
    public boolean isSuccess() {
        return "OK".equals(status)
                && route != null
                && route.properties() != null
                && route.properties().totalDistance() != null
                && route.properties().totalTime() != null;
    }

    public record Route(RouteProperties properties) {
    }

    public record RouteProperties(
            Integer totalDistance,
            Integer totalTime,
            String landingUrl
    ) {
    }
}
