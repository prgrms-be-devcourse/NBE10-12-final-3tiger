#!/bin/bash
set -euo pipefail

echo "[migrate] Applying course map image schema..."
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" \
    -v ON_ERROR_STOP=1 \
    -f /tmp/course-map-image.sql
echo "[migrate] Course map image schema migration complete."
