package com.back.hazard.repository;

import com.back.hazard.domain.HazardReport;
import com.back.hazard.domain.HazardStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HazardReportRepository extends JpaRepository<HazardReport, Long> {

    boolean existsByHazard_IdAndReporter_Id(Long hazardId, Long reporterId);

    Optional<HazardReport> findByHazard_IdAndReporter_Id(Long hazardId, Long reporterId);

    @Query("""
            select report
            from HazardReport report
            join fetch report.hazard hazard
            where hazard.course.id = :courseId
              and hazard.hazardType = :hazardType
              and hazard.status in :statuses
            order by report.createdAt desc, report.id desc
            """)
    List<HazardReport> findMatchingCandidates(
            @Param("courseId") Long courseId,
            @Param("hazardType") String hazardType,
            @Param("statuses") List<HazardStatus> statuses
    );

    @Query("""
            select count(distinct report.reporter.id)
            from HazardReport report
            where report.hazard.id = :hazardId
            """)
    long countDistinctReportersByHazardId(@Param("hazardId") Long hazardId);
}
