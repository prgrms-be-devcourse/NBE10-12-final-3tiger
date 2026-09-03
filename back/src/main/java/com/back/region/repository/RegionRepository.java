package com.back.region.repository;

import com.back.region.domain.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RegionRepository extends JpaRepository<Region, String> {

    @Query(value = """
            SELECT r.region_code                                                   AS regionCode,
                   r.name                                                          AS name,
                   ST_Y(r.center::geometry)                                                  AS centerLat,
                   ST_X(r.center::geometry)                                                  AS centerLng,
                   ST_AsGeoJSON(r.bbox)                                                      AS bbox,
                   (SELECT COUNT(*)::int FROM course c WHERE c.region_code = r.region_code) AS courseCount
              FROM region r
             ORDER BY r.name
            """, nativeQuery = true)
    List<RegionListView> findAllListViews();
}
