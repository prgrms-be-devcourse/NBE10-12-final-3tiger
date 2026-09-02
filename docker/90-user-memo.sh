#!/bin/bash
set -euo pipefail

echo "[migrate] Applying personal user memo schema..."
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" -f /tmp/user-memo.sql
