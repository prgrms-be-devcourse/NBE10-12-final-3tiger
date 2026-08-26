package com.back.course.dto;

import jakarta.validation.constraints.NotNull;

public record SaveCourseRequest(
        @NotNull GeoJsonLineString path,
        @NotNull String regionCode
) {}
