package com.back.bookmark.repository;

import com.back.bookmark.domain.Bookmark;
import com.back.bookmark.domain.BookmarkId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmarkRepository extends JpaRepository<Bookmark, BookmarkId> {
    boolean existsByUserIdAndCourseId(Long userId, Long courseId);
    long deleteByUserIdAndCourseId(Long userId, Long courseId);
    Page<Bookmark> findByUserId(Long userId, Pageable pageable);
}
