CREATE EXTENSION IF NOT EXISTS postgis;

DROP TABLE IF EXISTS grid_score;

CREATE TABLE grid_score (
    grid_id BIGINT PRIMARY KEY,
    region_code VARCHAR(10) NOT NULL,
    centroid geometry(Point, 4326) NOT NULL,
    flatness NUMERIC(4, 3),
    shade_summer NUMERIC(4, 3),
    shade_winter_sun NUMERIC(4, 3),
    traffic_low NUMERIC(4, 3),
    wheelchair NUMERIC(4, 3),
    surface_natural NUMERIC(4, 3),
    bench_density NUMERIC(4, 3),
    restroom_proximity NUMERIC(4, 3),
    water_facility NUMERIC(4, 3),
    data_version VARCHAR(30) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_grid_score_centroid ON grid_score USING GIST (centroid);

INSERT INTO grid_score VALUES
    (1, '11500', ST_SetSRID(ST_MakePoint(126.805, 37.505), 4326),
     0.100, 0.200, 0.300, 0.400, 0.500, 0.600, 0.700, 0.800, 0.900,
     'test-v1', '2026-08-24T05:00:00Z'),
    (2, '11500', ST_SetSRID(ST_MakePoint(126.810, 37.510), 4326),
     0.900, 0.800, 0.700, 0.600, 0.500, 0.400, 0.300, 0.200, 0.100,
     'test-v1', '2026-08-24T06:00:00Z'),
    (3, '11500', ST_SetSRID(ST_MakePoint(126.811, 37.511), 4326),
     1.000, 1.000, 1.000, 1.000, 1.000, 1.000, 1.000, 1.000, 1.000,
     'test-v1', '2026-08-24T07:00:00Z');
