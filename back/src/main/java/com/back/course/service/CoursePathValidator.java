package com.back.course.service;

import com.back.course.dto.GeoJsonLineString;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;

public final class CoursePathValidator {

    private CoursePathValidator() {
    }

    public static void validateForWrite(GeoJsonLineString path) {
        if (path == null ||
                !"LineString".equals(path.type()) ||
                path.coordinates() == null ||
                path.coordinates().size() < 2) {
            throw invalidPath();
        }

        for (var coordinate : path.coordinates()) {
            if (coordinate == null || coordinate.size() != 2) {
                throw invalidPath();
            }

            Double longitude = coordinate.get(0);
            Double latitude = coordinate.get(1);

            if (longitude == null || latitude == null ||
                    !Double.isFinite(longitude) || !Double.isFinite(latitude) ||
                    longitude < -180 || longitude > 180 ||
                    latitude < -90 || latitude > 90) {
                throw invalidPath();
            }
        }
    }

    private static BusinessException invalidPath() {
        return new BusinessException(ErrorCode.COURSE_PATH_DATA_INVALID);
    }
}
