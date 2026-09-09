#!/bin/bash
set -euo pipefail
echo "[migrate] Applying hazard resolution schema..."
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --file=/tmp/hazard-resolution.sql
echo "[migrate] Hazard resolution schema migration complete."
