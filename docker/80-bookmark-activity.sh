#!/bin/bash
set -euo pipefail

echo "[migrate] Applying bookmark rating and usage-log schema..."
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" -f /tmp/bookmark-activity.sql
