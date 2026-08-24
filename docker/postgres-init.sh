#!/bin/bash
set -uo pipefail

echo "[init] Restoring walking_mvp custom-format dump..."
# pg_restore returns non-zero for benign warnings (e.g. "schema public already exists"
# from PostGIS-bootstrapped DBs). We inspect the outcome via row counts below.
pg_restore \
  --username="$POSTGRES_USER" \
  --dbname="$POSTGRES_DB" \
  --no-owner \
  --role="$POSTGRES_USER" \
  /tmp/walking_mvp_final.dump || echo "[init] pg_restore reported warnings (continuing)"

echo "[init] Verifying restored data..."
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" -c "
  SELECT 'region' AS tbl, COUNT(*) FROM region
  UNION ALL SELECT 'grid_score', COUNT(*) FROM grid_score
  UNION ALL SELECT 'course',     COUNT(*) FROM course;
"
