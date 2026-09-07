#!/bin/bash
set -euo pipefail

echo "[migrate] Applying point reward schema..."
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --file=/tmp/point-reward-schema.sql
echo "[migrate] Point reward schema migration complete."
