package com.back.hazard.service;

import com.back.hazard.dto.WalkEdgeCandidate;
import com.back.hazard.repository.WalkEdgeCandidateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class HazardSpatialService {

    private final WalkEdgeCandidateRepository walkEdgeCandidateRepository;

    public HazardSpatialService(WalkEdgeCandidateRepository walkEdgeCandidateRepository) {
        this.walkEdgeCandidateRepository = walkEdgeCandidateRepository;
    }

    public List<WalkEdgeCandidate> findNearestEdgeCandidates(
            double latitude,
            double longitude,
            int limit
    ) {
        return walkEdgeCandidateRepository.findNearest(latitude, longitude, limit);
    }
}
