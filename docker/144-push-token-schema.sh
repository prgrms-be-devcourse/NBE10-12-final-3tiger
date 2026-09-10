#!/bin/bash
set -euo pipefail

echo "[migrate] Applying push token schema..."
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" -f /tmp/push-token-schema.sql
