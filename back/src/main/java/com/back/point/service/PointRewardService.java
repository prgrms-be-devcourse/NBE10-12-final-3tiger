package com.back.point.service;

import com.back.global.error.ApiException;
import com.back.point.domain.PointHistory;
import com.back.point.domain.PointType;
import com.back.point.repository.PointHistoryRepository;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
public class PointRewardService {

    static final long HAZARD_REPORT_REWARD = 10L;
    static final long DAILY_HAZARD_REPORT_REWARD_LIMIT = 50L;
    static final long HAZARD_ACTIVATION_REWARD = 100L;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PointHistoryRepository pointHistoryRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;
    private final Clock clock;

    @Autowired
    public PointRewardService(
            PointHistoryRepository pointHistoryRepository,
            UserRepository userRepository,
            EntityManager entityManager
    ) {
        this(pointHistoryRepository, userRepository, entityManager, Clock.system(KST));
    }

    PointRewardService(
            PointHistoryRepository pointHistoryRepository,
            UserRepository userRepository,
            EntityManager entityManager,
            Clock clock
    ) {
        this.pointHistoryRepository = pointHistoryRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional
    public boolean rewardHazardReport(Long userId, Long hazardReportId) {
        User user = findActiveUserForUpdate(userId);
        if (pointHistoryRepository.existsByUser_IdAndTypeAndReferenceId(
                userId,
                PointType.HAZARD_REPORT,
                hazardReportId
        )) {
            return false;
        }

        LocalDate today = LocalDate.now(clock);
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime startOfNextDay = today.plusDays(1).atStartOfDay();
        long earnedToday = pointHistoryRepository.sumAmountByUserAndTypeAndCreatedAtRange(
                userId,
                PointType.HAZARD_REPORT,
                startOfDay,
                startOfNextDay
        );
        if (earnedToday > DAILY_HAZARD_REPORT_REWARD_LIMIT - HAZARD_REPORT_REWARD) {
            return false;
        }

        award(
                user,
                HAZARD_REPORT_REWARD,
                PointType.HAZARD_REPORT,
                hazardReportId,
                LocalDateTime.now(clock)
        );
        return true;
    }

    @Transactional
    public void rewardHazardActivation(Long hazardId, List<Long> contributorIds) {
        if (pointHistoryRepository.existsByTypeAndReferenceId(
                PointType.HAZARD_ACTIVATED,
                hazardId
        )) {
            return;
        }

        LocalDateTime rewardedAt = LocalDateTime.now(clock);
        contributorIds.stream()
                .distinct()
                .sorted()
                .forEach(userId -> findActiveUserForUpdateIfPresent(userId)
                        .filter(user -> !pointHistoryRepository.existsByUser_IdAndTypeAndReferenceId(
                                userId,
                                PointType.HAZARD_ACTIVATED,
                                hazardId
                        ))
                        .ifPresent(user -> award(
                                user,
                                HAZARD_ACTIVATION_REWARD,
                                PointType.HAZARD_ACTIVATED,
                                hazardId,
                                rewardedAt
                        )));
    }

    private User findActiveUserForUpdate(Long userId) {
        return findActiveUserForUpdateIfPresent(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "존재하지 않는 사용자입니다."
                ));
    }

    private Optional<User> findActiveUserForUpdateIfPresent(Long userId) {
        Optional<User> user = userRepository.findByIdAndDeletedAtIsNullForUpdate(userId);
        user.ifPresent(entityManager::refresh);
        return user;
    }

    private void award(
            User user,
            long amount,
            PointType type,
            Long referenceId,
            LocalDateTime createdAt
    ) {
        user.addPoints(amount);
        pointHistoryRepository.save(new PointHistory(
                user,
                amount,
                type,
                referenceId,
                createdAt
        ));
    }
}
