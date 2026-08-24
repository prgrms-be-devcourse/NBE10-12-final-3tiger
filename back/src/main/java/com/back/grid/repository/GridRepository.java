package com.back.grid.repository;

import com.back.grid.entity.GridScore;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface GridRepository extends Repository<GridScore, Long> {
    @Query(value = """
            SELECT grid_id AS "gridId",
                   region_code AS "regionCode",
                   ST_Y(centroid) AS "centroidLat",
                   ST_X(centroid) AS "centroidLng",
                   flatness AS flatness,
                   shade_summer AS "shadeSummer",
                   shade_winter_sun AS "shadeWinterSun",
                   traffic_low AS "trafficLow",
                   wheelchair AS wheelchair,
                   surface_natural AS "surfaceNatural",
                   bench_density AS "benchDensity",
                   restroom_proximity AS "restroomProximity",
                   water_facility AS "waterFacility"
            FROM grid_score
            CROSS JOIN (
                SELECT ST_MakeEnvelope(
                    :minLng, :minLat, :maxLng, :maxLat, 4326
                ) AS bbox
            ) bounds
            WHERE centroid && bounds.bbox
              AND ST_Covers(bounds.bbox, centroid)
            ORDER BY grid_id
            """, nativeQuery = true)
    List<GridOverlayProjection> findAllByCentroidIn(
            @Param("minLng") double minLng,
            @Param("minLat") double minLat,
            @Param("maxLng") double maxLng,
            @Param("maxLat") double maxLat
    );
}
