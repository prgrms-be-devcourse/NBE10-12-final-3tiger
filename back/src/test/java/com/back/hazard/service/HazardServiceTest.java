package com.back.hazard.service;

import com.back.course.domain.Course;
import com.back.course.repository.CourseRepository;
import com.back.global.error.ApiException;
import com.back.hazard.domain.Hazard;
import com.back.hazard.dto.HazardCreateRequest;
import com.back.hazard.dto.HazardUpvoteResponse;
import com.back.hazard.repository.HazardRepository;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HazardServiceTest {

    @Mock
    private HazardRepository hazardRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private HazardService hazardService;

    @Test
    @DisplayName("작성자는 자신이 등록한 위험 신고를 삭제할 수 있다")
    void deletesOwnHazard() {
        User reporter = User.createLocal("reporter@test.com", "hash", "신고자");
        ReflectionTestUtils.setField(reporter, "id", 1L);
        Hazard hazard = new Hazard(
                new Course("테스트 코스", "11500", 3000),
                reporter,
                "빙판",
                "상",
                "그늘진 구간 결빙 주의",
                LocalDateTime.of(2027, 3, 31, 0, 0)
        );
        given(hazardRepository.findById(10L)).willReturn(Optional.of(hazard));

        hazardService.delete(1L, 10L);

        verify(hazardRepository).delete(hazard);
    }

    @Test
    @DisplayName("다른 사용자가 등록한 위험 신고는 삭제할 수 없다")
    void rejectsDeletingOtherUsersHazard() {
        User reporter = User.createLocal("other@test.com", "hash", "다른 신고자");
        ReflectionTestUtils.setField(reporter, "id", 2L);
        Hazard hazard = new Hazard(
                new Course("테스트 코스", "11500", 3000),
                reporter,
                "빙판",
                "상",
                "그늘진 구간 결빙 주의",
                LocalDateTime.of(2027, 3, 31, 0, 0)
        );
        given(hazardRepository.findById(10L)).willReturn(Optional.of(hazard));

        ApiException exception = catchThrowableOfType(
                () -> hazardService.delete(1L, 10L),
                ApiException.class
        );

        assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exception.getMessage()).isEqualTo("본인이 등록한 위험 신고만 삭제할 수 있습니다.");
        verify(hazardRepository, never()).delete(any(Hazard.class));
    }

    @Test
    @DisplayName("존재하지 않는 위험 신고는 삭제할 수 없다")
    void rejectsDeletingUnknownHazard() {
        given(hazardRepository.findById(999L)).willReturn(Optional.empty());

        ApiException exception = catchThrowableOfType(
                () -> hazardService.delete(1L, 999L),
                ApiException.class
        );

        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("존재하지 않는 위험 신고입니다.");
        verify(hazardRepository, never()).delete(any(Hazard.class));
    }

    @Test
    @DisplayName("위험 신고 공감 시 공감 수를 1 증가시키고 변경된 상태를 반환한다")
    void upvotesHazard() {
        LocalDateTime expiresAt = LocalDateTime.of(2027, 3, 31, 0, 0);
        Hazard hazard = new Hazard(
                new Course("테스트 코스", "11500", 3000),
                User.createLocal("reporter@test.com", "hash", "신고자"),
                "빙판",
                "상",
                "그늘진 구간 결빙 주의",
                expiresAt
        );
        ReflectionTestUtils.setField(hazard, "upvoteCount", 12);
        LocalDateTime createdAt = hazard.getCreatedAt();
        given(hazardRepository.findById(10L)).willReturn(Optional.of(hazard));

        HazardUpvoteResponse response = hazardService.upvote(10L);

        assertThat(response.upvoted()).isTrue();
        assertThat(response.upvoteCount()).isEqualTo(13);
        assertThat(hazard.getUpvoteCount()).isEqualTo(13);
        assertThat(hazard.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(hazard.getCreatedAt()).isEqualTo(createdAt);
        verify(hazardRepository, never()).save(any(Hazard.class));
    }

    @Test
    @DisplayName("존재하지 않는 위험 신고에 공감하면 404 예외를 발생시킨다")
    void rejectsUpvoteForUnknownHazard() {
        given(hazardRepository.findById(999L)).willReturn(Optional.empty());

        ApiException exception = catchThrowableOfType(
                () -> hazardService.upvote(999L),
                ApiException.class
        );

        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("존재하지 않는 위험 신고입니다.");
        verify(hazardRepository, never()).save(any(Hazard.class));
    }

    @Test
    @DisplayName("코스 위험 신고를 등록하고 생성된 ID를 반환한다")
    void createsHazard() {
        Course course = new Course("테스트 코스", "11500", 3000);
        User reporter = User.createLocal("reporter@test.com", "hash", "신고자");
        ReflectionTestUtils.setField(reporter, "id", 1L);
        LocalDateTime expiresAt = LocalDateTime.of(2027, 3, 31, 0, 0);
        HazardCreateRequest request = new HazardCreateRequest(
                "빙판", "상", "그늘진 구간 결빙 주의", expiresAt);
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(reporter));
        given(courseRepository.findById(1L)).willReturn(Optional.of(course));
        given(hazardRepository.save(any(Hazard.class))).willAnswer(invocation -> {
            Hazard hazard = invocation.getArgument(0);
            ReflectionTestUtils.setField(hazard, "id", 30L);
            return hazard;
        });

        LocalDateTime before = LocalDateTime.now();
        var response = hazardService.create(1L, 1L, request);
        LocalDateTime after = LocalDateTime.now();

        assertThat(response.hazardId()).isEqualTo(30L);
        ArgumentCaptor<Hazard> hazardCaptor = ArgumentCaptor.forClass(Hazard.class);
        verify(hazardRepository).save(hazardCaptor.capture());
        Hazard savedHazard = hazardCaptor.getValue();
        assertThat(savedHazard.getCourse()).isSameAs(course);
        assertThat(savedHazard.getReporter()).isSameAs(reporter);
        assertThat(savedHazard.getHazardType()).isEqualTo("빙판");
        assertThat(savedHazard.getSeverity()).isEqualTo("상");
        assertThat(savedHazard.getContent()).isEqualTo("그늘진 구간 결빙 주의");
        assertThat(savedHazard.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(savedHazard.getUpvoteCount()).isZero();
        assertThat(savedHazard.getCreatedAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("존재하지 않는 코스에는 위험 신고를 등록할 수 없다")
    void rejectsCreateForUnknownCourse() {
        User reporter = User.createLocal("reporter@test.com", "hash", "신고자");
        HazardCreateRequest request = new HazardCreateRequest(
                "빙판", "상", "그늘진 구간 결빙 주의", LocalDateTime.of(2027, 3, 31, 0, 0));
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(reporter));
        given(courseRepository.findById(999L)).willReturn(Optional.empty());

        ApiException exception = catchThrowableOfType(
                () -> hazardService.create(1L, 999L, request),
                ApiException.class
        );

        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("존재하지 않는 코스입니다.");
        verify(hazardRepository, never()).save(any(Hazard.class));
    }

    @Test
    @DisplayName("활성 위험 신고를 만료 시각 오름차순 조회하고 응답으로 변환한다")
    void returnsActiveHazards() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 9, 1, 18, 0);
        Hazard hazard = mock(Hazard.class);
        given(hazard.getId()).willReturn(10L);
        given(hazard.getHazardType()).willReturn("CONSTRUCTION");
        given(hazard.getSeverity()).willReturn("HIGH");
        given(hazard.getContent()).willReturn("보도 공사 중입니다.");
        given(hazard.getUpvoteCount()).willReturn(3);
        given(hazard.getExpiresAt()).willReturn(expiresAt);
        given(courseRepository.existsById(1L)).willReturn(true);
        given(hazardRepository.findByCourse_IdAndExpiresAtAfterOrderByExpiresAtAsc(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .willReturn(List.of(hazard));

        LocalDateTime before = LocalDateTime.now();
        var result = hazardService.getActiveHazards(1L);
        LocalDateTime after = LocalDateTime.now();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().hazardId()).isEqualTo(10L);
        assertThat(result.getFirst().hazardType()).isEqualTo("CONSTRUCTION");
        assertThat(result.getFirst().severity()).isEqualTo("HIGH");
        assertThat(result.getFirst().content()).isEqualTo("보도 공사 중입니다.");
        assertThat(result.getFirst().upvoteCount()).isEqualTo(3);
        assertThat(result.getFirst().expiresAt()).isEqualTo(expiresAt);

        ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(hazardRepository).findByCourse_IdAndExpiresAtAfterOrderByExpiresAtAsc(
                org.mockito.ArgumentMatchers.eq(1L), nowCaptor.capture());
        assertThat(nowCaptor.getValue()).isBetween(before, after);
    }

    @Test
    @DisplayName("활성 위험 신고가 없으면 빈 목록을 반환한다")
    void returnsEmptyListWhenNoActiveHazards() {
        given(courseRepository.existsById(1L)).willReturn(true);
        given(hazardRepository.findByCourse_IdAndExpiresAtAfterOrderByExpiresAtAsc(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .willReturn(List.of());

        var result = hazardService.getActiveHazards(1L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 코스면 404 예외를 발생시키고 위험 신고를 조회하지 않는다")
    void throwsNotFoundForUnknownCourse() {
        given(courseRepository.existsById(999L)).willReturn(false);

        ApiException exception = catchThrowableOfType(
                () -> hazardService.getActiveHazards(999L),
                ApiException.class
        );

        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("존재하지 않는 코스입니다.");
        verify(hazardRepository, never())
                .findByCourse_IdAndExpiresAtAfterOrderByExpiresAtAsc(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(LocalDateTime.class));
    }
}
