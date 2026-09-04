package com.back.hazard.dto;

public record WalkEdgeCandidate(
        Long edgeId,
        Long source,
        Long target,
        double distanceM
) {
}
