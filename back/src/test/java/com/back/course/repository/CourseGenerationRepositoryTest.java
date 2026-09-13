package com.back.course.repository;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * routing.generate_only / generate_oneway_only 이 발생시키는 커스텀 SQLSTATE 를
 * BusinessException 으로 변환하는 로직 검증. #169 회귀 방지.
 */
class CourseGenerationRepositoryTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CourseGenerationRepository repo = new CourseGenerationRepository(jdbc);
    private final LocalDateTime at = LocalDateTime.of(2026, 9, 13, 10, 0);

    @Test
    void generateOnly_translatesP1001SqlStateToStartPointOffRoad() {
        doThrow(offRoadException("P1001", "OFF_ROAD_START_POINT"))
                .when(jdbc).queryForList(anyString(), any(Object[].class));

        assertThatThrownBy(
                () -> repo.generateOnly(126.844, 37.55, 3000, at, 0, "walker")
        ).isInstanceOfSatisfying(BusinessException.class, ex ->
                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.COURSE_START_POINT_OFF_ROAD)
        );
    }

    @Test
    void generateOnewayOnly_translatesP1002SqlStateToEndPointOffRoad() {
        doThrow(offRoadException("P1002", "OFF_ROAD_END_POINT"))
                .when(jdbc).queryForList(anyString(), any(Object[].class));

        assertThatThrownBy(
                () -> repo.generateOnewayOnly(126.844, 37.55, 126.85, 37.552, at, "walker")
        ).isInstanceOfSatisfying(BusinessException.class, ex ->
                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.COURSE_END_POINT_OFF_ROAD)
        );
    }

    @Test
    void generateOnly_rethrowsUnrelatedDataAccessException() {
        UncategorizedSQLException unrelated = new UncategorizedSQLException(
                "task", "sql", new SQLException("undefined table", "42P01")
        );
        doThrow(unrelated)
                .when(jdbc).queryForList(anyString(), any(Object[].class));

        assertThatThrownBy(
                () -> repo.generateOnly(126.844, 37.55, 3000, at, 0, "walker")
        ).isSameAs(unrelated);
    }

    @Test
    void generateOnly_returnsEmptyWhenNoRows() {
        doReturn(Collections.emptyList())
                .when(jdbc).queryForList(anyString(), any(Object[].class));

        assertThat(repo.generateOnly(126.844, 37.55, 3000, at, 0, "walker")).isEmpty();
    }

    private UncategorizedSQLException offRoadException(String sqlState, String message) {
        return new UncategorizedSQLException(
                "routing.generate_*",
                "SELECT ...",
                new SQLException(message, sqlState)
        );
    }
}
