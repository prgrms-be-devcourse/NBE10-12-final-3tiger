package com.back.course.dto;

import java.util.List;

public record GeoJsonLineString(
        String type,
        List<List<Double>> coordinates
) {
    public static GeoJsonLineString of(List<List<Double>> coords) {
        return new GeoJsonLineString("LineString", coords);
    }
}
