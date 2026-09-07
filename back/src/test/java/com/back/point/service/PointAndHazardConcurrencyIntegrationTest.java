package com.back.point.service;

import com.back.course.domain.Course;
import com.back.course.repository.CourseRepository;
import com.back.hazard.domain.Hazard;
import com.back.hazard.domain.HazardReport;
import com.back.hazard.domain.HazardStatus;
import com.back.hazard.dto.HazardReportCreateRequest;
import com.back.hazard.repository.HazardReportRepository;
import com.back.hazard.repository.HazardRepository;
import com.back.hazard.service.HazardService;
import com.back.point.domain.PointHistory;
import com.back.point.domain.PointType;
import com.back.point.repository.PointHistoryRepository;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create")
@ActiveProfiles("test")
class PointAndHazardConcurrencyIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private HazardRepository hazardRepository;
    @Autowired
    private HazardReportRepository hazardReportRepository;
    @Autowired
    private PointHistoryRepository pointHistoryRepository;
    @Autowired
    private PointRewardService pointRewardService;
    @Autowired
    private HazardService hazardService;

    @Test
    @DisplayName("일반 조회된 같은 User에 +100P와 +10P를 동시에 지급해도 최종 잔액은 110P다")
    void keepsBothRewardsWhenManagedUserWasLoadedBeforeLock() throws Exception {
        TransactionTemplate tx = transactionTemplate();
        Long userId = tx.execute(status -> userRepository.saveAndFlush(
                User.createLocal(uniqueEmail("point-lock"), "hash", "사용자")).getId());
        long hazardReferenceId = 9_000_001L;
        long reportReferenceId = 9_100_001L;

        CountDownLatch bothLoaded = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch activationApplied = new CountDownLatch(1);
        CountDownLatch reportLockAttempt = new CountDownLatch(1);
        CountDownLatch allowActivationCommit = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var activation = executor.submit(() -> tx.executeWithoutResult(status -> {
                userRepository.findByIdAndDeletedAtIsNull(userId).orElseThrow();
                bothLoaded.countDown();
                await(start);
                pointRewardService.rewardHazardActivation(hazardReferenceId, List.of(userId));
                activationApplied.countDown();
                await(allowActivationCommit);
            }));
            var report = executor.submit(() -> tx.executeWithoutResult(status -> {
                userRepository.findByIdAndDeletedAtIsNull(userId).orElseThrow();
                bothLoaded.countDown();
                await(start);
                await(activationApplied);
                reportLockAttempt.countDown();
                pointRewardService.rewardHazardReport(userId, reportReferenceId);
            }));

            assertThat(bothLoaded.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(activationApplied.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(reportLockAttempt.await(10, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(300);
            allowActivationCommit.countDown();
            activation.get(10, TimeUnit.SECONDS);
            report.get(10, TimeUnit.SECONDS);
        }

        long pointBalance = tx.execute(status ->
                userRepository.findById(userId).orElseThrow().getPointBalance());
        assertThat(pointBalance).isEqualTo(110L);
    }

    @Test
    @DisplayName("matching에서 PENDING으로 읽은 Hazard도 잠금 대기 후 최신 ACTIVE와 activatedAt을 유지한다")
    void refreshesHazardAfterWaitingForPessimisticLock() throws Exception {
        TransactionTemplate tx = transactionTemplate();
        Fixture fixture = tx.execute(status -> {
            User first = userRepository.save(User.createLocal(
                    uniqueEmail("hazard-first"), "hash", "첫째"));
            User second = userRepository.save(User.createLocal(
                    uniqueEmail("hazard-second"), "hash", "둘째"));
            User third = userRepository.save(User.createLocal(
                    uniqueEmail("hazard-third"), "hash", "셋째"));
            Course course = courseRepository.save(new Course("잠금 코스", "11500", 3000));
            Hazard hazard = hazardRepository.save(new Hazard(course, "빙판"));
            hazardReportRepository.save(new HazardReport(
                    hazard, first, "상", "첫 신고", 37.5, 126.8));
            hazardReportRepository.saveAndFlush(new HazardReport(
                    hazard, second, "상", "두 번째 신고", 37.5001, 126.8001));
            return new Fixture(course.getId(), hazard.getId(), third.getId());
        });

        CountDownLatch candidateLoaded = new CountDownLatch(1);
        CountDownLatch activeFlushed = new CountDownLatch(1);
        CountDownLatch reportLockAttempt = new CountDownLatch(1);
        CountDownLatch allowActiveCommit = new CountDownLatch(1);
        AtomicReference<LocalDateTime> originalActivatedAt = new AtomicReference<>();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var report = executor.submit(() -> tx.executeWithoutResult(status -> {
                Hazard initiallyManaged = hazardReportRepository.findMatchingCandidates(
                                fixture.courseId(),
                                "빙판",
                                List.of(HazardStatus.PENDING, HazardStatus.ACTIVE))
                        .getFirst()
                        .getHazard();
                assertThat(initiallyManaged.getStatus()).isEqualTo(HazardStatus.PENDING);
                candidateLoaded.countDown();
                await(activeFlushed);
                reportLockAttempt.countDown();
                hazardService.addReport(
                        fixture.thirdUserId(),
                        fixture.hazardId(),
                        new HazardReportCreateRequest(
                                "상", "세 번째 신고", 37.5002, 126.8002)
                );
            }));
            var activation = executor.submit(() -> {
                await(candidateLoaded);
                tx.executeWithoutResult(status -> {
                    Hazard locked = hazardRepository.findByIdForUpdate(fixture.hazardId())
                            .orElseThrow();
                    locked.updateStatusByReporterCount(3, 3);
                    entityManager.flush();
                    entityManager.refresh(locked);
                    originalActivatedAt.set(locked.getActivatedAt());
                    activeFlushed.countDown();
                    await(allowActiveCommit);
                });
            });

            assertThat(activeFlushed.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(reportLockAttempt.await(10, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(300);
            allowActiveCommit.countDown();
            activation.get(10, TimeUnit.SECONDS);
            report.get(10, TimeUnit.SECONDS);
        }

        Hazard updated = tx.execute(status ->
                hazardRepository.findById(fixture.hazardId()).orElseThrow());
        assertThat(updated.getStatus()).isEqualTo(HazardStatus.ACTIVE);
        assertThat(updated.getActivatedAt()).isEqualTo(originalActivatedAt.get());
        assertThat(hazardReportRepository.countDistinctReportersByHazardId(fixture.hazardId()))
                .isEqualTo(3L);
    }

    @Test
    @DisplayName("40P 적립 상태에서 두 신고 보상이 동시에 요청되어도 일일 한도 50P를 넘지 않는다")
    void doesNotExceedDailyLimitWithConcurrentReportRewards() throws Exception {
        TransactionTemplate tx = transactionTemplate();
        Long userId = tx.execute(status -> {
            User user = userRepository.save(User.createLocal(
                    uniqueEmail("daily-limit"), "hash", "사용자"));
            user.addPoints(40L);
            LocalDateTime today = LocalDate.now(KST).atTime(1, 0);
            for (long referenceId = 9_200_001L; referenceId <= 9_200_004L; referenceId++) {
                pointHistoryRepository.save(new PointHistory(
                        user, 10L, PointType.HAZARD_REPORT, referenceId, today));
            }
            pointHistoryRepository.flush();
            return user.getId();
        });

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                ready.countDown();
                await(start);
                return pointRewardService.rewardHazardReport(userId, 9_200_005L);
            });
            var second = executor.submit(() -> {
                ready.countDown();
                await(start);
                return pointRewardService.rewardHazardReport(userId, 9_200_006L);
            });

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }

        long balance = tx.execute(status ->
                userRepository.findById(userId).orElseThrow().getPointBalance());
        long earnedToday = tx.execute(status ->
                pointHistoryRepository.sumAmountByUserAndTypeAndCreatedAtRange(
                        userId,
                        PointType.HAZARD_REPORT,
                        LocalDate.now(KST).atStartOfDay(),
                        LocalDate.now(KST).plusDays(1).atStartOfDay()
                ));
        assertThat(balance).isEqualTo(50L);
        assertThat(earnedToday).isEqualTo(50L);
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@test.com";
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시성 테스트 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private record Fixture(Long courseId, Long hazardId, Long thirdUserId) {
    }
}
