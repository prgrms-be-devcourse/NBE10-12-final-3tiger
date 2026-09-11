package com.back.walk.reservation.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record WalkReservationRequest(
        @NotNull Long courseId,
        @NotNull OffsetDateTime scheduledAt
) {}
