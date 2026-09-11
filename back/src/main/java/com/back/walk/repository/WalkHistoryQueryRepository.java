package com.back.walk.repository;

import com.back.global.api.PageResponse;
import com.back.walk.dto.WalkRecordResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class WalkHistoryQueryRepository {

    private static final String HISTORY_SQL = """
            SELECT usage_log.usage_log_id,
                   course.course_id,
                   course.name AS course_name,
                   usage_log.used_at,
                   course.distance_m,
                   usage_log.duration_seconds,
                   bookmark.rating
              FROM course_usage_log usage_log
              JOIN course ON course.course_id = usage_log.course_id
              LEFT JOIN bookmark
                ON bookmark.user_id = usage_log.user_id
               AND bookmark.course_id = usage_log.course_id
             WHERE usage_log.user_id = ?
             ORDER BY usage_log.used_at DESC, usage_log.usage_log_id DESC
             LIMIT ? OFFSET ?
            """;

    private static final String COUNT_SQL = """
            SELECT COUNT(*)
              FROM course_usage_log
             WHERE user_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public WalkHistoryQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResponse<WalkRecordResponse> findByUserId(Long userId, int page, int size) {
        long offset = (long) page * size;
        List<WalkRecordResponse> content = jdbcTemplate.query(
                HISTORY_SQL,
                (resultSet, rowNumber) -> new WalkRecordResponse(
                        resultSet.getLong("usage_log_id"),
                        resultSet.getLong("course_id"),
                        resultSet.getString("course_name"),
                        resultSet.getTimestamp("used_at").toLocalDateTime(),
                        resultSet.getInt("distance_m"),
                        resultSet.getObject("duration_seconds", Integer.class),
                        resultSet.getObject("rating", Integer.class)
                ),
                userId,
                size,
                offset
        );
        Long totalElements = jdbcTemplate.queryForObject(COUNT_SQL, Long.class, userId);

        return new PageResponse<>(content, page, size, totalElements == null ? 0 : totalElements);
    }
}
