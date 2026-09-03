#!/bin/bash
set -euo pipefail

echo "[migrate] Applying notification setting schema..."
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" -f /tmp/notification-setting.sql
