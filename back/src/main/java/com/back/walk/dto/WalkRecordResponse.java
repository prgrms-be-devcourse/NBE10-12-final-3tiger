package com.back.walk.dto;

import java.time.LocalDateTime;

public record WalkRecordResponse(
        Long walkId,
        Long courseId,
        String courseName,
        LocalDateTime walkedAt,
        int distanceMeters,
        Integer durationSeconds,
        Integer rating
) {}
