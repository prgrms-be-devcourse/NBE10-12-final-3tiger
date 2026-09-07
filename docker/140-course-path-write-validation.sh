#!/bin/bash
set -euo pipefail

echo "[migrate] Applying course path write validation..."
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" \
    -v ON_ERROR_STOP=1 \
    -f /tmp/course-path-write-validation.sql
