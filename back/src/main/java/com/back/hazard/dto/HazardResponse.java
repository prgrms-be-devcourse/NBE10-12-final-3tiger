package com.back.hazard.dto;

import com.back.hazard.domain.Hazard;

import java.time.LocalDateTime;

public record HazardResponse(
        Long hazardId,
        String hazardType,
        String severity,
        String content,
        int upvoteCount,
        LocalDateTime expiresAt
) {
    public static HazardResponse from(Hazard hazard) {
        return new HazardResponse(
                hazard.getId(),
                hazard.getHazardType(),
                hazard.getSeverity(),
                hazard.getContent(),
                hazard.getUpvoteCount(),
                hazard.getExpiresAt()
        );
    }
}
