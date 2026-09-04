package com.back.userblock.repository;

import com.back.userblock.domain.UserBlock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    boolean existsByBlocker_IdAndBlocked_Id(Long blockerId, Long blockedId);

    long deleteByBlocker_IdAndBlocked_Id(Long blockerId, Long blockedId);

    @EntityGraph(attributePaths = {"blocked"})
    Page<UserBlock> findByBlocker_IdOrderByCreatedAtDesc(Long blockerId, Pageable pageable);

    /** userId 와 차단 관계(양방향)에 있는 상대 사용자 id 목록. */
    @Query("""
            select case when ub.blocker.id = :userId then ub.blocked.id else ub.blocker.id end
            from UserBlock ub
            where ub.blocker.id = :userId or ub.blocked.id = :userId
            """)
    List<Long> findRelatedUserIds(@Param("userId") Long userId);
}
