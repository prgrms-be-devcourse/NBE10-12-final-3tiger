#!/bin/bash
set -euo pipefail

echo "[migrate] Applying course road snap validation (#169)..."
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" \
    -v ON_ERROR_STOP=1 \
    -f /tmp/course-road-snap-validation.sql
echo "[migrate] Course road snap validation migration complete."
