#!/bin/bash
set -euo pipefail

echo "[migrate] Applying walk reservation schema..."
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" \
    -v ON_ERROR_STOP=1 \
    -f /tmp/walk-reservation.sql
echo "[migrate] Walk reservation schema migration complete."
