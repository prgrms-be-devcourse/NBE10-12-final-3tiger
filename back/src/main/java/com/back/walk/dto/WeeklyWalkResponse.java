package com.back.walk.dto;

import java.time.LocalDate;
import java.util.List;

public record WeeklyWalkResponse(
        LocalDate weekStart,
        LocalDate weekEnd,
        long totalWalkCount,
        long totalDistanceMeters,
        long totalMinutes,
        List<DailyWalkRecord> dailyRecords
) {
    public record DailyWalkRecord(
            LocalDate date,
            long walkCount,
            long distanceMeters,
            long minutes
    ) {}
}
