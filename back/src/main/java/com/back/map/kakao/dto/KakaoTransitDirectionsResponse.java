package com.back.map.kakao.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record KakaoTransitDirectionsResponse(
        String status,
        Properties properties,
        List<Route> routes
) {
    public boolean isSuccess() {
        return "OK".equals(status)
                && properties != null
                && routes != null
                && !routes.isEmpty();
    }

    public record Properties(
            Integer total,
            Integer bus,
            Integer subway,
            Integer busAndSubway,
            @JsonProperty("landingURL") String landingUrl
    ) {
    }

    public record Route(RouteProperties properties) {
    }

    public record RouteProperties(
            String type,
            Integer totalDistance,
            Integer totalTime,
            Integer transfers,
            Fare fare
    ) {
    }

    public record Fare(Integer value, Integer min, Integer max) {
    }
}
