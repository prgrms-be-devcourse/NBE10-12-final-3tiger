package com.back.bookmark;

import com.back.bookmark.domain.Bookmark;
import com.back.bookmark.repository.BookmarkRepository;
import com.back.bookmark.repository.CourseUsageLogRepository;
import com.back.bookmark.service.BookmarkService;
import com.back.course.domain.Course;
import com.back.course.repository.CourseRepository;
import com.back.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BookmarkServiceTest {

    private final BookmarkRepository bookmarks = mock(BookmarkRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final CourseUsageLogRepository usageLogs = mock(CourseUsageLogRepository.class);
    private final BookmarkService service = new BookmarkService(bookmarks, users, courses, usageLogs);

    @Test
    void ratingResponseContainsCourseMapImageUrl() {
        Course course = mock(Course.class);
        Bookmark bookmark = mock(Bookmark.class);
        LocalDateTime bookmarkedAt = LocalDateTime.of(2026, 9, 8, 12, 0);
        given(bookmarks.findByUserIdAndCourseId(1L, 44L)).willReturn(Optional.of(bookmark));
        given(bookmark.getCourse()).willReturn(course);
        given(bookmark.getCreatedAt()).willReturn(bookmarkedAt);
        given(course.getId()).willReturn(44L);
        given(course.getName()).willReturn("서울식물원 코스");
        given(course.getMapImageUrl()).willReturn("https://cdn.example.com/course-maps/44.png");
        given(course.getDistanceM()).willReturn(2150);

        BookmarkService.BookmarkItem result = service.rate(1L, 44L, 5);

        assertThat(result.courseId()).isEqualTo(44L);
        assertThat(result.mapImageUrl()).isEqualTo("https://cdn.example.com/course-maps/44.png");
        verify(bookmark).rate(5);
    }

    @Test
    void bookmarkListContainsCourseMapImageUrl() {
        Course course = mock(Course.class);
        Bookmark bookmark = mock(Bookmark.class);
        given(bookmark.getCourse()).willReturn(course);
        given(course.getId()).willReturn(44L);
        given(course.getName()).willReturn("서울식물원 코스");
        given(course.getMapImageUrl()).willReturn("https://cdn.example.com/course-maps/44.png");
        given(course.getDistanceM()).willReturn(2150);
        given(bookmarks.findByUserId(eq(1L), any()))
                .willReturn(new PageImpl<>(List.of(bookmark)));

        var result = service.mine(1L, 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).mapImageUrl())
                .isEqualTo("https://cdn.example.com/course-maps/44.png");
    }
}
