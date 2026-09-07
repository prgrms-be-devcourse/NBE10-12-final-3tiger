package com.back.point.service;

import com.back.point.domain.PointHistory;
import com.back.point.domain.PointType;
import com.back.point.repository.PointHistoryRepository;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PointRewardServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private PointHistoryRepository pointHistoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EntityManager entityManager;

    private PointRewardService pointRewardService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-07T14:30:00Z"), KST);
        pointRewardService = new PointRewardService(
                pointHistoryRepository, userRepository, entityManager, clock);
    }

    @Test
    @DisplayName("첫 HazardReport 보상은 10P와 이력을 함께 적립한다")
    void rewardsFirstHazardReport() {
        User user = User.createLocal("first@test.com", "hash", "신고자");
        given(userRepository.findByIdAndDeletedAtIsNullForUpdate(1L)).willReturn(Optional.of(user));
        given(pointHistoryRepository.existsByUser_IdAndTypeAndReferenceId(
                1L, PointType.HAZARD_REPORT, 101L)).willReturn(false);
        given(pointHistoryRepository.sumAmountByUserAndTypeAndCreatedAtRange(
                eq(1L), eq(PointType.HAZARD_REPORT), any(), any())).willReturn(0L);

        boolean rewarded = pointRewardService.rewardHazardReport(1L, 101L);

        assertThat(rewarded).isTrue();
        assertThat(user.getPointBalance()).isEqualTo(10L);
        ArgumentCaptor<PointHistory> captor = ArgumentCaptor.forClass(PointHistory.class);
        verify(pointHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(captor.getValue().getAmount()).isEqualTo(10L);
        assertThat(captor.getValue().getType()).isEqualTo(PointType.HAZARD_REPORT);
        assertThat(captor.getValue().getReferenceId()).isEqualTo(101L);
        verify(entityManager).refresh(user);
    }

    @Test
    @DisplayName("하루 다섯 건까지만 50P를 지급하고 여섯 번째 신고에는 지급하지 않는다")
    void capsDailyHazardReportRewardAtFiftyPoints() {
        User user = User.createLocal("daily@test.com", "hash", "신고자");
        given(userRepository.findByIdAndDeletedAtIsNullForUpdate(1L)).willReturn(Optional.of(user));
        given(pointHistoryRepository.existsByUser_IdAndTypeAndReferenceId(
                eq(1L), eq(PointType.HAZARD_REPORT), any())).willReturn(false);
        given(pointHistoryRepository.sumAmountByUserAndTypeAndCreatedAtRange(
                eq(1L), eq(PointType.HAZARD_REPORT), any(), any()))
                .willReturn(0L, 10L, 20L, 30L, 40L, 50L);

        for (long reportId = 101L; reportId <= 105L; reportId++) {
            assertThat(pointRewardService.rewardHazardReport(1L, reportId)).isTrue();
        }
        assertThat(pointRewardService.rewardHazardReport(1L, 106L)).isFalse();

        assertThat(user.getPointBalance()).isEqualTo(50L);
        verify(pointHistoryRepository, times(5)).save(any(PointHistory.class));
    }

    @Test
    @DisplayName("일일 한도는 KST 자정 경계로 계산한다")
    void usesKstDayBoundary() {
        Clock nextDayClock = Clock.fixed(Instant.parse("2026-09-07T15:30:00Z"), KST);
        pointRewardService = new PointRewardService(
                pointHistoryRepository, userRepository, entityManager, nextDayClock);
        User user = User.createLocal("kst@test.com", "hash", "신고자");
        given(userRepository.findByIdAndDeletedAtIsNullForUpdate(1L)).willReturn(Optional.of(user));
        given(pointHistoryRepository.existsByUser_IdAndTypeAndReferenceId(
                1L, PointType.HAZARD_REPORT, 101L)).willReturn(false);

        pointRewardService.rewardHazardReport(1L, 101L);

        verify(pointHistoryRepository).sumAmountByUserAndTypeAndCreatedAtRange(
                1L,
                PointType.HAZARD_REPORT,
                LocalDateTime.of(2026, 9, 8, 0, 0),
                LocalDateTime.of(2026, 9, 9, 0, 0)
        );
    }

    @Test
    @DisplayName("KST 날짜가 바뀌면 전날 한도와 무관하게 다시 신고 보상을 지급한다")
    void rewardsAgainAfterKstDateChanges() {
        User user = User.createLocal("next-day@test.com", "hash", "신고자");
        given(userRepository.findByIdAndDeletedAtIsNullForUpdate(1L)).willReturn(Optional.of(user));
        given(pointHistoryRepository.existsByUser_IdAndTypeAndReferenceId(
                eq(1L), eq(PointType.HAZARD_REPORT), any())).willReturn(false);
        given(pointHistoryRepository.sumAmountByUserAndTypeAndCreatedAtRange(
                eq(1L), eq(PointType.HAZARD_REPORT), any(), any())).willReturn(50L, 0L);

        assertThat(pointRewardService.rewardHazardReport(1L, 101L)).isFalse();

        Clock nextDayClock = Clock.fixed(Instant.parse("2026-09-08T14:30:00Z"), KST);
        pointRewardService = new PointRewardService(
                pointHistoryRepository, userRepository, entityManager, nextDayClock);

        assertThat(pointRewardService.rewardHazardReport(1L, 102L)).isTrue();
        assertThat(user.getPointBalance()).isEqualTo(10L);
        verify(pointHistoryRepository).save(any(PointHistory.class));
    }

    @Test
    @DisplayName("같은 HazardReport 보상 이력이 있으면 포인트를 중복 지급하지 않는다")
    void doesNotRewardSameHazardReportTwice() {
        User user = User.createLocal("duplicate@test.com", "hash", "신고자");
        given(userRepository.findByIdAndDeletedAtIsNullForUpdate(1L)).willReturn(Optional.of(user));
        given(pointHistoryRepository.existsByUser_IdAndTypeAndReferenceId(
                1L, PointType.HAZARD_REPORT, 101L)).willReturn(true);

        assertThat(pointRewardService.rewardHazardReport(1L, 101L)).isFalse();

        assertThat(user.getPointBalance()).isZero();
        verify(pointHistoryRepository, never()).save(any(PointHistory.class));
        verify(pointHistoryRepository, never()).sumAmountByUserAndTypeAndCreatedAtRange(
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("최초 ACTIVE 전환 보상은 당시 활성 신고자 모두에게 100P를 지급한다")
    void rewardsActiveContributorsWithoutDailyReportLimit() {
        User first = User.createLocal("first@test.com", "hash", "첫째");
        User second = User.createLocal("second@test.com", "hash", "둘째");
        User third = User.createLocal("third@test.com", "hash", "셋째");
        given(pointHistoryRepository.existsByTypeAndReferenceId(PointType.HAZARD_ACTIVATED, 30L))
                .willReturn(false);
        given(userRepository.findByIdAndDeletedAtIsNullForUpdate(1L)).willReturn(Optional.of(first));
        given(userRepository.findByIdAndDeletedAtIsNullForUpdate(2L)).willReturn(Optional.of(second));
        given(userRepository.findByIdAndDeletedAtIsNullForUpdate(3L)).willReturn(Optional.of(third));
        given(pointHistoryRepository.existsByUser_IdAndTypeAndReferenceId(
                any(), eq(PointType.HAZARD_ACTIVATED), eq(30L))).willReturn(false);

        pointRewardService.rewardHazardActivation(30L, List.of(3L, 1L, 2L));

        assertThat(first.getPointBalance()).isEqualTo(100L);
        assertThat(second.getPointBalance()).isEqualTo(100L);
        assertThat(third.getPointBalance()).isEqualTo(100L);
        verify(pointHistoryRepository, times(3)).save(any(PointHistory.class));
        verify(pointHistoryRepository, never()).sumAmountByUserAndTypeAndCreatedAtRange(
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("탈퇴 사용자는 ACTIVE 보상 대상에서 제외한다")
    void excludesInactiveContributorFromActivationReward() {
        User first = User.createLocal("first@test.com", "hash", "첫째");
        User third = User.createLocal("third@test.com", "hash", "셋째");
        given(pointHistoryRepository.existsByTypeAndReferenceId(PointType.HAZARD_ACTIVATED, 30L))
                .willReturn(false);
        given(userRepository.findByIdAndDeletedAtIsNullForUpdate(1L)).willReturn(Optional.of(first));
        given(userRepository.findByIdAndDeletedAtIsNullForUpdate(2L)).willReturn(Optional.empty());
        given(userRepository.findByIdAndDeletedAtIsNullForUpdate(3L)).willReturn(Optional.of(third));
        given(pointHistoryRepository.existsByUser_IdAndTypeAndReferenceId(
                any(), eq(PointType.HAZARD_ACTIVATED), eq(30L))).willReturn(false);

        pointRewardService.rewardHazardActivation(30L, List.of(1L, 2L, 3L));

        assertThat(first.getPointBalance()).isEqualTo(100L);
        assertThat(third.getPointBalance()).isEqualTo(100L);
        verify(pointHistoryRepository, times(2)).save(any(PointHistory.class));
    }

    @Test
    @DisplayName("같은 Hazard에 ACTIVE 보상 이력이 있으면 재활성화해도 다시 지급하지 않는다")
    void doesNotRewardReactivatedHazardAgain() {
        given(pointHistoryRepository.existsByTypeAndReferenceId(PointType.HAZARD_ACTIVATED, 30L))
                .willReturn(true);

        pointRewardService.rewardHazardActivation(30L, List.of(1L, 2L, 3L));

        verify(userRepository, never()).findByIdAndDeletedAtIsNullForUpdate(any());
        verify(pointHistoryRepository, never()).save(any(PointHistory.class));
    }
}
