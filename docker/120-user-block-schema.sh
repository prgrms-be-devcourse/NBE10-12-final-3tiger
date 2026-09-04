#!/bin/bash
set -euo pipefail

echo "[migrate] Applying user block schema..."
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" -f /tmp/user-block-schema.sql
