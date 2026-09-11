package com.back.walk.service;

import com.back.bookmark.domain.CourseUsageLog;
import com.back.bookmark.repository.BookmarkRepository;
import com.back.bookmark.repository.CourseUsageLogRepository;
import com.back.course.domain.Course;
import com.back.course.repository.CourseRepository;
import com.back.global.api.PageResponse;
import com.back.global.exception.BusinessException;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import com.back.walk.dto.WalkCompletionRequest;
import com.back.walk.dto.WalkRecordResponse;
import com.back.walk.repository.WalkHistoryQueryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class WalkServiceTest {

    @Mock UserRepository userRepository;
    @Mock CourseRepository courseRepository;
    @Mock BookmarkRepository bookmarkRepository;
    @Mock CourseUsageLogRepository usageLogRepository;
    @Mock WalkHistoryQueryRepository historyRepository;
    @InjectMocks WalkService service;

    @Test
    void completesWalkWithoutRequiringBookmark() {
        User user = User.createLocal("walker@example.com", "hash", "산책러");
        Course course = new Course("서울숲 코스", "11680", 2_500);
        WalkCompletionRequest request = new WalkCompletionRequest(
                OffsetDateTime.parse("2026-09-11T09:00:00+09:00"),
                OffsetDateTime.parse("2026-09-11T09:35:30+09:00")
        );
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(courseRepository.findById(44L)).willReturn(Optional.of(course));
        given(bookmarkRepository.findByUserIdAndCourseId(1L, 44L)).willReturn(Optional.empty());
        given(usageLogRepository.save(any(CourseUsageLog.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        WalkRecordResponse response = service.complete(1L, 44L, request);

        assertThat(response.courseName()).isEqualTo("서울숲 코스");
        assertThat(response.distanceMeters()).isEqualTo(2_500);
        assertThat(response.durationSeconds()).isEqualTo(2_130);
        assertThat(response.walkedAt()).isEqualTo("2026-09-11T09:35:30");
        assertThat(response.rating()).isNull();

        ArgumentCaptor<CourseUsageLog> captor = ArgumentCaptor.forClass(CourseUsageLog.class);
        verify(usageLogRepository).save(captor.capture());
        assertThat(captor.getValue().getDurationSeconds()).isEqualTo(2_130);
    }

    @Test
    void rejectsCompletionBeforeStart() {
        WalkCompletionRequest request = new WalkCompletionRequest(
                OffsetDateTime.parse("2026-09-11T10:00:00+09:00"),
                OffsetDateTime.parse("2026-09-11T09:00:00+09:00")
        );

        assertThatThrownBy(() -> service.complete(1L, 44L, request))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(userRepository, courseRepository, usageLogRepository);
    }

    @Test
    void delegatesPagedHistoryQuery() {
        PageResponse<WalkRecordResponse> expected = new PageResponse<>(List.of(), 0, 5, 0);
        given(historyRepository.findByUserId(1L, 0, 5)).willReturn(expected);

        assertThat(service.getMyWalks(1L, 0, 5)).isSameAs(expected);
    }
}
