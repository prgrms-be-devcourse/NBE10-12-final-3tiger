package com.back.hazard.repository;

import com.back.hazard.domain.Hazard;
import com.back.hazard.domain.HazardStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HazardRepository extends JpaRepository<Hazard, Long> {

    List<Hazard> findByCourse_IdAndStatusOrderByCreatedAtDesc(
            Long courseId,
            HazardStatus status
    );
}
