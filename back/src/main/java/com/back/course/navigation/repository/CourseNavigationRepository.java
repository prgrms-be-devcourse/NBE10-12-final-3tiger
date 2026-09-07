package com.back.course.navigation.repository;

import com.back.course.domain.Course;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CourseNavigationRepository extends Repository<Course, Long> {

    @Query(value = """
              SELECT
                  c.course_id AS courseId,
                  c.name AS name,
                  ST_Y(c.start_point) AS startLat,
                  ST_X(c.start_point) AS startLng
              FROM course c
              WHERE c.course_id = :courseId
              """, nativeQuery = true)
    Optional<CourseStartPointView> findStartPointByCourseId(
            @Param("courseId") Long courseId
    );

    @Query(value = """
              SELECT
                  c.course_id AS courseId,
                  c.name AS name,
                  c.distance_m AS distanceM,
                  c.estimated_minutes AS estimatedMinutes,
                  c.is_loop AS isLoop,

                  ST_Y(c.start_point) AS startLat,
                  ST_X(c.start_point) AS startLng,
                  ST_Y(c.end_point) AS endLat,
                  ST_X(c.end_point) AS endLng,
                  ST_AsGeoJSON(c.path) AS pathGeoJson

              FROM course c
              WHERE c.course_id = :courseId
              """, nativeQuery = true)
    Optional<CourseNavigationView> findNavigationByCourseId(
            @Param("courseId") Long courseId
    );
}
