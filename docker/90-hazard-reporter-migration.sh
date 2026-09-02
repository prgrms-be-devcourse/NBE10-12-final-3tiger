#!/bin/bash
set -euo pipefail

echo "[migrate] Applying hazard reporter migration..."
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --file=/tmp/hazard-reporter-migration.sql
echo "[migrate] Hazard reporter migration complete."
