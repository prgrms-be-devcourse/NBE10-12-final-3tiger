#!/bin/bash
set -uo pipefail

echo "[migrate] Applying persona weight migrations (21..25)..."
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" -f /tmp/persona/21_persona_weight.sql
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" -f /tmp/persona/22_persona_course_scores.sql
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" -f /tmp/persona/23_persona_edge_costs.sql
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" -f /tmp/persona/24_generate_functions_persona.sql
psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" -f /tmp/persona/25_oneway_routing.sql
echo "[migrate] Persona migrations complete."
