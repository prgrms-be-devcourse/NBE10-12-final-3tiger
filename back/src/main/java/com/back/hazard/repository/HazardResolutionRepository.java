package com.back.hazard.repository;

import com.back.hazard.domain.HazardResolution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface HazardResolutionRepository extends JpaRepository<HazardResolution, Long> {
    boolean existsByHazard_IdAndUser_Id(Long hazardId, Long userId);
    long countByHazard_Id(Long hazardId);
    void deleteByHazard_Id(Long hazardId);
    @Query("select distinct r.user.id from HazardResolution r where r.hazard.id = :hazardId order by r.user.id")
    List<Long> findDistinctUserIdsByHazardId(@Param("hazardId") Long hazardId);
}
