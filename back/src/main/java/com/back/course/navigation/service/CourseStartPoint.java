package com.back.course.navigation.service;

public record CourseStartPoint(
        Long courseId,
        String name,
        Double latitude,
        Double longitude
) {
}
