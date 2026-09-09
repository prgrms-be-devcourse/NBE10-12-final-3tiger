package com.back.hazard.dto;

import com.back.hazard.domain.Hazard;
import com.back.hazard.domain.HazardStatus;

import java.time.LocalDateTime;

public record HazardResponse(
        Long hazardId,
        String hazardType,
        HazardStatus status,
        double latitude,
        double longitude,
        long reportCount,
        long confirmationCount,
        boolean reportedByMe,
        LocalDateTime createdAt,
        LocalDateTime activatedAt
) {
    public static HazardResponse from(
            Hazard hazard,
            double latitude,
            double longitude,
            long confirmationCount
    ) {
        return from(hazard, latitude, longitude, 0L, confirmationCount);
    }

    public static HazardResponse from(
            Hazard hazard,
            double latitude,
            double longitude,
            long reportCount,
            long confirmationCount
    ) {
        return new HazardResponse(
                hazard.getId(),
                hazard.getHazardType(),
                hazard.getStatus(),
                latitude,
                longitude,
                reportCount,
                confirmationCount,
                false,
                hazard.getCreatedAt(),
                hazard.getActivatedAt()
        );
    }
}
