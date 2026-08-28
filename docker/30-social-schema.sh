#!/bin/bash
set -uo pipefail

echo "[migrate] Applying social schema (bookmark, comment, like, notification)..."
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" -f /tmp/social-schema.sql
echo "[migrate] Social schema migration complete."
