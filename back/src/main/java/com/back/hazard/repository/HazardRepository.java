package com.back.hazard.repository;

import com.back.hazard.domain.Hazard;
import com.back.hazard.domain.HazardStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;

public interface HazardRepository extends JpaRepository<Hazard, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select hazard from Hazard hazard where hazard.id = :id")
    java.util.Optional<Hazard> findByIdForUpdate(@Param("id") Long id);

    List<Hazard> findByCourse_IdAndStatusOrderByCreatedAtDesc(
            Long courseId,
            HazardStatus status
    );
}
