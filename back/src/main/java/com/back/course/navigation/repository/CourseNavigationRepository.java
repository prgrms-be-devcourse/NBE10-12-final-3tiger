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
                  c.distance_m AS distanceM,
                  c.estimated_minutes AS estimatedMinutes,
                  c.is_loop AS isLoop,

                  ST_Y(
                      COALESCE(
                          c.start_point,
                          ST_StartPoint(c.path)
                      )
                  ) AS startLat,

                  ST_X(
                      COALESCE(
                          c.start_point,
                          ST_StartPoint(c.path)
                      )
                  ) AS startLng,

                  ST_Y(ST_EndPoint(c.path)) AS endLat,
                  ST_X(ST_EndPoint(c.path)) AS endLng,

                  ST_AsGeoJSON(c.path) AS pathGeoJson,

                  ST_NPoints(c.path) AS coordinateCount,
                  ST_SRID(c.path) AS srid,
                  GeometryType(c.path) AS geometryType,
                  ST_IsValid(c.path) AS pathValid,
                  ST_IsEmpty(c.path) AS pathEmpty,

                  ST_Length(c.path::geography)
                      AS calculatedDistanceM,

                  ST_Distance(
                      ST_StartPoint(c.path)::geography,
                      ST_EndPoint(c.path)::geography
                  ) AS startEndDistanceM

              FROM course c
              WHERE c.course_id = :courseId
              """, nativeQuery = true)
    Optional<CourseNavigationView> findNavigationByCourseId(
            @Param("courseId") Long courseId
    );
}
