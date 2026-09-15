#!/bin/bash
set -euo pipefail

echo "[migrate] Applying ordered loop route assembly..."
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" \
    -v ON_ERROR_STOP=1 \
    -f /tmp/loop-route-ordering.sql
echo "[migrate] Ordered loop route assembly migration complete."
