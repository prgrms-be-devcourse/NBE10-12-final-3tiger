#!/bin/bash
set -euo pipefail

echo "[migrate] Applying walk history schema..."
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" \
    -v ON_ERROR_STOP=1 \
    -f /tmp/walk-history.sql
echo "[migrate] Walk history schema migration complete."
