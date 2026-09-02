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

    public record Route(
            RouteProperties properties,
            List<Step> steps
    ) {
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

    public record Step(
            StepProperties properties,
            Path path
    ) {
    }

    public record StepProperties(
            String guidance,
            String type,
            Integer distance,
            Integer time,
            List<Stop> stops,
            List<Vehicle> vehicles
    ) {
    }

    public record Stop(String name) {
    }

    public record Vehicle(String type, String name) {
    }

    public record Path(List<List<Double>> points) {
    }
}
