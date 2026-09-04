#!/bin/bash
set -euo pipefail

echo "[migrate] Applying report schema..."
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" -f /tmp/report-schema.sql
