package com.back.hazard.repository;

import com.back.hazard.domain.HazardConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HazardConfirmationRepository extends JpaRepository<HazardConfirmation, Long> {

    boolean existsByHazard_IdAndUser_Id(Long hazardId, Long userId);

    long countByHazard_Id(Long hazardId);

    void deleteByHazard_Id(Long hazardId);
}
