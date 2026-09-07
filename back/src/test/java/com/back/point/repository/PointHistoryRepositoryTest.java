package com.back.point.repository;

import com.back.course.domain.Course;
import com.back.course.repository.CourseRepository;
import com.back.global.config.JpaConfig;
import com.back.hazard.domain.Hazard;
import com.back.hazard.repository.HazardRepository;
import com.back.point.domain.PointHistory;
import com.back.point.domain.PointType;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.datasource.url="
        + "jdbc:h2:mem:point;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
        + "INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@ActiveProfiles("test")
class PointHistoryRepositoryTest {

    @Autowired
    private PointHistoryRepository pointHistoryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private HazardRepository hazardRepository;

    @Test
    @DisplayName("포인트 잔액과 적립 이력을 함께 저장한다")
    void savesPointBalanceAndHistory() {
        User user = userRepository.save(User.createLocal("point@test.com", "hash", "신고자"));
        user.addPoints(10L);
        LocalDateTime rewardedAt = LocalDateTime.of(2026, 9, 7, 12, 0);

        PointHistory saved = pointHistoryRepository.saveAndFlush(new PointHistory(
                user,
                10L,
                PointType.HAZARD_REPORT,
                101L,
                rewardedAt
        ));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getPointBalance())
                .isEqualTo(10L);
        assertThat(pointHistoryRepository.findById(saved.getId()).orElseThrow().getCreatedAt())
                .isEqualTo(rewardedAt);
    }

    @Test
    @DisplayName("같은 사용자, 보상 유형, 참조 ID의 이력은 중복 저장할 수 없다")
    void enforcesUniqueRewardReference() {
        User user = userRepository.save(User.createLocal("unique-point@test.com", "hash", "신고자"));
        LocalDateTime rewardedAt = LocalDateTime.of(2026, 9, 7, 12, 0);
        pointHistoryRepository.saveAndFlush(new PointHistory(
                user, 10L, PointType.HAZARD_REPORT, 101L, rewardedAt));

        assertThatThrownBy(() -> pointHistoryRepository.saveAndFlush(new PointHistory(
                user, 10L, PointType.HAZARD_REPORT, 101L, rewardedAt.plusSeconds(1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Hazard가 삭제되어도 FK가 없는 포인트 이력은 유지된다")
    void keepsPointHistoryAfterHazardDeletion() {
        User user = userRepository.save(User.createLocal("history@test.com", "hash", "신고자"));
        Course course = courseRepository.save(new Course("테스트 코스", "11500", 3000));
        Hazard hazard = hazardRepository.saveAndFlush(new Hazard(course, "빙판"));
        PointHistory history = pointHistoryRepository.saveAndFlush(new PointHistory(
                user,
                100L,
                PointType.HAZARD_ACTIVATED,
                hazard.getId(),
                LocalDateTime.of(2026, 9, 7, 12, 0)
        ));

        hazardRepository.delete(hazard);
        hazardRepository.flush();

        assertThat(pointHistoryRepository.findById(history.getId())).isPresent();
        assertThat(pointHistoryRepository.findById(history.getId()).orElseThrow().getReferenceId())
                .isEqualTo(hazard.getId());
    }
}
