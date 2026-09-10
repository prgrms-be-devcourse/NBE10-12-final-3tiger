package com.back.walk.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class WeeklyWalkQueryRepository {

    private static final String WEEKLY_AGGREGATE_SQL = """
            SELECT CAST(usage_log.used_at AS DATE) AS walked_date,
                   COUNT(*) AS walk_count,
                   COALESCE(SUM(course.distance_m), 0) AS distance_meters,
                   COALESCE(SUM(course.estimated_minutes), 0) AS minutes
              FROM course_usage_log usage_log
              JOIN course ON course.course_id = usage_log.course_id
             WHERE usage_log.user_id = ?
               AND usage_log.used_at >= ?
               AND usage_log.used_at < ?
             GROUP BY CAST(usage_log.used_at AS DATE)
             ORDER BY walked_date
            """;

    private final JdbcTemplate jdbcTemplate;

    public WeeklyWalkQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DailyWalkAggregate> findDailyAggregates(
            Long userId,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive
    ) {
        return jdbcTemplate.query(
                WEEKLY_AGGREGATE_SQL,
                (resultSet, rowNumber) -> new DailyWalkAggregate(
                        resultSet.getDate("walked_date").toLocalDate(),
                        resultSet.getLong("walk_count"),
                        resultSet.getLong("distance_meters"),
                        resultSet.getLong("minutes")
                ),
                userId,
                Timestamp.valueOf(startInclusive),
                Timestamp.valueOf(endExclusive)
        );
    }

    public record DailyWalkAggregate(
            LocalDate date,
            long walkCount,
            long distanceMeters,
            long minutes
    ) {}
}
