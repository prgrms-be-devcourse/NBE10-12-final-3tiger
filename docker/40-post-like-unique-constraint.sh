#!/bin/bash
set -euo pipefail

psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --file=/tmp/post-like-unique-constraint.sql
