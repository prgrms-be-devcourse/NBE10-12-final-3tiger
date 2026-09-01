package com.back.bookmark.repository;

import com.back.bookmark.domain.Bookmark;
import com.back.bookmark.domain.BookmarkId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, BookmarkId> {
    boolean existsByUserIdAndCourseId(Long userId, Long courseId);
    Optional<Bookmark> findByUserIdAndCourseId(Long userId, Long courseId);
    long deleteByUserIdAndCourseId(Long userId, Long courseId);
    Page<Bookmark> findByUserId(Long userId, Pageable pageable);

    @Query("""
            select b.course.id
              from Bookmark b
             where b.user.id = :userId
               and b.course.id in :courseIds
            """)
    Set<Long> findBookmarkedCourseIds(@Param("userId") Long userId,
                                      @Param("courseIds") Collection<Long> courseIds);
}
