#!/bin/bash
set -euo pipefail

echo "[migrate] Applying cosmetic shop schema..."
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --file=/tmp/shop-schema.sql
echo "[migrate] Cosmetic shop schema migration complete."
