package com.back.hazard.service;

import com.back.course.domain.Course;
import com.back.course.repository.CourseRepository;
import com.back.global.error.ApiException;
import com.back.hazard.domain.Hazard;
import com.back.hazard.domain.HazardConfirmation;
import com.back.hazard.domain.HazardReport;
import com.back.hazard.domain.HazardStatus;
import com.back.hazard.dto.HazardCreateRequest;
import com.back.hazard.dto.HazardReportCreateRequest;
import com.back.hazard.repository.HazardConfirmationRepository;
import com.back.hazard.repository.HazardReportRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HazardServiceTest {

    @Mock
    private HazardRepository hazardRepository;
    @Mock
    private HazardReportRepository hazardReportRepository;
    @Mock
    private HazardConfirmationRepository hazardConfirmationRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private HazardMatchingService hazardMatchingService;
    @InjectMocks
    private HazardService hazardService;

    @Test
    @DisplayName("최초 신고는 PENDING Hazard와 GPS가 담긴 HazardReport를 생성한다")
    void createsPendingHazardAndFirstReport() {
        Course course = new Course("테스트 코스", "11500", 3000);
        User reporter = user(1L, "first@test.com");
        HazardCreateRequest request = new HazardCreateRequest(
                "빙판", "상", "그늘진 구간 결빙 주의", 37.5219, 126.8575);
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(reporter));
        given(courseRepository.findById(10L)).willReturn(Optional.of(course));
        given(hazardMatchingService.findMatchingHazard(
                10L, "빙판", 37.5219, 126.8575)).willReturn(Optional.empty());
        given(hazardRepository.save(any(Hazard.class))).willAnswer(invocation -> {
            Hazard hazard = invocation.getArgument(0);
            ReflectionTestUtils.setField(hazard, "id", 30L);
            return hazard;
        });
        given(hazardReportRepository.countDistinctReportersByHazardId(30L)).willReturn(1L);

        var response = hazardService.create(1L, 10L, request);

        assertThat(response.hazardId()).isEqualTo(30L);
        ArgumentCaptor<Hazard> hazardCaptor = ArgumentCaptor.forClass(Hazard.class);
        verify(hazardRepository).save(hazardCaptor.capture());
        Hazard savedHazard = hazardCaptor.getValue();
        assertThat(savedHazard.getCourse()).isSameAs(course);
        assertThat(savedHazard.getHazardType()).isEqualTo("빙판");
        assertThat(savedHazard.getStatus()).isEqualTo(HazardStatus.PENDING);

        ArgumentCaptor<HazardReport> reportCaptor = ArgumentCaptor.forClass(HazardReport.class);
        verify(hazardReportRepository).saveAndFlush(reportCaptor.capture());
        HazardReport report = reportCaptor.getValue();
        assertThat(report.getHazard()).isSameAs(savedHazard);
        assertThat(report.getReporter()).isSameAs(reporter);
        assertThat(report.getSeverity()).isEqualTo("상");
        assertThat(report.getContent()).isEqualTo("그늘진 구간 결빙 주의");
        assertThat(report.getLatitude()).isEqualTo(37.5219);
        assertThat(report.getLongitude()).isEqualTo(126.8575);
    }

    @Test
    @DisplayName("자동 매칭된 기존 Hazard에는 새 Hazard 대신 Report를 추가한다")
    void addsReportToAutomaticallyMatchedHazard() {
        Course course = new Course("테스트 코스", "11500", 3000);
        User reporter = user(2L, "second@test.com");
        Hazard matched = hazard(30L);
        HazardCreateRequest request = new HazardCreateRequest(
                "빙판", "상", "추가 결빙 신고", 37.5219, 126.8575);
        given(userRepository.findByIdAndDeletedAtIsNull(2L)).willReturn(Optional.of(reporter));
        given(courseRepository.findById(10L)).willReturn(Optional.of(course));
        given(hazardMatchingService.findMatchingHazard(
                10L, "빙판", 37.5219, 126.8575)).willReturn(Optional.of(matched));
        given(hazardReportRepository.existsByHazard_IdAndReporter_Id(30L, 2L)).willReturn(false);
        given(hazardReportRepository.countDistinctReportersByHazardId(30L)).willReturn(2L);

        var response = hazardService.create(2L, 10L, request);

        assertThat(response.hazardId()).isEqualTo(30L);
        verify(hazardRepository, never()).save(any(Hazard.class));
        ArgumentCaptor<HazardReport> captor = ArgumentCaptor.forClass(HazardReport.class);
        verify(hazardReportRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getHazard()).isSameAs(matched);
        assertThat(captor.getValue().getReporter()).isSameAs(reporter);
    }

    @Test
    @DisplayName("두 명이 신고한 Hazard에 세 번째 사용자가 자동 매칭되면 ACTIVE가 된다")
    void activatesAutomaticallyMatchedHazardWithThirdReporter() {
        Course course = new Course("테스트 코스", "11500", 3000);
        User reporter = user(3L, "third@test.com");
        Hazard matched = hazard(30L);
        HazardCreateRequest request = new HazardCreateRequest(
                "빙판", "상", "세 번째 신고", 37.5219, 126.8575);
        given(userRepository.findByIdAndDeletedAtIsNull(3L)).willReturn(Optional.of(reporter));
        given(courseRepository.findById(10L)).willReturn(Optional.of(course));
        given(hazardMatchingService.findMatchingHazard(
                10L, "빙판", 37.5219, 126.8575)).willReturn(Optional.of(matched));
        given(hazardReportRepository.existsByHazard_IdAndReporter_Id(30L, 3L)).willReturn(false);
        given(hazardReportRepository.countDistinctReportersByHazardId(30L)).willReturn(3L);

        hazardService.create(3L, 10L, request);

        assertThat(matched.getStatus()).isEqualTo(HazardStatus.ACTIVE);
        assertThat(matched.getActivatedAt()).isNotNull();
    }

    @Test
    @DisplayName("자동 매칭된 Hazard에 이미 신고한 사용자는 새 Hazard로 우회하지 않고 409이다")
    void rejectsDuplicateReporterOnAutomaticallyMatchedHazard() {
        Course course = new Course("테스트 코스", "11500", 3000);
        User reporter = user(1L, "first@test.com");
        Hazard matched = hazard(30L);
        HazardCreateRequest request = new HazardCreateRequest(
                "빙판", "상", "중복 신고", 37.5219, 126.8575);
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(reporter));
        given(courseRepository.findById(10L)).willReturn(Optional.of(course));
        given(hazardMatchingService.findMatchingHazard(
                10L, "빙판", 37.5219, 126.8575)).willReturn(Optional.of(matched));
        given(hazardReportRepository.existsByHazard_IdAndReporter_Id(30L, 1L)).willReturn(true);

        ApiException exception = catchThrowableOfType(
                () -> hazardService.create(1L, 10L, request), ApiException.class);

        assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
        verify(hazardRepository, never()).save(any(Hazard.class));
        verify(hazardReportRepository, never()).saveAndFlush(any(HazardReport.class));
    }

    @Test
    @DisplayName("자동 매칭된 ACTIVE Hazard에도 추가 Report를 저장한다")
    void addsReportToAutomaticallyMatchedActiveHazard() {
        Course course = new Course("테스트 코스", "11500", 3000);
        User reporter = user(4L, "fourth@test.com");
        Hazard matched = hazard(30L);
        matched.updateStatusByReporterCount(3, 3);
        HazardCreateRequest request = new HazardCreateRequest(
                "공사", "중", "공사 계속 중", 37.5219, 126.8575);
        given(userRepository.findByIdAndDeletedAtIsNull(4L)).willReturn(Optional.of(reporter));
        given(courseRepository.findById(10L)).willReturn(Optional.of(course));
        given(hazardMatchingService.findMatchingHazard(
                10L, "공사", 37.5219, 126.8575)).willReturn(Optional.of(matched));
        given(hazardReportRepository.existsByHazard_IdAndReporter_Id(30L, 4L)).willReturn(false);
        given(hazardReportRepository.countDistinctReportersByHazardId(30L)).willReturn(4L);

        hazardService.create(4L, 10L, request);

        assertThat(matched.getStatus()).isEqualTo(HazardStatus.ACTIVE);
        verify(hazardReportRepository).saveAndFlush(any(HazardReport.class));
        verify(hazardRepository, never()).save(any(Hazard.class));
    }

    @Test
    @DisplayName("서로 다른 세 명의 신고가 같은 Hazard에 연결되면 ACTIVE가 된다")
    void activatesAfterThreeDistinctReporters() {
        Hazard hazard = hazard(30L);
        User first = user(1L, "first@test.com");
        User second = user(2L, "second@test.com");
        User third = user(3L, "third@test.com");
        given(hazardRepository.findById(30L)).willReturn(Optional.of(hazard));
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(first));
        given(userRepository.findByIdAndDeletedAtIsNull(2L)).willReturn(Optional.of(second));
        given(userRepository.findByIdAndDeletedAtIsNull(3L)).willReturn(Optional.of(third));
        given(hazardReportRepository.existsByHazard_IdAndReporter_Id(any(), any())).willReturn(false);
        given(hazardReportRepository.countDistinctReportersByHazardId(30L)).willReturn(1L, 2L, 3L);
        HazardReportCreateRequest report = new HazardReportCreateRequest(
                "상", "결빙 주의", 37.5219, 126.8575);

        hazardService.addReport(1L, 30L, report);
        assertThat(hazard.getStatus()).isEqualTo(HazardStatus.PENDING);
        hazardService.addReport(2L, 30L, report);
        assertThat(hazard.getStatus()).isEqualTo(HazardStatus.PENDING);
        hazardService.addReport(3L, 30L, report);

        assertThat(hazard.getStatus()).isEqualTo(HazardStatus.ACTIVE);
        assertThat(hazard.getActivatedAt()).isNotNull();
        verify(hazardReportRepository, times(3)).saveAndFlush(any(HazardReport.class));
    }

    @Test
    @DisplayName("같은 사용자의 중복 신고는 409이고 신고자 수를 늘리지 않는다")
    void rejectsDuplicateReporter() {
        Hazard hazard = hazard(30L);
        User reporter = user(1L, "first@test.com");
        given(hazardRepository.findById(30L)).willReturn(Optional.of(hazard));
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(reporter));
        given(hazardReportRepository.existsByHazard_IdAndReporter_Id(30L, 1L)).willReturn(true);

        ApiException exception = catchThrowableOfType(
                () -> hazardService.addReport(1L, 30L, new HazardReportCreateRequest(
                        "상", "중복 신고", 37.5219, 126.8575)),
                ApiException.class
        );

        assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
        verify(hazardReportRepository, never()).saveAndFlush(any(HazardReport.class));
        verify(hazardReportRepository, never()).countDistinctReportersByHazardId(any());
    }

    @Test
    @DisplayName("ACTIVE 이후 추가 신고도 저장되고 상태는 유지된다")
    void acceptsAdditionalReportAfterActivation() {
        Hazard hazard = hazard(30L);
        hazard.updateStatusByReporterCount(3, 3);
        User fourth = user(4L, "fourth@test.com");
        given(hazardRepository.findById(30L)).willReturn(Optional.of(hazard));
        given(userRepository.findByIdAndDeletedAtIsNull(4L)).willReturn(Optional.of(fourth));
        given(hazardReportRepository.existsByHazard_IdAndReporter_Id(30L, 4L)).willReturn(false);
        given(hazardReportRepository.countDistinctReportersByHazardId(30L)).willReturn(4L);

        hazardService.addReport(4L, 30L, new HazardReportCreateRequest(
                "중", "여전히 위험함", 37.5220, 126.8576));

        assertThat(hazard.getStatus()).isEqualTo(HazardStatus.ACTIVE);
        verify(hazardReportRepository).saveAndFlush(any(HazardReport.class));
    }

    @Test
    @DisplayName("위험 확인은 사용자별 1건을 저장하고 확인 수를 반환한다")
    void confirmsHazardOnce() {
        Hazard hazard = hazard(30L);
        User user = user(2L, "confirmer@test.com");
        given(hazardRepository.findById(30L)).willReturn(Optional.of(hazard));
        given(userRepository.findByIdAndDeletedAtIsNull(2L)).willReturn(Optional.of(user));
        given(hazardConfirmationRepository.existsByHazard_IdAndUser_Id(30L, 2L)).willReturn(false);
        given(hazardConfirmationRepository.countByHazard_Id(30L)).willReturn(1L);

        var response = hazardService.confirm(2L, 30L);

        assertThat(response.confirmed()).isTrue();
        assertThat(response.confirmationCount()).isEqualTo(1L);
        ArgumentCaptor<HazardConfirmation> captor = ArgumentCaptor.forClass(HazardConfirmation.class);
        verify(hazardConfirmationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getHazard()).isSameAs(hazard);
        assertThat(captor.getValue().getUser()).isSameAs(user);
    }

    @Test
    @DisplayName("같은 사용자의 중복 위험 확인은 409이다")
    void rejectsDuplicateConfirmation() {
        Hazard hazard = hazard(30L);
        User user = user(2L, "confirmer@test.com");
        given(hazardRepository.findById(30L)).willReturn(Optional.of(hazard));
        given(userRepository.findByIdAndDeletedAtIsNull(2L)).willReturn(Optional.of(user));
        given(hazardConfirmationRepository.existsByHazard_IdAndUser_Id(30L, 2L)).willReturn(true);

        ApiException exception = catchThrowableOfType(
                () -> hazardService.confirm(2L, 30L), ApiException.class);

        assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
        verify(hazardConfirmationRepository, never()).saveAndFlush(any(HazardConfirmation.class));
    }

    @Test
    @DisplayName("Confirmation 수는 PENDING 활성화에 포함되지 않는다")
    void confirmationDoesNotActivatePendingHazard() {
        Hazard hazard = hazard(30L);
        User user = user(2L, "confirmer@test.com");
        given(hazardRepository.findById(30L)).willReturn(Optional.of(hazard));
        given(userRepository.findByIdAndDeletedAtIsNull(2L)).willReturn(Optional.of(user));
        given(hazardConfirmationRepository.existsByHazard_IdAndUser_Id(30L, 2L)).willReturn(false);
        given(hazardConfirmationRepository.countByHazard_Id(30L)).willReturn(10L);

        hazardService.confirm(2L, 30L);

        assertThat(hazard.getStatus()).isEqualTo(HazardStatus.PENDING);
        verify(hazardReportRepository, never()).countDistinctReportersByHazardId(any());
    }

    @Test
    @DisplayName("공개 조회는 코스의 ACTIVE Hazard만 최신순으로 조회한다")
    void returnsOnlyActiveHazards() {
        Hazard hazard = hazard(30L);
        hazard.updateStatusByReporterCount(3, 3);
        given(courseRepository.existsById(10L)).willReturn(true);
        given(hazardRepository.findByCourse_IdAndStatusOrderByCreatedAtDesc(10L, HazardStatus.ACTIVE))
                .willReturn(List.of(hazard));
        given(hazardConfirmationRepository.countByHazard_Id(30L)).willReturn(2L);

        var result = hazardService.getActiveHazards(10L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().hazardId()).isEqualTo(30L);
        assertThat(result.getFirst().status()).isEqualTo(HazardStatus.ACTIVE);
        assertThat(result.getFirst().confirmationCount()).isEqualTo(2L);
        verify(hazardRepository).findByCourse_IdAndStatusOrderByCreatedAtDesc(10L, HazardStatus.ACTIVE);
    }

    @Test
    @DisplayName("존재하지 않는 코스는 404이고 Hazard를 조회하지 않는다")
    void rejectsUnknownCourse() {
        given(courseRepository.existsById(999L)).willReturn(false);

        ApiException exception = catchThrowableOfType(
                () -> hazardService.getActiveHazards(999L), ApiException.class);

        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(hazardRepository, never()).findByCourse_IdAndStatusOrderByCreatedAtDesc(any(), any());
    }

    @Test
    @DisplayName("PENDING 위험에서 자신의 신고를 삭제하면 남은 신고 수로 PENDING을 유지한다")
    void deletesOwnReportFromPendingHazard() {
        Hazard hazard = hazard(30L);
        HazardReport report = report(hazard, user(1L, "first@test.com"));
        given(hazardRepository.findById(30L)).willReturn(Optional.of(hazard));
        given(userRepository.findByIdAndDeletedAtIsNull(1L))
                .willReturn(Optional.of(user(1L, "first@test.com")));
        given(hazardReportRepository.findByHazard_IdAndReporter_Id(30L, 1L))
                .willReturn(Optional.of(report));
        given(hazardReportRepository.countDistinctReportersByHazardId(30L)).willReturn(1L);

        hazardService.deleteMyReport(1L, 30L);

        assertThat(hazard.getStatus()).isEqualTo(HazardStatus.PENDING);
        assertThat(hazard.getActivatedAt()).isNull();
        verify(hazardReportRepository).delete(report);
        verify(hazardReportRepository).flush();
        verify(hazardRepository, never()).delete(any(Hazard.class));
    }

    @Test
    @DisplayName("ACTIVE 위험에서 한 명이 신고를 삭제해 두 명이 남으면 PENDING으로 전환한다")
    void revertsToPendingAfterDeletingFromThreeReporterActiveHazard() {
        Hazard hazard = hazard(30L);
        hazard.updateStatusByReporterCount(3, 3);
        HazardReport report = report(hazard, user(3L, "third@test.com"));
        given(hazardRepository.findById(30L)).willReturn(Optional.of(hazard));
        given(userRepository.findByIdAndDeletedAtIsNull(3L))
                .willReturn(Optional.of(user(3L, "third@test.com")));
        given(hazardReportRepository.findByHazard_IdAndReporter_Id(30L, 3L))
                .willReturn(Optional.of(report));
        given(hazardReportRepository.countDistinctReportersByHazardId(30L)).willReturn(2L);

        hazardService.deleteMyReport(3L, 30L);

        assertThat(hazard.getStatus()).isEqualTo(HazardStatus.PENDING);
        assertThat(hazard.getActivatedAt()).isNull();
    }

    @Test
    @DisplayName("네 명 ACTIVE 위험에서 한 명이 삭제해 세 명이 남으면 ACTIVE를 유지한다")
    void keepsActiveWhenThreeReportersRemain() {
        Hazard hazard = hazard(30L);
        hazard.updateStatusByReporterCount(4, 3);
        var activatedAt = hazard.getActivatedAt();
        HazardReport report = report(hazard, user(4L, "fourth@test.com"));
        given(hazardRepository.findById(30L)).willReturn(Optional.of(hazard));
        given(userRepository.findByIdAndDeletedAtIsNull(4L))
                .willReturn(Optional.of(user(4L, "fourth@test.com")));
        given(hazardReportRepository.findByHazard_IdAndReporter_Id(30L, 4L))
                .willReturn(Optional.of(report));
        given(hazardReportRepository.countDistinctReportersByHazardId(30L)).willReturn(3L);

        hazardService.deleteMyReport(4L, 30L);

        assertThat(hazard.getStatus()).isEqualTo(HazardStatus.ACTIVE);
        assertThat(hazard.getActivatedAt()).isEqualTo(activatedAt);
        verify(hazardRepository, never()).delete(any(Hazard.class));
    }

    @Test
    @DisplayName("마지막 신고를 삭제하면 Confirmation을 정리한 뒤 Hazard를 삭제한다")
    void deletesHazardAfterLastReport() {
        Hazard hazard = hazard(30L);
        HazardReport report = report(hazard, user(1L, "first@test.com"));
        given(hazardRepository.findById(30L)).willReturn(Optional.of(hazard));
        given(userRepository.findByIdAndDeletedAtIsNull(1L))
                .willReturn(Optional.of(user(1L, "first@test.com")));
        given(hazardReportRepository.findByHazard_IdAndReporter_Id(30L, 1L))
                .willReturn(Optional.of(report));
        given(hazardReportRepository.countDistinctReportersByHazardId(30L)).willReturn(0L);

        hazardService.deleteMyReport(1L, 30L);

        var order = inOrder(hazardReportRepository, hazardConfirmationRepository, hazardRepository);
        order.verify(hazardReportRepository).delete(report);
        order.verify(hazardReportRepository).flush();
        order.verify(hazardConfirmationRepository).deleteByHazard_Id(30L);
        order.verify(hazardConfirmationRepository).flush();
        order.verify(hazardRepository).delete(hazard);
    }

    @Test
    @DisplayName("자신의 신고가 없으면 404이고 다른 사용자의 신고를 삭제하지 않는다")
    void rejectsDeletionWhenOwnReportDoesNotExist() {
        Hazard hazard = hazard(30L);
        given(hazardRepository.findById(30L)).willReturn(Optional.of(hazard));
        given(userRepository.findByIdAndDeletedAtIsNull(2L))
                .willReturn(Optional.of(user(2L, "second@test.com")));
        given(hazardReportRepository.findByHazard_IdAndReporter_Id(30L, 2L))
                .willReturn(Optional.empty());

        ApiException exception = catchThrowableOfType(
                () -> hazardService.deleteMyReport(2L, 30L), ApiException.class);

        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("해당 위험에 등록한 신고가 없습니다.");
        verify(hazardReportRepository, never()).delete(any(HazardReport.class));
        verify(hazardRepository, never()).delete(any(Hazard.class));
    }

    @Test
    @DisplayName("탈퇴했거나 존재하지 않는 사용자는 자신의 위험 신고를 삭제할 수 없다")
    void rejectsReportDeletionByInactiveUser() {
        Hazard hazard = hazard(30L);
        given(hazardRepository.findById(30L)).willReturn(Optional.of(hazard));
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());

        ApiException exception = catchThrowableOfType(
                () -> hazardService.deleteMyReport(1L, 30L), ApiException.class);

        assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exception.getMessage()).isEqualTo("존재하지 않는 사용자입니다.");
        verify(hazardReportRepository, never()).findByHazard_IdAndReporter_Id(any(), any());
        verify(hazardReportRepository, never()).delete(any(HazardReport.class));
    }

    private static Hazard hazard(Long id) {
        Hazard hazard = new Hazard(new Course("테스트 코스", "11500", 3000), "빙판");
        ReflectionTestUtils.setField(hazard, "id", id);
        return hazard;
    }

    private static User user(Long id, String email) {
        User user = User.createLocal(email, "hash", "사용자" + id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static HazardReport report(Hazard hazard, User reporter) {
        return new HazardReport(hazard, reporter, "상", "결빙 주의", 37.5219, 126.8575);
    }
}
