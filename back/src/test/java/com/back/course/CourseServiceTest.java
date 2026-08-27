package com.back.course;

import com.back.bookmark.repository.BookmarkRepository;
import com.back.course.repository.CourseDetailView;
import com.back.course.repository.CourseRepository;
import com.back.course.service.CourseService;
import com.back.global.error.ApiException;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class CourseServiceTest {

    private final CourseRepository courses = mock(CourseRepository.class);
    private final BookmarkRepository bookmarks = mock(BookmarkRepository.class);
    private final CourseService service = new CourseService(courses, bookmarks, new ObjectMapper());

    @Test
    @DisplayName("DB의 GeoJSON 경로 문자열을 코스 상세 응답 객체로 변환한다")
    void convertsDatabaseGeoJsonToResponseObject() {
        CourseDetailView view = mock(CourseDetailView.class);
        given(view.getCourseId()).willReturn(101L);
        given(view.getName()).willReturn("성수 서울숲 순환");
        given(view.getPathGeoJson()).willReturn("""
                {"type":"LineString","coordinates":[[127.037,37.544],[127.038,37.545]]}
                """);
        given(view.getDistanceM()).willReturn(2500);
        given(view.getEstimatedMinutes()).willReturn(35);
        given(view.getIsLoop()).willReturn(true);
        given(view.getFlatness()).willReturn(0.82);
        given(courses.findDetailById(101L, true)).willReturn(Optional.of(view));
        given(bookmarks.existsByUserIdAndCourseId(1L, 101L)).willReturn(true);

        var result = service.getDetail(101L, 1L, LocalDateTime.of(2026, 7, 1, 12, 0));

        assertThat(result.path().type()).isEqualTo("LineString");
        assertThat(result.path().coordinates()).containsExactly(
                java.util.List.of(127.037, 37.544),
                java.util.List.of(127.038, 37.545)
        );
        assertThat(result.isBookmarked()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 코스 상세 조회 시 예외를 발생시킨다")
    void throwsNotFoundForUnknownCourse() {
        given(courses.findDetailById(999L, false)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(
                999L, null, LocalDateTime.of(2026, 1, 1, 12, 0)))
                .isInstanceOf(ApiException.class)
                .hasMessage("존재하지 않는 코스입니다.");
    }
}
