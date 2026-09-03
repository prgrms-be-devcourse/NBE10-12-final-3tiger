#!/bin/bash
set -euo pipefail

echo "[migrate] Applying Hazard/Report/Confirmation schema..."
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --file=/tmp/hazard-schema.sql
echo "[migrate] Hazard schema migration complete."
