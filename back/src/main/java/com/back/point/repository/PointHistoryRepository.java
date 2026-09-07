package com.back.point.repository;

import com.back.point.domain.PointHistory;
import com.back.point.domain.PointType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {

    boolean existsByUser_IdAndTypeAndReferenceId(Long userId, PointType type, Long referenceId);

    boolean existsByTypeAndReferenceId(PointType type, Long referenceId);

    @Query("""
            select coalesce(sum(history.amount), 0)
            from PointHistory history
            where history.user.id = :userId
              and history.type = :type
              and history.createdAt >= :startInclusive
              and history.createdAt < :endExclusive
            """)
    long sumAmountByUserAndTypeAndCreatedAtRange(
            @Param("userId") Long userId,
            @Param("type") PointType type,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive
    );
}
