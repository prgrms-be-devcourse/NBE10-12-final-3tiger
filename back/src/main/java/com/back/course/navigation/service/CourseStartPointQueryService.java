package com.back.course.navigation.service;

import com.back.course.navigation.repository.CourseNavigationRepository;
import com.back.course.navigation.repository.CourseStartPointView;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseStartPointQueryService {

    private final CourseNavigationRepository repository;

    public CourseStartPointQueryService(CourseNavigationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public CourseStartPoint getStartPoint(Long courseId) {
        CourseStartPointView view = repository.findStartPointByCourseId(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));

        return new CourseStartPoint(
                view.getCourseId(),
                view.getName(),
                view.getStartLat(),
                view.getStartLng()
        );
    }
}
