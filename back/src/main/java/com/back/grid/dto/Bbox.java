package com.back.grid.dto;

import com.back.global.error.ApiException;
import org.springframework.http.HttpStatus;

public record Bbox(
        double minLng,
        double minLat,
        double maxLng,
        double maxLat
) {
    private static final double MAX_SPAN_DEGREES = 0.02;
    private static final double COMPARISON_EPSILON = 1e-9;

    public static Bbox parse(String value) {
        if (value == null || value.isBlank()) {
            throw badRequest("bbox는 필수입니다.");
        }

        String[] coordinates = value.split(",", -1);
        if (coordinates.length != 4) {
            throw badRequest("bbox는 minLng,minLat,maxLng,maxLat 형식이어야 합니다.");
        }

        try {
            Bbox bbox = new Bbox(
                    Double.parseDouble(coordinates[0].trim()),
                    Double.parseDouble(coordinates[1].trim()),
                    Double.parseDouble(coordinates[2].trim()),
                    Double.parseDouble(coordinates[3].trim())
            );
            bbox.validate();
            return bbox;
        } catch (NumberFormatException exception) {
            throw badRequest("bbox 좌표는 숫자여야 합니다.");
        }
    }

    private void validate() {
        if (!Double.isFinite(minLng) || !Double.isFinite(minLat)
                || !Double.isFinite(maxLng) || !Double.isFinite(maxLat)) {
            throw badRequest("bbox 좌표는 유한한 숫자여야 합니다.");
        }
        if (minLng < -180 || maxLng > 180 || minLat < -90 || maxLat > 90) {
            throw badRequest("bbox 좌표 범위가 올바르지 않습니다.");
        }
        if (minLng >= maxLng || minLat >= maxLat) {
            throw badRequest("bbox 최소 좌표는 최대 좌표보다 작아야 합니다.");
        }
        if (maxLng - minLng > MAX_SPAN_DEGREES + COMPARISON_EPSILON
                || maxLat - minLat > MAX_SPAN_DEGREES + COMPARISON_EPSILON) {
            throw badRequest("bbox 조회 범위는 위도와 경도 각각 0.02도 이하여야 합니다.");
        }
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
