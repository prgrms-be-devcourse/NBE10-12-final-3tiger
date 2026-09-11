#!/bin/bash
set -euo pipefail

echo "[migrate] Applying walk reservation reminder schema..."
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" \
    -v ON_ERROR_STOP=1 \
    -f /tmp/walk-reservation-reminder.sql
echo "[migrate] Walk reservation reminder schema migration complete."
