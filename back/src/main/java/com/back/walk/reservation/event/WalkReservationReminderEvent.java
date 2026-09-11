package com.back.walk.reservation.event;

public record WalkReservationReminderEvent(
        Long userId,
        Long reservationId,
        String courseName
) {}
