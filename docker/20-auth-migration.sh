#!/bin/bash
set -uo pipefail

echo "[migrate] Applying auth schema migration..."
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" -f /tmp/auth-migration.sql
echo "[migrate] Auth migration complete."
