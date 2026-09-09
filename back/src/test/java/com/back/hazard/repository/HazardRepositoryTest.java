package com.back.hazard.repository;

import com.back.course.domain.Course;
import com.back.course.repository.CourseRepository;
import com.back.global.config.JpaConfig;
import com.back.hazard.domain.Hazard;
import com.back.hazard.domain.HazardConfirmation;
import com.back.hazard.domain.HazardReport;
import com.back.hazard.domain.HazardStatus;
import com.back.hazard.dto.HazardResponse;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.datasource.url="
        + "jdbc:h2:mem:hazard;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
        + "INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@ActiveProfiles("test")
class HazardRepositoryTest {

    @Autowired
    private HazardRepository hazardRepository;
    @Autowired
    private HazardReportRepository hazardReportRepository;
    @Autowired
    private HazardConfirmationRepository hazardConfirmationRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("HazardReport는 Hazard, reporter, 원본 GPS를 함께 저장한다")
    void savesHazardReportRelationship() {
        Course course = courseRepository.save(new Course("테스트 코스", "11500", 3000));
        User reporter = userRepository.save(User.createLocal("reporter@test.com", "hash", "신고자"));
        Hazard hazard = hazardRepository.save(new Hazard(course, "빙판"));

        HazardReport saved = hazardReportRepository.saveAndFlush(new HazardReport(
                hazard, reporter, "상", "결빙 주의", 37.5219, 126.8575));

        HazardReport found = hazardReportRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getHazard().getId()).isEqualTo(hazard.getId());
        assertThat(found.getReporter().getId()).isEqualTo(reporter.getId());
        assertThat(found.getLatitude()).isEqualTo(37.5219);
        assertThat(found.getLongitude()).isEqualTo(126.8575);
        assertThat(hazardReportRepository.countDistinctReportersByHazardId(hazard.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 Hazard에 동일 reporter 신고를 두 번 저장할 수 없다")
    void enforcesUniqueReporterPerHazard() {
        Course course = courseRepository.save(new Course("테스트 코스", "11500", 3000));
        User reporter = userRepository.save(User.createLocal("reporter2@test.com", "hash", "신고자"));
        Hazard hazard = hazardRepository.save(new Hazard(course, "빙판"));
        hazardReportRepository.saveAndFlush(new HazardReport(
                hazard, reporter, "상", "첫 신고", 37.5219, 126.8575));

        assertThatThrownBy(() -> hazardReportRepository.saveAndFlush(new HazardReport(
                hazard, reporter, "중", "중복 신고", 37.5220, 126.8576)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 Hazard에 동일 사용자의 Confirmation을 두 번 저장할 수 없다")
    void enforcesUniqueConfirmationPerUser() {
        Course course = courseRepository.save(new Course("테스트 코스", "11500", 3000));
        User user = userRepository.save(User.createLocal("confirm@test.com", "hash", "확인자"));
        Hazard hazard = hazardRepository.save(new Hazard(course, "빙판"));
        hazardConfirmationRepository.saveAndFlush(new HazardConfirmation(hazard, user));

        assertThatThrownBy(() -> hazardConfirmationRepository.saveAndFlush(
                new HazardConfirmation(hazard, user)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("공개 조회 Repository는 ACTIVE Hazard만 최신순으로 반환한다")
    void findsOnlyActiveHazards() {
        Course course = courseRepository.save(new Course("테스트 코스", "11500", 3000));
        Hazard pending = hazardRepository.save(new Hazard(course, "PENDING_TYPE"));
        Hazard active = new Hazard(course, "ACTIVE_TYPE");
        active.updateStatusByReporterCount(3, 3);
        hazardRepository.saveAndFlush(active);

        var result = hazardRepository.findByCourse_IdAndStatusOrderByCreatedAtDesc(
                course.getId(), HazardStatus.ACTIVE);

        assertThat(result).extracting(Hazard::getHazardType).containsExactly("ACTIVE_TYPE");
        assertThat(result).doesNotContain(pending);
    }

    @Test
    @DisplayName("ACTIVE Hazard 조회는 createdAt과 id가 가장 앞선 최초 신고 좌표를 반환한다")
    void findsActiveHazardWithFirstReportCoordinates() {
        Course course = courseRepository.save(new Course("테스트 코스", "11500", 3000));
        User firstReporter = userRepository.save(User.createLocal("first-location@test.com", "hash", "첫째"));
        User secondReporter = userRepository.save(User.createLocal("second-location@test.com", "hash", "둘째"));
        User thirdReporter = userRepository.save(User.createLocal("third-location@test.com", "hash", "셋째"));
        User pendingReporter = userRepository.save(User.createLocal("pending-location@test.com", "hash", "대기"));

        Hazard active = hazardRepository.save(new Hazard(course, "빙판"));
        LocalDateTime sameCreatedAt = LocalDateTime.of(2026, 9, 8, 10, 0);
        HazardReport firstReport = new HazardReport(
                active, firstReporter, "상", "최초 신고", 37.5219, 126.8575);
        ReflectionTestUtils.setField(firstReport, "createdAt", sameCreatedAt);
        hazardReportRepository.saveAndFlush(firstReport);
        HazardReport secondReport = new HazardReport(
                active, secondReporter, "중", "동일 시각 후속 신고", 37.5225, 126.8581);
        ReflectionTestUtils.setField(secondReport, "createdAt", sameCreatedAt);
        hazardReportRepository.saveAndFlush(secondReport);
        hazardReportRepository.saveAndFlush(new HazardReport(
                active, thirdReporter, "하", "나중 신고", 37.5230, 126.8586));
        active.updateStatusByReporterCount(3, 3);
        hazardConfirmationRepository.saveAndFlush(new HazardConfirmation(active, firstReporter));

        Hazard pending = hazardRepository.save(new Hazard(course, "침수"));
        hazardReportRepository.saveAndFlush(new HazardReport(
                pending, pendingReporter, "상", "대기 신고", 37.5300, 126.8600));

        var result = hazardReportRepository.findVisibleHazardResponsesByCourseId(
                course.getId(), HazardStatus.ACTIVE, HazardStatus.PENDING, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().hazardId()).isEqualTo(active.getId());
        assertThat(result.getFirst().latitude()).isEqualTo(37.5219);
        assertThat(result.getFirst().longitude()).isEqualTo(126.8575);
        assertThat(result.getFirst().status()).isEqualTo(HazardStatus.ACTIVE);
        assertThat(result.getFirst().reportCount()).isEqualTo(3L);
        assertThat(result.getFirst().confirmationCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("신고가 없는 비정상 ACTIVE Hazard는 공개 좌표 조회에서 제외한다")
    void excludesActiveHazardWithoutReportFromPublicLocationQuery() {
        Course course = courseRepository.save(new Course("테스트 코스", "11500", 3000));
        Hazard activeWithoutReport = new Hazard(course, "빙판");
        activeWithoutReport.updateStatusByReporterCount(3, 3);
        hazardRepository.saveAndFlush(activeWithoutReport);

        var result = hazardReportRepository.findVisibleHazardResponsesByCourseId(
                course.getId(), HazardStatus.ACTIVE, HazardStatus.PENDING, null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("로그인 사용자는 ACTIVE 전체와 자신이 신고한 PENDING만 조회한다")
    void findsActiveAndOnlyOwnPendingHazards() {
        Course course = courseRepository.save(new Course("테스트 코스", "11500", 3000));
        User me = userRepository.save(User.createLocal("pending-me@test.com", "hash", "나"));
        User other = userRepository.save(User.createLocal("pending-other@test.com", "hash", "타인"));
        User activeReporter = userRepository.save(User.createLocal("active@test.com", "hash", "활성"));

        Hazard active = hazardRepository.save(new Hazard(course, "공사"));
        hazardReportRepository.saveAndFlush(new HazardReport(
                active, activeReporter, "상", "활성 신고", 37.51, 126.85));
        active.updateStatusByReporterCount(3, 3);

        Hazard ownPending = hazardRepository.save(new Hazard(course, "빙판"));
        hazardReportRepository.saveAndFlush(new HazardReport(
                ownPending, me, "중", "내 대기 신고", 37.52, 126.86));

        Hazard otherPending = hazardRepository.save(new Hazard(course, "침수"));
        hazardReportRepository.saveAndFlush(new HazardReport(
                otherPending, other, "하", "타인 대기 신고", 37.53, 126.87));

        var result = hazardReportRepository.findVisibleHazardResponsesByCourseId(
                course.getId(), HazardStatus.ACTIVE, HazardStatus.PENDING, me.getId());

        assertThat(result).extracting(HazardResponse::hazardId)
                .containsExactlyInAnyOrder(active.getId(), ownPending.getId());
        HazardResponse pending = result.stream()
                .filter(item -> item.hazardId().equals(ownPending.getId()))
                .findFirst().orElseThrow();
        assertThat(pending.status()).isEqualTo(HazardStatus.PENDING);
        assertThat(pending.reportedByMe()).isTrue();
        assertThat(pending.reportCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("비회원은 다른 사용자의 PENDING 없이 ACTIVE만 조회한다")
    void anonymousFindsOnlyActiveHazards() {
        Course course = courseRepository.save(new Course("테스트 코스", "11500", 3000));
        User reporter = userRepository.save(User.createLocal("anonymous-filter@test.com", "hash", "신고자"));
        Hazard pending = hazardRepository.save(new Hazard(course, "빙판"));
        hazardReportRepository.saveAndFlush(new HazardReport(
                pending, reporter, "상", "대기 신고", 37.52, 126.86));

        var result = hazardReportRepository.findVisibleHazardResponsesByCourseId(
                course.getId(), HazardStatus.ACTIVE, HazardStatus.PENDING, null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("마지막 신고와 Confirmation을 정리하면 Hazard를 FK 오류 없이 삭제할 수 있다")
    void deletesHazardAfterRemovingDependentRows() {
        Course course = courseRepository.save(new Course("테스트 코스", "11500", 3000));
        User reporter = userRepository.save(User.createLocal("delete@test.com", "hash", "신고자"));
        User confirmer = userRepository.save(User.createLocal("confirmer2@test.com", "hash", "확인자"));
        Hazard hazard = hazardRepository.save(new Hazard(course, "빙판"));
        HazardReport report = hazardReportRepository.saveAndFlush(new HazardReport(
                hazard, reporter, "상", "결빙 주의", 37.5219, 126.8575));
        hazardConfirmationRepository.saveAndFlush(new HazardConfirmation(hazard, confirmer));

        hazardReportRepository.delete(report);
        hazardReportRepository.flush();
        hazardConfirmationRepository.deleteByHazard_Id(hazard.getId());
        hazardConfirmationRepository.flush();
        hazardRepository.delete(hazard);
        hazardRepository.flush();

        assertThat(hazardRepository.findById(hazard.getId())).isEmpty();
        assertThat(hazardReportRepository.findByHazard_IdAndReporter_Id(
                hazard.getId(), reporter.getId())).isEmpty();
        assertThat(hazardConfirmationRepository.countByHazard_Id(hazard.getId())).isZero();
    }

    @Test
    @DisplayName("자동 매칭 후보는 course, hazardType, status로 DB에서 먼저 제한한다")
    void findsMatchingCandidatesByCourseTypeAndStatus() {
        Course targetCourse = courseRepository.save(new Course("대상 코스", "11500", 3000));
        Course otherCourse = courseRepository.save(new Course("다른 코스", "11500", 3000));
        User first = userRepository.save(User.createLocal("candidate1@test.com", "hash", "신고자1"));
        User second = userRepository.save(User.createLocal("candidate2@test.com", "hash", "신고자2"));
        User third = userRepository.save(User.createLocal("candidate3@test.com", "hash", "신고자3"));
        User fourth = userRepository.save(User.createLocal("candidate4@test.com", "hash", "신고자4"));

        Hazard pending = hazardRepository.save(new Hazard(targetCourse, "빙판"));
        Hazard active = new Hazard(targetCourse, "빙판");
        active.updateStatusByReporterCount(3, 3);
        hazardRepository.save(active);
        Hazard otherType = hazardRepository.save(new Hazard(targetCourse, "침수"));
        Hazard otherCourseHazard = hazardRepository.save(new Hazard(otherCourse, "빙판"));

        hazardReportRepository.save(new HazardReport(
                pending, first, "상", "후보1", 37.5, 126.8));
        hazardReportRepository.save(new HazardReport(
                active, second, "상", "후보2", 37.5, 126.8));
        hazardReportRepository.save(new HazardReport(
                otherType, third, "상", "제외1", 37.5, 126.8));
        hazardReportRepository.saveAndFlush(new HazardReport(
                otherCourseHazard, fourth, "상", "제외2", 37.5, 126.8));

        var candidates = hazardReportRepository.findMatchingCandidates(
                targetCourse.getId(),
                "빙판",
                List.of(HazardStatus.PENDING, HazardStatus.ACTIVE)
        );

        assertThat(candidates)
                .extracting(report -> report.getHazard().getId())
                .containsExactlyInAnyOrder(pending.getId(), active.getId());
    }
}
