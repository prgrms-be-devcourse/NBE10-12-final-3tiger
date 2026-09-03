CREATE EXTENSION IF NOT EXISTS postgis;

DROP TABLE IF EXISTS course_score CASCADE;
DROP TABLE IF EXISTS course CASCADE;

CREATE TABLE course (
    course_id         BIGINT PRIMARY KEY,
    region_code       VARCHAR(10) NOT NULL,
    name              VARCHAR(200) NOT NULL,
    path              geometry(LineString, 4326),
    start_point       geometry(Point, 4326),
    distance_m        INTEGER NOT NULL,
    estimated_minutes INTEGER NOT NULL,
    elevation_gain_m  INTEGER DEFAULT 0,
    elevation_loss_m  INTEGER DEFAULT 0,
    is_loop           BOOLEAN NOT NULL,
    source            VARCHAR(50),
    like_count        INTEGER DEFAULT 0,
    data_version      VARCHAR(30)
);

INSERT INTO course (
    course_id, region_code, name, path, start_point,
    distance_m, estimated_minutes, is_loop, source
) VALUES (
    101,
    '11500',
    'loop-course',
    ST_GeomFromText(
        'LINESTRING(127.037 37.544, 127.038 37.545, 127.037 37.544)',
        4326
    ),
    ST_SetSRID(ST_MakePoint(127.037, 37.544), 4326),
    300,
    5,
    true,
    'test'
), (
    102,
    '11500',
    'one-way-course',
    ST_GeomFromText(
        'LINESTRING(126.850 37.550, 126.851 37.551)',
        4326
    ),
    NULL,
    150,
    3,
    false,
    'test'
);
