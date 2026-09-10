#!/bin/bash
set -euo pipefail

echo "[migrate] Applying weekly walk query index..."
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" \
    -v ON_ERROR_STOP=1 \
    -f /tmp/weekly-walk-query.sql
echo "[migrate] Weekly walk query index migration complete."
