package com.back.course.navigation;

import com.back.course.navigation.repository.CourseNavigationRepository;
import com.back.course.navigation.repository.CourseNavigationView;
import com.back.course.navigation.service.CourseNavigationService;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
class CourseNavigationServiceTest {

    @Mock CourseNavigationRepository repository;
    private CourseNavigationService service;

    @BeforeEach
    void setUp() {
        service = new CourseNavigationService(repository, new ObjectMapper());
    }

    @Test
    @DisplayName("유효한 코스 경로를 안내 응답으로 변환한다")
    void returnsNavigationResponse() {
        CourseNavigationView view = validView();
        given(repository.findNavigationByCourseId(101L)).willReturn(Optional.of(view));

        var response = service.getNavigation(101L);

        assertThat(response.courseId()).isEqualTo(101L);
        assertThat(response.startPoint().lat()).isEqualTo(37.544);
        assertThat(response.path().type()).isEqualTo("LineString");
        assertThat(response.path().coordinates().getFirst())
                .containsExactly(127.037, 37.544);
    }

    @Test
    @DisplayName("존재하지 않는 코스를 거부한다")
    void rejectsMissingCourse() {
        given(repository.findNavigationByCourseId(999L)).willReturn(Optional.empty());
        assertErrorCode(999L, ErrorCode.COURSE_NOT_FOUND);
    }

    @Test
    @DisplayName("경로가 없는 코스를 거부한다")
    void rejectsCourseWithoutPath() {
        CourseNavigationView view = validView();
        given(view.getPathGeoJson()).willReturn(null);
        given(repository.findNavigationByCourseId(101L)).willReturn(Optional.of(view));
        assertErrorCode(101L, ErrorCode.COURSE_NOT_NAVIGABLE);
    }

    @Test
    @DisplayName("LineString이 아닌 경로를 거부한다")
    void rejectsNonLineStringGeometry() {
        CourseNavigationView view = validView();
        given(view.getGeometryType()).willReturn("POINT");
        given(repository.findNavigationByCourseId(101L)).willReturn(Optional.of(view));
        assertErrorCode(101L, ErrorCode.COURSE_NOT_NAVIGABLE);
    }

    @Test
    @DisplayName("SRID 4326이 아닌 경로를 거부한다")
    void rejectsUnexpectedSrid() {
        CourseNavigationView view = validView();
        given(view.getSrid()).willReturn(3857);
        given(repository.findNavigationByCourseId(101L)).willReturn(Optional.of(view));
        assertErrorCode(101L, ErrorCode.COURSE_NOT_NAVIGABLE);
    }

    @Test
    @DisplayName("파싱할 수 없는 GeoJSON을 서버 데이터 오류로 변환한다")
    void rejectsMalformedGeoJson() {
        CourseNavigationView view = validView();
        given(view.getPathGeoJson()).willReturn("not-json");
        given(repository.findNavigationByCourseId(101L)).willReturn(Optional.of(view));
        assertErrorCode(101L, ErrorCode.COURSE_PATH_DATA_INVALID);
    }

    private void assertErrorCode(Long courseId, ErrorCode expected) {
        assertThatThrownBy(() -> service.getNavigation(courseId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(expected));
    }

    private CourseNavigationView validView() {
        CourseNavigationView view = mock(
                CourseNavigationView.class,
                withSettings().lenient()
        );
        given(view.getCourseId()).willReturn(101L);
        given(view.getName()).willReturn("서울숲 순환 산책로");
        given(view.getDistanceM()).willReturn(2500);
        given(view.getEstimatedMinutes()).willReturn(35);
        given(view.getIsLoop()).willReturn(true);
        given(view.getStartLat()).willReturn(37.544);
        given(view.getStartLng()).willReturn(127.037);
        given(view.getEndLat()).willReturn(37.544);
        given(view.getEndLng()).willReturn(127.037);
        given(view.getPathGeoJson()).willReturn("""
                {"type":"LineString","coordinates":[
                  [127.037,37.544],[127.038,37.545]
                ]}
                """);
        given(view.getCoordinateCount()).willReturn(2);
        given(view.getSrid()).willReturn(4326);
        given(view.getGeometryType()).willReturn("LINESTRING");
        given(view.getPathValid()).willReturn(true);
        given(view.getPathEmpty()).willReturn(false);
        given(view.getCalculatedDistanceM()).willReturn(2500.0);
        given(view.getStartEndDistanceM()).willReturn(0.0);
        return view;
    }
}
