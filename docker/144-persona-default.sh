#!/bin/bash
set -euo pipefail

echo "[migrate] Fixing persona_pref default from general to walker..."
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" \
    -v ON_ERROR_STOP=1 \
    -f /tmp/144-persona-default.sql
echo "[migrate] Persona default migration complete."
