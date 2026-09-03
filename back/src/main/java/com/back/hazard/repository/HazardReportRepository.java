package com.back.hazard.repository;

import com.back.hazard.domain.HazardReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface HazardReportRepository extends JpaRepository<HazardReport, Long> {

    boolean existsByHazard_IdAndReporter_Id(Long hazardId, Long reporterId);

    Optional<HazardReport> findByHazard_IdAndReporter_Id(Long hazardId, Long reporterId);

    @Query("""
            select count(distinct report.reporter.id)
            from HazardReport report
            where report.hazard.id = :hazardId
            """)
    long countDistinctReportersByHazardId(@Param("hazardId") Long hazardId);
}
