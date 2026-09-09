package com.back.course.map.event;

import com.back.course.dto.GeoJsonLineString;

public record CourseMapImageRequested(Long courseId, GeoJsonLineString path) {
}
