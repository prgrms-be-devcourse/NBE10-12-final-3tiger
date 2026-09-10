package com.back.walk.service;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.walk.dto.WeeklyWalkResponse;
import com.back.walk.dto.WeeklyWalkResponse.DailyWalkRecord;
import com.back.walk.repository.WeeklyWalkQueryRepository;
import com.back.walk.repository.WeeklyWalkQueryRepository.DailyWalkAggregate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class WeeklyWalkService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final int DAYS_PER_WEEK = 7;

    private final WeeklyWalkQueryRepository repository;

    public WeeklyWalkService(WeeklyWalkQueryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public WeeklyWalkResponse getWeeklyWalks(Long userId, LocalDate requestedWeekStart) {
        LocalDate weekStart = requestedWeekStart == null ? currentWeekStart() : requestedWeekStart;
        validateMonday(weekStart);

        LocalDate endExclusive = weekStart.plusDays(DAYS_PER_WEEK);
        Map<LocalDate, DailyWalkAggregate> aggregateByDate = repository.findDailyAggregates(
                        userId,
                        weekStart.atStartOfDay(),
                        endExclusive.atStartOfDay()
                ).stream()
                .collect(Collectors.toMap(DailyWalkAggregate::date, Function.identity()));

        List<DailyWalkRecord> dailyRecords = IntStream.range(0, DAYS_PER_WEEK)
                .mapToObj(weekStart::plusDays)
                .map(date -> toDailyRecord(date, aggregateByDate.get(date)))
                .toList();

        return new WeeklyWalkResponse(
                weekStart,
                endExclusive.minusDays(1),
                dailyRecords.stream().mapToLong(DailyWalkRecord::walkCount).sum(),
                dailyRecords.stream().mapToLong(DailyWalkRecord::distanceMeters).sum(),
                dailyRecords.stream().mapToLong(DailyWalkRecord::minutes).sum(),
                dailyRecords
        );
    }

    private LocalDate currentWeekStart() {
        return LocalDate.now(SEOUL_ZONE)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private void validateMonday(LocalDate weekStart) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private DailyWalkRecord toDailyRecord(LocalDate date, DailyWalkAggregate aggregate) {
        if (aggregate == null) {
            return new DailyWalkRecord(date, 0, 0, 0);
        }
        return new DailyWalkRecord(
                date,
                aggregate.walkCount(),
                aggregate.distanceMeters(),
                aggregate.minutes()
        );
    }
}
