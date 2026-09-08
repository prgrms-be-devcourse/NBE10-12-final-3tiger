package com.back.map.naver.dto;

import java.util.List;

public record NaverReverseGeocodeResponse(
        Status status,
        List<Result> results
) {
    public record Status(
            int code,
            String name,
            String message
    ) {}

    public record Result(
            String name,
            Region region,
            Land land
    ) {}

    public record Region(
            Area area0,
            Area area1,
            Area area2,
            Area area3,
            Area area4
    ) {}

    public record Area(String name) {}

    public record Land(
            String type,
            String number1,
            String number2,
            String name
    ) {}
}
