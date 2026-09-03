#!/bin/bash
set -euo pipefail

echo "[migrate] Applying post title migration..."
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --file=/tmp/post-title-migration.sql
echo "[migrate] Post title migration complete."
