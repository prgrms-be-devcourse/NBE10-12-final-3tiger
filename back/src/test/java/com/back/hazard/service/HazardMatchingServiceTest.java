package com.back.hazard.service;

import com.back.course.domain.Course;
import com.back.hazard.domain.Hazard;
import com.back.hazard.domain.HazardReport;
import com.back.hazard.domain.HazardStatus;
import com.back.hazard.dto.WalkEdgeCandidate;
import com.back.hazard.repository.HazardReportRepository;
import com.back.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class HazardMatchingServiceTest {

    @Mock
    private HazardReportRepository hazardReportRepository;
    @Mock
    private HazardSpatialService hazardSpatialService;
    @InjectMocks
    private HazardMatchingService hazardMatchingService;

    @Test
    @DisplayName("같은 course와 hazardType에서 가까운 같은 edge 신고는 기존 Hazard와 매칭한다")
    void matchesNearbyReportOnSameEdge() {
        Hazard hazard = hazard("빙판");
        HazardReport report = report(hazard, 37.5001, 126.8000);
        given(hazardReportRepository.findMatchingCandidates(
                10L, "빙판", List.of(HazardStatus.PENDING, HazardStatus.ACTIVE)))
                .willReturn(List.of(report));
        given(hazardSpatialService.findNearestEdgeCandidates(37.5000, 126.8000, 1))
                .willReturn(List.of(edge(101L)));
        given(hazardSpatialService.findNearestEdgeCandidates(37.5001, 126.8000, 1))
                .willReturn(List.of(edge(101L)));

        var result = hazardMatchingService.findMatchingHazard(
                10L, "빙판", 37.5000, 126.8000);

        assertThat(result).containsSame(hazard);
        verify(hazardSpatialService, never()).areBothPointsWithinDistanceOfAnyVertex(
                anyList(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("같은 hazardType과 edge라도 거리 기준을 넘으면 매칭하지 않는다")
    void doesNotMatchDistantReportOnSameEdge() {
        Hazard hazard = hazard("빙판");
        HazardReport report = report(hazard, 37.5010, 126.8000);
        given(hazardReportRepository.findMatchingCandidates(
                10L, "빙판", List.of(HazardStatus.PENDING, HazardStatus.ACTIVE)))
                .willReturn(List.of(report));
        var result = hazardMatchingService.findMatchingHazard(
                10L, "빙판", 37.5000, 126.8000);

        assertThat(result).isEmpty();
        verifyNoInteractions(hazardSpatialService);
    }

    @Test
    @DisplayName("가까운 신고라도 현재 최근접 edge가 다르면 매칭하지 않는다")
    void doesNotMatchNearbyReportOnDifferentEdge() {
        Hazard hazard = hazard("빙판");
        HazardReport report = report(hazard, 37.5001, 126.8000);
        given(hazardReportRepository.findMatchingCandidates(
                10L, "빙판", List.of(HazardStatus.PENDING, HazardStatus.ACTIVE)))
                .willReturn(List.of(report));
        given(hazardSpatialService.findNearestEdgeCandidates(37.5000, 126.8000, 1))
                .willReturn(List.of(edge(101L, 1L, 2L)));
        given(hazardSpatialService.findNearestEdgeCandidates(37.5001, 126.8000, 1))
                .willReturn(List.of(edge(102L, 3L, 4L)));

        var result = hazardMatchingService.findMatchingHazard(
                10L, "빙판", 37.5000, 126.8000);

        assertThat(result).isEmpty();
        verify(hazardSpatialService, never()).areBothPointsWithinDistanceOfAnyVertex(
                anyList(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("edge는 다르지만 두 GPS가 shared vertex 5m 이내면 매칭한다")
    void matchesDifferentEdgesNearSharedVertex() {
        Hazard hazard = hazard("빙판");
        HazardReport report = report(hazard, 37.5001, 126.8000);
        given(hazardReportRepository.findMatchingCandidates(
                10L, "빙판", List.of(HazardStatus.PENDING, HazardStatus.ACTIVE)))
                .willReturn(List.of(report));
        given(hazardSpatialService.findNearestEdgeCandidates(37.5000, 126.8000, 1))
                .willReturn(List.of(edge(101L, 1L, 2L)));
        given(hazardSpatialService.findNearestEdgeCandidates(37.5001, 126.8000, 1))
                .willReturn(List.of(edge(102L, 2L, 3L)));
        given(hazardSpatialService.areBothPointsWithinDistanceOfAnyVertex(
                List.of(2L), 37.5000, 126.8000, 37.5001, 126.8000, 5.0))
                .willReturn(true);

        var result = hazardMatchingService.findMatchingHazard(
                10L, "빙판", 37.5000, 126.8000);

        assertThat(result).containsSame(hazard);
    }

    @Test
    @DisplayName("shared vertex가 있어도 새 신고 GPS만 5m 밖이면 매칭하지 않는다")
    void doesNotMatchWhenNewReportIsFarFromSharedVertex() {
        Hazard hazard = hazard("빙판");
        HazardReport report = report(hazard, 37.5001, 126.8000);
        given(hazardReportRepository.findMatchingCandidates(
                10L, "빙판", List.of(HazardStatus.PENDING, HazardStatus.ACTIVE)))
                .willReturn(List.of(report));
        given(hazardSpatialService.findNearestEdgeCandidates(37.5000, 126.8000, 1))
                .willReturn(List.of(edge(101L, 1L, 2L)));
        given(hazardSpatialService.findNearestEdgeCandidates(37.5001, 126.8000, 1))
                .willReturn(List.of(edge(102L, 2L, 3L)));
        given(hazardSpatialService.areBothPointsWithinDistanceOfAnyVertex(
                List.of(2L), 37.5000, 126.8000, 37.5001, 126.8000, 5.0))
                .willReturn(false);

        var result = hazardMatchingService.findMatchingHazard(
                10L, "빙판", 37.5000, 126.8000);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("shared vertex가 있어도 기존 Report GPS만 5m 밖이면 매칭하지 않는다")
    void doesNotMatchWhenExistingReportIsFarFromSharedVertex() {
        Hazard hazard = hazard("빙판");
        HazardReport report = report(hazard, 37.5001, 126.8000);
        given(hazardReportRepository.findMatchingCandidates(
                10L, "빙판", List.of(HazardStatus.PENDING, HazardStatus.ACTIVE)))
                .willReturn(List.of(report));
        given(hazardSpatialService.findNearestEdgeCandidates(37.5000, 126.8000, 1))
                .willReturn(List.of(edge(101L, 1L, 2L)));
        given(hazardSpatialService.findNearestEdgeCandidates(37.5001, 126.8000, 1))
                .willReturn(List.of(edge(102L, 2L, 3L)));
        given(hazardSpatialService.areBothPointsWithinDistanceOfAnyVertex(
                List.of(2L), 37.5000, 126.8000, 37.5001, 126.8000, 5.0))
                .willReturn(false);

        var result = hazardMatchingService.findMatchingHazard(
                10L, "빙판", 37.5000, 126.8000);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("shared vertex가 있어도 두 GPS 모두 vertex에서 멀면 매칭하지 않는다")
    void doesNotMatchWhenBothReportsAreFarFromSharedVertex() {
        Hazard hazard = hazard("빙판");
        HazardReport report = report(hazard, 37.5001, 126.8000);
        given(hazardReportRepository.findMatchingCandidates(
                10L, "빙판", List.of(HazardStatus.PENDING, HazardStatus.ACTIVE)))
                .willReturn(List.of(report));
        given(hazardSpatialService.findNearestEdgeCandidates(37.5000, 126.8000, 1))
                .willReturn(List.of(edge(101L, 1L, 2L)));
        given(hazardSpatialService.findNearestEdgeCandidates(37.5001, 126.8000, 1))
                .willReturn(List.of(edge(102L, 2L, 3L)));
        given(hazardSpatialService.areBothPointsWithinDistanceOfAnyVertex(
                List.of(2L), 37.5000, 126.8000, 37.5001, 126.8000, 5.0))
                .willReturn(false);

        var result = hazardMatchingService.findMatchingHazard(
                10L, "빙판", 37.5000, 126.8000);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("두 edge가 양 끝 vertex를 모두 공유하면 어느 한 vertex의 5m 조건으로 매칭한다")
    void matchesWhenEitherOfTwoSharedVerticesIsNearBothReports() {
        Hazard hazard = hazard("빙판");
        HazardReport report = report(hazard, 37.5001, 126.8000);
        given(hazardReportRepository.findMatchingCandidates(
                10L, "빙판", List.of(HazardStatus.PENDING, HazardStatus.ACTIVE)))
                .willReturn(List.of(report));
        given(hazardSpatialService.findNearestEdgeCandidates(37.5000, 126.8000, 1))
                .willReturn(List.of(edge(101L, 1L, 2L)));
        given(hazardSpatialService.findNearestEdgeCandidates(37.5001, 126.8000, 1))
                .willReturn(List.of(edge(102L, 2L, 1L)));
        given(hazardSpatialService.areBothPointsWithinDistanceOfAnyVertex(
                List.of(1L, 2L), 37.5000, 126.8000, 37.5001, 126.8000, 5.0))
                .willReturn(true);

        var result = hazardMatchingService.findMatchingHazard(
                10L, "빙판", 37.5000, 126.8000);

        assertThat(result).containsSame(hazard);
    }

    @Test
    @DisplayName("self-loop edge의 중복 endpoint는 하나의 shared vertex로만 검사한다")
    void deduplicatesSharedVertexForSelfLoopEdge() {
        Hazard hazard = hazard("빙판");
        HazardReport report = report(hazard, 37.5001, 126.8000);
        given(hazardReportRepository.findMatchingCandidates(
                10L, "빙판", List.of(HazardStatus.PENDING, HazardStatus.ACTIVE)))
                .willReturn(List.of(report));
        given(hazardSpatialService.findNearestEdgeCandidates(37.5000, 126.8000, 1))
                .willReturn(List.of(edge(101L, 2L, 2L)));
        given(hazardSpatialService.findNearestEdgeCandidates(37.5001, 126.8000, 1))
                .willReturn(List.of(edge(102L, 2L, 3L)));
        given(hazardSpatialService.areBothPointsWithinDistanceOfAnyVertex(
                List.of(2L), 37.5000, 126.8000, 37.5001, 126.8000, 5.0))
                .willReturn(true);

        var result = hazardMatchingService.findMatchingHazard(
                10L, "빙판", 37.5000, 126.8000);

        assertThat(result).containsSame(hazard);
    }

    @Test
    @DisplayName("ACTIVE Hazard도 같은 실제 위험의 추가 신고 후보에 포함한다")
    void includesActiveHazardCandidates() {
        Hazard hazard = hazard("공사");
        hazard.updateStatusByReporterCount(3, 3);
        HazardReport report = report(hazard, 37.5001, 126.8000);
        given(hazardReportRepository.findMatchingCandidates(
                10L, "공사", List.of(HazardStatus.PENDING, HazardStatus.ACTIVE)))
                .willReturn(List.of(report));
        given(hazardSpatialService.findNearestEdgeCandidates(37.5000, 126.8000, 1))
                .willReturn(List.of(edge(101L)));
        given(hazardSpatialService.findNearestEdgeCandidates(37.5001, 126.8000, 1))
                .willReturn(List.of(edge(101L)));

        var result = hazardMatchingService.findMatchingHazard(
                10L, "공사", 37.5000, 126.8000);

        assertThat(result).containsSame(hazard);
        assertThat(hazard.getStatus()).isEqualTo(HazardStatus.ACTIVE);
    }

    @Test
    @DisplayName("같은 course에 같은 hazardType 후보가 없으면 매칭하지 않는다")
    void doesNotMatchDifferentHazardType() {
        given(hazardReportRepository.findMatchingCandidates(
                10L, "침수", List.of(HazardStatus.PENDING, HazardStatus.ACTIVE)))
                .willReturn(List.of());

        var result = hazardMatchingService.findMatchingHazard(
                10L, "침수", 37.5000, 126.8000);

        assertThat(result).isEmpty();
        verify(hazardSpatialService, never())
                .findNearestEdgeCandidates(37.5000, 126.8000, 1);
    }

    private static Hazard hazard(String hazardType) {
        return new Hazard(new Course("테스트 코스", "11500", 3000), hazardType);
    }

    private static HazardReport report(Hazard hazard, double latitude, double longitude) {
        return new HazardReport(
                hazard,
                User.createLocal("reporter@test.com", "hash", "신고자"),
                "상",
                "위험 구간",
                latitude,
                longitude
        );
    }

    private static WalkEdgeCandidate edge(Long edgeId) {
        return new WalkEdgeCandidate(edgeId, 1L, 2L, 1.0);
    }

    private static WalkEdgeCandidate edge(Long edgeId, Long source, Long target) {
        return new WalkEdgeCandidate(edgeId, source, target, 1.0);
    }
}
