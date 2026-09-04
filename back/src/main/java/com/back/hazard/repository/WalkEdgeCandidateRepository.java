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
}
