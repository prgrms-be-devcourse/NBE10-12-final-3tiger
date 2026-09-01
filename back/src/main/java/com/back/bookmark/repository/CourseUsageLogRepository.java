package com.back.bookmark.repository;

import com.back.bookmark.domain.CourseUsageLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseUsageLogRepository extends JpaRepository<CourseUsageLog, Long> {
    Page<CourseUsageLog> findByUser_IdAndCourse_IdOrderByUsedAtDesc(Long userId, Long courseId, Pageable pageable);
}
