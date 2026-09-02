package com.back.hazard.repository;

import com.back.hazard.domain.Hazard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface HazardRepository extends JpaRepository<Hazard, Long> {

    List<Hazard> findByCourse_IdAndExpiresAtAfterOrderByExpiresAtAsc(
            Long courseId,
            LocalDateTime now
    );
}
