package com.back.hazard.repository;

import com.back.hazard.domain.HazardReport;
import com.back.hazard.domain.HazardStatus;
import com.back.hazard.dto.HazardResponse;
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
            select new com.back.hazard.dto.HazardResponse(
                hazard.id,
                hazard.hazardType,
                hazard.status,
                report.latitude,
                report.longitude,
                count(distinct allReport.reporter.id),
                count(distinct confirmation.id),
                case when count(distinct myReport.id) > 0 then true else false end,
                hazard.createdAt,
                hazard.activatedAt
            )
            from HazardReport report
            join report.hazard hazard
            left join HazardReport allReport on allReport.hazard = hazard
            left join HazardConfirmation confirmation on confirmation.hazard = hazard
            left join HazardReport myReport on myReport.hazard = hazard
                and myReport.reporter.id = :userId
            where hazard.course.id = :courseId
              and (
                    hazard.status = :activeStatus
                    or (
                        hazard.status = :pendingStatus
                        and :userId is not null
                        and myReport.id is not null
                    )
              )
              and not exists (
                  select earlier.id
                  from HazardReport earlier
                  where earlier.hazard = hazard
                    and (
                        earlier.createdAt < report.createdAt
                        or (earlier.createdAt = report.createdAt and earlier.id < report.id)
                    )
              )
            group by hazard.id,
                     hazard.hazardType,
                     hazard.status,
                     report.latitude,
                     report.longitude,
                     hazard.createdAt,
                     hazard.activatedAt
            order by hazard.createdAt desc, hazard.id desc
            """)
    List<HazardResponse> findVisibleHazardResponsesByCourseId(
            @Param("courseId") Long courseId,
            @Param("activeStatus") HazardStatus activeStatus,
            @Param("pendingStatus") HazardStatus pendingStatus,
            @Param("userId") Long userId
    );

    @Query("""
            select count(distinct report.reporter.id)
            from HazardReport report
            where report.hazard.id = :hazardId
            """)
    long countDistinctReportersByHazardId(@Param("hazardId") Long hazardId);

    @Query("""
            select distinct report.reporter.id
            from HazardReport report
            where report.hazard.id = :hazardId
            order by report.reporter.id
            """)
    List<Long> findDistinctReporterIdsByHazardId(@Param("hazardId") Long hazardId);
}
