package com.back.hazard.dto;

public record HazardConfirmationResponse(
        boolean confirmed,
        long confirmationCount
) {
}
