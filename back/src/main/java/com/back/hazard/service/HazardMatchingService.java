package com.back.hazard.service;

import com.back.hazard.domain.Hazard;
import com.back.hazard.domain.HazardReport;
import com.back.hazard.domain.HazardStatus;
import com.back.hazard.dto.WalkEdgeCandidate;
import com.back.hazard.repository.HazardReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class HazardMatchingService {

    static final double HAZARD_MATCH_DISTANCE_METERS = 30.0;

    // 실제 운영 정확도가 확정된 값이 아니라 교차로 중심부만 보수적으로 포함하는 초기 MVP 기준이다.
    static final double VERTEX_MATCH_DISTANCE_METERS = 5.0;

    private static final int EDGE_CANDIDATE_LIMIT = 1;
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final List<HazardStatus> MATCHABLE_STATUSES = List.of(
            HazardStatus.PENDING,
            HazardStatus.ACTIVE
    );

    private final HazardReportRepository hazardReportRepository;
    private final HazardSpatialService hazardSpatialService;

    public HazardMatchingService(
            HazardReportRepository hazardReportRepository,
            HazardSpatialService hazardSpatialService
    ) {
        this.hazardReportRepository = hazardReportRepository;
        this.hazardSpatialService = hazardSpatialService;
    }

    public Optional<Hazard> findMatchingHazard(
            Long courseId,
            String hazardType,
            double latitude,
            double longitude
    ) {
        List<HazardReport> candidates = hazardReportRepository.findMatchingCandidates(
                courseId,
                hazardType,
                MATCHABLE_STATUSES
        );
        List<NearbyReport> nearbyReports = candidates.stream()
                .map(candidate -> new NearbyReport(
                        candidate,
                        distanceMeters(
                                latitude,
                                longitude,
                                candidate.getLatitude(),
                                candidate.getLongitude()
                        )
                ))
                .filter(candidate -> candidate.distanceMeters() <= HAZARD_MATCH_DISTANCE_METERS)
                .sorted(Comparator.comparingDouble(NearbyReport::distanceMeters))
                .toList();
        if (nearbyReports.isEmpty()) {
            return Optional.empty();
        }

        Optional<WalkEdgeCandidate> newReportEdge = findNearestEdge(latitude, longitude);
        if (newReportEdge.isEmpty()) {
            return Optional.empty();
        }

        for (NearbyReport nearbyReport : nearbyReports) {
            HazardReport candidate = nearbyReport.report();
            Optional<WalkEdgeCandidate> candidateEdge = findNearestEdge(
                    candidate.getLatitude(),
                    candidate.getLongitude()
            );
            if (candidateEdge.isEmpty()) {
                continue;
            }

            if (candidateEdge.get().edgeId().equals(newReportEdge.get().edgeId())) {
                return Optional.of(candidate.getHazard());
            }

            List<Long> sharedVertexIds = findSharedVertexIds(
                    newReportEdge.get(),
                    candidateEdge.get()
            );
            if (!sharedVertexIds.isEmpty()
                    && hazardSpatialService.areBothPointsWithinDistanceOfAnyVertex(
                            sharedVertexIds,
                            latitude,
                            longitude,
                            candidate.getLatitude(),
                            candidate.getLongitude(),
                            VERTEX_MATCH_DISTANCE_METERS
                    )) {
                return Optional.of(candidate.getHazard());
            }
        }

        return Optional.empty();
    }

    private Optional<WalkEdgeCandidate> findNearestEdge(double latitude, double longitude) {
        return hazardSpatialService
                .findNearestEdgeCandidates(latitude, longitude, EDGE_CANDIDATE_LIMIT)
                .stream()
                .findFirst();
    }

    private static List<Long> findSharedVertexIds(
            WalkEdgeCandidate firstEdge,
            WalkEdgeCandidate secondEdge
    ) {
        List<Long> firstVertexIds = Stream.of(firstEdge.source(), firstEdge.target())
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        return Stream.of(secondEdge.source(), secondEdge.target())
                .filter(Objects::nonNull)
                .filter(firstVertexIds::contains)
                .distinct()
                .sorted()
                .toList();
    }

    private static double distanceMeters(
            double firstLatitude,
            double firstLongitude,
            double secondLatitude,
            double secondLongitude
    ) {
        double latitudeDelta = Math.toRadians(secondLatitude - firstLatitude);
        double longitudeDelta = Math.toRadians(secondLongitude - firstLongitude);
        double firstLatitudeRadians = Math.toRadians(firstLatitude);
        double secondLatitudeRadians = Math.toRadians(secondLatitude);

        double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(firstLatitudeRadians)
                * Math.cos(secondLatitudeRadians)
                * Math.pow(Math.sin(longitudeDelta / 2), 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(haversine));
    }

    private record NearbyReport(HazardReport report, double distanceMeters) {
    }
}
