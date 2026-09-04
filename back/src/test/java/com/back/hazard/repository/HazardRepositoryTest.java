package com.back.hazard.repository;

import com.back.course.domain.Course;
import com.back.course.repository.CourseRepository;
import com.back.global.config.JpaConfig;
import com.back.hazard.domain.Hazard;
import com.back.hazard.domain.HazardConfirmation;
import com.back.hazard.domain.HazardReport;
import com.back.hazard.domain.HazardStatus;
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
}
