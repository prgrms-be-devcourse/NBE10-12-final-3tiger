package com.back.walk.reservation.dto;

import com.back.walk.reservation.domain.WalkReservationStatus;

import java.time.LocalDateTime;

public record WalkReservationResponse(
        Long reservationId,
        Long courseId,
        String courseName,
        LocalDateTime scheduledAt,
        WalkReservationStatus status
) {}
