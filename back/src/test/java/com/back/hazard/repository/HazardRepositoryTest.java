package com.back.hazard.repository;

import com.back.course.domain.Course;
import com.back.course.repository.CourseRepository;
import com.back.global.config.JpaConfig;
import com.back.hazard.domain.Hazard;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

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
    private CourseRepository courseRepository;
    @Autowired
    private UserRepository userRepository;
    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("새 위험 신고의 필드와 초기값을 저장한다")
    void savesHazardWithInitialValues() {
        Course course = courseRepository.save(new Course("테스트 코스", "11500", 3000));
        User reporter = userRepository.save(User.createLocal("reporter@test.com", "hash", "신고자"));
        LocalDateTime expiresAt = LocalDateTime.of(2027, 3, 31, 0, 0);

        Hazard saved = hazardRepository.saveAndFlush(
                new Hazard(course, reporter, "빙판", "상", "그늘진 구간 결빙 주의", expiresAt));
        Long hazardId = saved.getId();
        entityManager.clear();
        Hazard found = hazardRepository.findById(hazardId).orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getCourse().getId()).isEqualTo(course.getId());
        assertThat(found.getReporter().getId()).isEqualTo(reporter.getId());
        assertThat(found.getHazardType()).isEqualTo("빙판");
        assertThat(found.getSeverity()).isEqualTo("상");
        assertThat(found.getContent()).isEqualTo("그늘진 구간 결빙 주의");
        assertThat(found.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(found.getUpvoteCount()).isZero();
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("특정 코스의 만료되지 않은 신고만 만료 시각 오름차순으로 조회한다")
    void findsOnlyActiveHazardsInExpirationOrder() {
        Course course = courseRepository.save(new Course("테스트 코스", "11500", 3000));
        Course otherCourse = courseRepository.save(new Course("다른 코스", "11470", 2000));
        User reporter = userRepository.save(User.createLocal("reporter@test.com", "hash", "신고자"));
        LocalDateTime now = LocalDateTime.of(2026, 9, 1, 12, 0);

        hazardRepository.save(new Hazard(
                course, reporter, "EXPIRED", "LOW", "이미 만료된 신고", now.minusMinutes(1)));
        hazardRepository.save(new Hazard(
                course, reporter, "EXPIRING_NOW", "LOW", "현재 시각에 만료되는 신고", now));
        hazardRepository.save(new Hazard(
                course, reporter, "LATER", "MEDIUM", "나중에 만료되는 신고", now.plusHours(2)));
        hazardRepository.save(new Hazard(
                course, reporter, "SOON", "HIGH", "곧 만료되는 신고", now.plusHours(1)));
        hazardRepository.save(new Hazard(
                otherCourse, reporter, "OTHER", "HIGH", "다른 코스 신고", now.plusMinutes(30)));

        var result = hazardRepository
                .findByCourse_IdAndExpiresAtAfterOrderByExpiresAtAsc(course.getId(), now);

        assertThat(result)
                .extracting(Hazard::getHazardType)
                .containsExactly("SOON", "LATER");
    }
}
