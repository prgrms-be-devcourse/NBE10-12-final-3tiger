#!/bin/bash
set -euo pipefail

echo "[migrate] Applying hazard schema migration..."
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --file=/tmp/hazard-schema.sql
echo "[migrate] Hazard schema migration complete."
