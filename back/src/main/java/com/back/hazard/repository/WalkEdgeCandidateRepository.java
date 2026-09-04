package com.back.hazard.repository;

import com.back.hazard.dto.WalkEdgeCandidate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class WalkEdgeCandidateRepository {

    private static final String FIND_NEAREST_SQL = """
            WITH report_point AS (
                SELECT ST_Transform(
                    ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326),
                    5179
                ) AS geom
            )
            SELECT edge.id AS edge_id,
                   edge.source,
                   edge.target,
                   ST_Distance(edge.geom_5179, point.geom) AS distance_m
            FROM routing.walk_edges edge
            CROSS JOIN report_point point
            WHERE edge.geom_5179 IS NOT NULL
            ORDER BY edge.geom_5179 <-> point.geom, edge.id
            LIMIT :limit
            """;

    private static final String CHECK_SHARED_VERTEX_DISTANCE_SQL = """
            WITH report_points AS (
                SELECT ST_Transform(
                           ST_SetSRID(ST_MakePoint(:firstLongitude, :firstLatitude), 4326),
                           5179
                       ) AS first_geom,
                       ST_Transform(
                           ST_SetSRID(ST_MakePoint(:secondLongitude, :secondLatitude), 4326),
                           5179
                       ) AS second_geom
            )
            SELECT EXISTS (
                SELECT 1
                FROM routing.walk_edges_vertices_pgr vertex
                CROSS JOIN report_points points
                WHERE vertex.id IN (:vertexIds)
                  AND ST_Distance(vertex.the_geom, points.first_geom) <= :maxDistanceMeters
                  AND ST_Distance(vertex.the_geom, points.second_geom) <= :maxDistanceMeters
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public WalkEdgeCandidateRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<WalkEdgeCandidate> findNearest(double latitude, double longitude, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit은 1 이상이어야 합니다.");
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("latitude", latitude)
                .addValue("longitude", longitude)
                .addValue("limit", limit);

        return jdbc.query(FIND_NEAREST_SQL, parameters, (resultSet, rowNumber) ->
                new WalkEdgeCandidate(
                        resultSet.getLong("edge_id"),
                        resultSet.getObject("source", Long.class),
                        resultSet.getObject("target", Long.class),
                        resultSet.getDouble("distance_m")
                ));
    }

    public boolean areBothPointsWithinDistanceOfAnyVertex(
            List<Long> vertexIds,
            double firstLatitude,
            double firstLongitude,
            double secondLatitude,
            double secondLongitude,
            double maxDistanceMeters
    ) {
        if (vertexIds.isEmpty()) {
            return false;
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("vertexIds", vertexIds)
                .addValue("firstLatitude", firstLatitude)
                .addValue("firstLongitude", firstLongitude)
                .addValue("secondLatitude", secondLatitude)
                .addValue("secondLongitude", secondLongitude)
                .addValue("maxDistanceMeters", maxDistanceMeters);

        return Boolean.TRUE.equals(jdbc.queryForObject(
                CHECK_SHARED_VERTEX_DISTANCE_SQL,
                parameters,
                Boolean.class
        ));
    }
}
