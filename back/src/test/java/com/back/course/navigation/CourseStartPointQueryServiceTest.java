package com.back.course.navigation;

import com.back.course.navigation.repository.CourseNavigationRepository;
import com.back.course.navigation.repository.CourseStartPointView;
import com.back.course.navigation.service.CourseStartPointQueryService;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class CourseStartPointQueryServiceTest {

    private final CourseNavigationRepository repository = mock(CourseNavigationRepository.class);
    private final CourseStartPointQueryService service = new CourseStartPointQueryService(repository);

    @Test
    void mapsProjectionToStartPointValue() {
        CourseStartPointView view = mock(CourseStartPointView.class);
        given(view.getCourseId()).willReturn(15L);
        given(view.getName()).willReturn("서울식물원 코스");
        given(view.getStartLat()).willReturn(37.5690);
        given(view.getStartLng()).willReturn(126.8350);
        given(repository.findStartPointByCourseId(15L)).willReturn(Optional.of(view));

        var result = service.getStartPoint(15L);

        assertThat(result.courseId()).isEqualTo(15L);
        assertThat(result.name()).isEqualTo("서울식물원 코스");
        assertThat(result.latitude()).isEqualTo(37.5690);
        assertThat(result.longitude()).isEqualTo(126.8350);
    }

    @Test
    void missingCourseReturnsCourseNotFound() {
        given(repository.findStartPointByCourseId(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStartPoint(99L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COURSE_NOT_FOUND));
    }
}
