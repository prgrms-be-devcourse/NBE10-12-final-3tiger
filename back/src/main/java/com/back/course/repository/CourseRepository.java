package com.back.course.repository;

import com.back.course.domain.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {

    @Query(value = """
            SELECT c.course_id                                                       AS courseId,
                   c.name                                                            AS name,
                   c.distance_m                                                      AS distanceM,
                   c.estimated_minutes                                               AS estimatedMinutes,
                   c.is_loop                                                         AS isLoop,
                   ST_Y(c.start_point)                                               AS startLat,
                   ST_X(c.start_point)                                               AS startLng,
                   cs.flatness                                                       AS flatness,
                   CASE WHEN :useSummer THEN cs.shade_summer ELSE cs.shade_winter_sun END AS shadeScore,
                   cs.wheelchair                                                     AS wheelchair,
                   c.like_count                                                      AS likeCount
              FROM course c
              LEFT JOIN course_score cs ON cs.course_id = c.course_id
             WHERE c.region_code = :regionCode
               AND (CAST(:isLoop AS boolean) IS NULL OR c.is_loop = CAST(:isLoop AS boolean))
               AND (CAST(:distanceMinM AS integer) IS NULL OR c.distance_m >= CAST(:distanceMinM AS integer))
               AND (CAST(:distanceMaxM AS integer) IS NULL OR c.distance_m <= CAST(:distanceMaxM AS integer))
             ORDER BY
               CASE WHEN :sort = 'score' THEN
                 (COALESCE(cs.flatness, 0)
                  + COALESCE(CASE WHEN :useSummer THEN cs.shade_summer ELSE cs.shade_winter_sun END, 0)
                  + COALESCE(cs.traffic_low, 0)
                  + COALESCE(cs.wheelchair, 0)
                  + COALESCE(cs.surface_natural, 0)
                  + COALESCE(cs.bench_density, 0)
                  + COALESCE(cs.restroom_proximity, 0)
                  + COALESCE(cs.water_facility, 0)
                  + COALESCE(cs.loop_bonus, 0)) / 9.0
                 ELSE NULL END DESC NULLS LAST,
               CASE WHEN :sort = 'popularity' THEN c.like_count ELSE NULL END DESC NULLS LAST,
               c.course_id ASC
             LIMIT :size OFFSET :offset
            """, nativeQuery = true)
    List<CourseListView> searchByRegion(@Param("regionCode") String regionCode,
                                        @Param("isLoop") Boolean isLoop,
                                        @Param("distanceMinM") Integer distanceMinM,
                                        @Param("distanceMaxM") Integer distanceMaxM,
                                        @Param("useSummer") boolean useSummer,
                                        @Param("sort") String sort,
                                        @Param("size") int size,
                                        @Param("offset") int offset);

    @Query(value = """
            SELECT COUNT(*)
              FROM course c
             WHERE c.region_code = :regionCode
               AND (CAST(:isLoop AS boolean) IS NULL OR c.is_loop = CAST(:isLoop AS boolean))
               AND (CAST(:distanceMinM AS integer) IS NULL OR c.distance_m >= CAST(:distanceMinM AS integer))
               AND (CAST(:distanceMaxM AS integer) IS NULL OR c.distance_m <= CAST(:distanceMaxM AS integer))
            """, nativeQuery = true)
    long countByRegion(@Param("regionCode") String regionCode,
                       @Param("isLoop") Boolean isLoop,
                       @Param("distanceMinM") Integer distanceMinM,
                       @Param("distanceMaxM") Integer distanceMaxM);

    @Query(value = """
            SELECT c.course_id                                                       AS courseId,
                   c.name                                                            AS name,
                   c.distance_m                                                      AS distanceM,
                   c.estimated_minutes                                               AS estimatedMinutes,
                   c.is_loop                                                         AS isLoop,
                   ST_Y(c.start_point)                                               AS startLat,
                   ST_X(c.start_point)                                               AS startLng,
                   cs.flatness                                                       AS flatness,
                   CASE WHEN :useSummer THEN cs.shade_summer ELSE cs.shade_winter_sun END AS shadeScore,
                   cs.wheelchair                                                     AS wheelchair,
                   c.like_count                                                      AS likeCount
              FROM course c
              LEFT JOIN course_score cs ON cs.course_id = c.course_id
             WHERE ST_DWithin(c.start_point::geography,
                              ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                              :radiusM)
               AND (CAST(:isLoop AS boolean) IS NULL OR c.is_loop = CAST(:isLoop AS boolean))
               AND (CAST(:distanceMinM AS integer) IS NULL OR c.distance_m >= CAST(:distanceMinM AS integer))
               AND (CAST(:distanceMaxM AS integer) IS NULL OR c.distance_m <= CAST(:distanceMaxM AS integer))
             ORDER BY
               CASE WHEN :sort = 'score' THEN
                 (COALESCE(cs.flatness, 0)
                  + COALESCE(CASE WHEN :useSummer THEN cs.shade_summer ELSE cs.shade_winter_sun END, 0)
                  + COALESCE(cs.traffic_low, 0)
                  + COALESCE(cs.wheelchair, 0)
                  + COALESCE(cs.surface_natural, 0)
                  + COALESCE(cs.bench_density, 0)
                  + COALESCE(cs.restroom_proximity, 0)
                  + COALESCE(cs.water_facility, 0)
                  + COALESCE(cs.loop_bonus, 0)) / 9.0
                 ELSE NULL END DESC NULLS LAST,
               CASE WHEN :sort = 'popularity' THEN c.like_count ELSE NULL END DESC NULLS LAST,
               CASE WHEN :sort = 'distance' THEN
                 ST_Distance(c.start_point::geography,
                             ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography)
                 ELSE NULL END ASC NULLS LAST,
               c.course_id ASC
             LIMIT :size OFFSET :offset
            """, nativeQuery = true)
    List<CourseListView> searchByLocation(@Param("lat") double lat,
                                          @Param("lng") double lng,
                                          @Param("radiusM") int radiusM,
                                          @Param("isLoop") Boolean isLoop,
                                          @Param("distanceMinM") Integer distanceMinM,
                                          @Param("distanceMaxM") Integer distanceMaxM,
                                          @Param("useSummer") boolean useSummer,
                                          @Param("sort") String sort,
                                          @Param("size") int size,
                                          @Param("offset") int offset);

    @Query(value = """
            SELECT COUNT(*)
              FROM course c
             WHERE ST_DWithin(c.start_point::geography,
                              ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                              :radiusM)
               AND (CAST(:isLoop AS boolean) IS NULL OR c.is_loop = CAST(:isLoop AS boolean))
               AND (CAST(:distanceMinM AS integer) IS NULL OR c.distance_m >= CAST(:distanceMinM AS integer))
               AND (CAST(:distanceMaxM AS integer) IS NULL OR c.distance_m <= CAST(:distanceMaxM AS integer))
            """, nativeQuery = true)
    long countByLocation(@Param("lat") double lat,
                         @Param("lng") double lng,
                         @Param("radiusM") int radiusM,
                         @Param("isLoop") Boolean isLoop,
                         @Param("distanceMinM") Integer distanceMinM,
                         @Param("distanceMaxM") Integer distanceMaxM);
}
