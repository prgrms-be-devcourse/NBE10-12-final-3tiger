CREATE EXTENSION IF NOT EXISTS postgis;

DROP SCHEMA IF EXISTS routing CASCADE;
CREATE SCHEMA routing;

CREATE TABLE routing.walk_edges (
    id BIGINT PRIMARY KEY,
    source BIGINT,
    target BIGINT,
    geom_5179 geometry(LineString, 5179) NOT NULL
);

CREATE TABLE routing.walk_edges_vertices_pgr (
    id BIGINT PRIMARY KEY,
    the_geom geometry(Point, 5179) NOT NULL
);

CREATE INDEX idx_test_walk_edges_geom_5179
    ON routing.walk_edges USING GIST (geom_5179);

INSERT INTO routing.walk_edges (id, source, target, geom_5179) VALUES
    (101, 1001, 1002, ST_Transform(
        ST_GeomFromText('LINESTRING(126.8000 37.5000, 126.8010 37.5000)', 4326), 5179)),
    (102, 1003, 1004, ST_Transform(
        ST_GeomFromText('LINESTRING(126.8000 37.5010, 126.8010 37.5010)', 4326), 5179)),
    (103, 1002, 1005, ST_Transform(
        ST_GeomFromText('LINESTRING(126.8010 37.5000, 126.8010 37.5010)', 4326), 5179));

INSERT INTO routing.walk_edges_vertices_pgr (id, the_geom) VALUES
    (1001, ST_Transform(ST_SetSRID(ST_MakePoint(126.8000, 37.5000), 4326), 5179)),
    (1002, ST_Transform(ST_SetSRID(ST_MakePoint(126.8010, 37.5000), 4326), 5179)),
    (1003, ST_Transform(ST_SetSRID(ST_MakePoint(126.8000, 37.5010), 4326), 5179)),
    (1004, ST_Transform(ST_SetSRID(ST_MakePoint(126.8010, 37.5010), 4326), 5179)),
    (1005, ST_Transform(ST_SetSRID(ST_MakePoint(126.8010, 37.5010), 4326), 5179));
