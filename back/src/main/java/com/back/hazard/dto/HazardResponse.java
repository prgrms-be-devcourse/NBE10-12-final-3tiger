package com.back.hazard.dto;

import com.back.hazard.domain.Hazard;
import com.back.hazard.domain.HazardStatus;

import java.time.LocalDateTime;

public record HazardResponse(
        Long hazardId,
        String hazardType,
        HazardStatus status,
        long confirmationCount,
        LocalDateTime createdAt,
        LocalDateTime activatedAt
) {
    public static HazardResponse from(Hazard hazard, long confirmationCount) {
        return new HazardResponse(
                hazard.getId(),
                hazard.getHazardType(),
                hazard.getStatus(),
                confirmationCount,
                hazard.getCreatedAt(),
                hazard.getActivatedAt()
        );
    }
}
